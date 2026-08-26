#!/usr/bin/env python3
"""Play Console API helper — no local build dependencies.

Environment:
  PLAY_SERVICE_ACCOUNT_JSON_PATH   path to service-account JSON key (from CI secret)

Subcommands:
  next-version-code PKG              -> prints maxVersionCode:nextVersionCode
  upload PKG AAB --track T [--staged-fraction F] [--release-notes N]
                                     -> creates edit, uploads bundle, rolls out,
                                        commits, prints version_code=NN
  fetch-universal-apk PKG VCODE [--out DIR]
                                     -> downloads the Play-signed universal APK
                                        (signed with the Play App Signing key)
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import sys
import time
import urllib.error
import urllib.request

import jwt  # pyjwt[crypto]

SCOPE = "https://www.googleapis.com/auth/androidpublisher"
TOKEN_URL = "https://oauth2.googleapis.com/token"
API = "https://androidpublisher.googleapis.com/androidpublisher/v3"
UPLOAD_BASE = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3"


class Fail(SystemExit):
    pass


def load_sa() -> dict:
    path = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON_PATH")
    if not path or not os.path.isfile(path):
        raise Fail("PLAY_SERVICE_ACCOUNT_JSON_PATH is not set or missing")
    with open(path) as f:
        sa = json.load(f)
    for key in ("client_email", "private_key"):
        if key not in sa:
            raise Fail(f"service account JSON missing '{key}'")
    return sa


def token() -> str:
    sa = load_sa()
    now = int(time.time())
    claims = {
        "iss": sa["client_email"],
        "scope": SCOPE,
        "aud": TOKEN_URL,
        "iat": now,
        "exp": now + 3600,
    }
    assertion = jwt.encode(claims, sa["private_key"], algorithm="RS256")
    req = urllib.request.Request(
        TOKEN_URL,
        data=(
            "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"
            f"&assertion={assertion}"
        ).encode(),
        headers={"Content-Type": "application/x-www-form-urlencoded"},
        method="POST",
    )
    try:
        resp = json.load(urllib.request.urlopen(req, timeout=30))
    except urllib.error.HTTPError as e:
        raise Fail(f"token exchange failed: {e.code} {e.read().decode()[:200]}")
    if "access_token" not in resp:
        raise Fail(f"no access_token in token response: {resp}")
    return resp["access_token"]


def api(method: str, path: str, tok: str, body: dict | None = None,
        timeout: int = 120, raw: bool = False):
    url = path if path.startswith("http") else f"{API}{path}"
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {tok}",
            "Content-Type": "application/json",
        },
    )
    for attempt in range(4):
        try:
            r = urllib.request.urlopen(req, timeout=timeout)
            return (r.headers, r.read()) if raw else json.loads(r.read())
        except urllib.error.HTTPError as e:
            msg = ""
            try:
                msg = e.read().decode()[:300]
            except Exception:
                pass
            if e.code in (503, 429, 500) and attempt < 3:
                time.sleep(2**attempt)
                continue
            raise Fail(f"{method} {path} -> {e.code}: {msg}")
        except Exception as e:  # noqa: BLE001
            if attempt < 3:
                time.sleep(2**attempt)
                continue
            raise Fail(f"{method} {path} -> {e}")
    raise Fail("unreachable")


def insert_edit(tok: str, pkg: str) -> str:
    return api("POST", f"/applications/{pkg}/edits", tok)["id"]


def cmd_next_version_code(pkg: str) -> None:
    tok = token()
    edit = insert_edit(tok, pkg)
    bundles = api("GET", f"/applications/{pkg}/edits/{edit}/bundles", tok).get("bundles", [])
    codes = [int(b.get("versionCode", 0)) for b in bundles]
    current = max(codes) if codes else 0
    print(f"{current}:{current + 1}")


def cmd_upload(pkg: str, aab: str, track: str, fraction: float, notes: str) -> None:
    tok = token()
    if not os.path.isfile(aab):
        raise Fail(f"AAB not found: {aab}")
    if not (0 < fraction <= 1):
        raise Fail(f"--staged-fraction must be in (0, 1], got {fraction}")

    edit = insert_edit(tok, pkg)
    bundles = api("GET", f"/applications/{pkg}/edits/{edit}/bundles", tok).get("bundles", [])
    existing = {int(b.get("versionCode", 0)) for b in bundles}

    # upload bundle (simple media upload — Google's backend now rejects
    # resumable upload for AABs with "Media type 'application/x-zip' is
    # not supported")
    with open(aab, "rb") as f:
        body = f.read()
    put = urllib.request.Request(
        f"{UPLOAD_BASE}/upload/applications/{pkg}/edits/{edit}/bundles?uploadType=media",
        data=body,
        method="POST",
        headers={
            "Authorization": f"Bearer {tok}",
            "Content-Type": "application/octet-stream",
            "Content-Length": str(len(body)),
        },
    )
    try:
        up = json.load(urllib.request.urlopen(put, timeout=600))
    except urllib.error.HTTPError as e:
        raise Fail(f"blob upload failed: {e.code} {e.read().decode()[:200]}")
    vc = int(up["versionCode"])
    if vc in existing:
        raise Fail(
            f"Play already has versionCode {vc} for {pkg}. "
            "Bump VERSION_CODE before building."
        )
    print(f"uploaded versionCode={vc}", flush=True)

    # track release
    try:
        track_info = api("GET", f"/applications/{pkg}/edits/{edit}/tracks/{track}", tok)
    except Fail:
        track_info = {}
    releases = list(track_info.get("releases", []))
    new_rel: dict = {
        "name": f"{vc}",
        "versionCodes": [str(vc)],
    }
    if track == "staged_rollout_draft":
        new_rel.update(status="inProgress", userFraction=fraction)
    else:
        new_rel.update(status="completed")
    if notes.strip():
        new_rel["releaseNotes"] = [{"language": "en-US", "text": notes.strip()[:500]}]
    # Replace the release list with just the new release — a track can hold
    # only ONE completed production release at a time ("Only one completed
    # release is allowed" 400 otherwise). Old versions remain downloadable
    # for users who already have them; the track just points at the latest.
    api("PUT", f"/applications/{pkg}/edits/{edit}/tracks/{track}", tok, {"releases": [new_rel]})

    # commit — let changes be sent for review automatically (Play API v3
    # now rejects changesNotSentForReview=true with 400)
    api(
        "POST",
        f"/applications/{pkg}/edits/{edit}:commit",
        tok,
        body={},
    )
    print(f"version_code={vc}")


def cmd_fetch_universal(pkg: str, vc: int, outdir: str) -> None:
    tok = token()
    list_path = f"/applications/{pkg}/generatedApks/{vc}"
    data = None
    for attempt in range(8):  # ~2 min total, generation can lag commit
        try:
            data = api("GET", list_path, tok)
            if data.get("generatedApks"):
                break
            raise Fail("empty generatedApks")
        except Fail:
            if attempt == 7:
                raise Fail(f"no generated APKs for {pkg} v{vc} after retries")
            time.sleep(15)
    candidates: list[str] = []
    for entry in data["generatedApks"]:
        # Preferred: the dedicated "signed, universal APK" generated for the AAB.
        gen = entry.get("generatedUniversalApk") or {}
        if gen.get("downloadId"):
            candidates.insert(0, gen["downloadId"])
            continue
        # Fallback: accept any non-config base split (per-device universals).
        for s in entry.get("generatedSplitApks", []):
            try:
                decoded = base64.b64decode(s["downloadId"]).decode()
            except Exception:
                continue
            parts = decoded.split(";")
            if not parts or parts[0] == "archived":
                continue
            if len(parts) < 3 or parts[1] != "split":
                continue
            segs = parts[2].split(",")
            module = segs[1] if len(segs) > 1 else ""
            extras = segs[2:] if len(segs) > 2 else []
            if any(x.startswith("config.") for x in extras):
                continue
            if module and module != "base":
                continue
            candidates.append(s["downloadId"])
    if not candidates:
        raise Fail(f"no universal base APK found for {pkg} v{vc}")
    did = candidates[0]
    out_path = os.path.join(outdir, "play-signed-universal.apk")
    os.makedirs(outdir, exist_ok=True)
    hdrs, blob = api(
        "GET",
        f"{API}/applications/{pkg}/generatedApks/{vc}/downloads/{did}:download?alt=media",
        tok,
        raw=True,
        timeout=300,
    )
    if not blob or len(blob) < 10_000:
        raise Fail("downloaded APK looks empty/corrupt")
    with open(out_path, "wb") as f:
        f.write(blob)
    h1 = hdrs.get("x-goog-hash") or ""
    print(f"saved {out_path} ({len(blob)} bytes) variant={candidates[0][0]};{h1[:60]}")


def main() -> None:
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)

    p_nv = sub.add_parser("next-version-code")
    p_nv.add_argument("pkg")

    p_up = sub.add_parser("upload")
    p_up.add_argument("pkg")
    p_up.add_argument("aab")
    p_up.add_argument("--track", default="production")
    p_up.add_argument("--staged-fraction", type=float, default=1.0)
    p_up.add_argument("--release-notes", default="", help="en-US release notes")

    p_fetch = sub.add_parser("fetch-universal-apk")
    p_fetch.add_argument("pkg")
    p_fetch.add_argument("version_code", type=int)
    p_fetch.add_argument("--out", default="app/release")

    args = p.parse_args()
    if not os.environ.get("PLAY_SERVICE_ACCOUNT_JSON_PATH"):
        raise Fail("set PLAY_SERVICE_ACCOUNT_JSON_PATH to the SA key path")
    if args.cmd == "next-version-code":
        cmd_next_version_code(args.pkg)
    elif args.cmd == "upload":
        cmd_upload(args.pkg, args.aab, args.track, args.staged_fraction, args.release_notes)
    elif args.cmd == "fetch-universal-apk":
        cmd_fetch_universal(args.pkg, args.version_code, args.out)


if __name__ == "__main__":
    main()
