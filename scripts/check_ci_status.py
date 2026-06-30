import json
import sys
from playwright.sync_api import sync_playwright

REPO = "aasheesh333/DebtBro"


def main():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()

        # 1. Navigate to Actions
        page.goto(f"https://github.com/{REPO}/actions")
        page.wait_for_selector('a[href*="/runs/"]', timeout=10000)

        # Extract latest runs
        runs = page.evaluate('''() => {
            const links = [...document.querySelectorAll('a[href*="/runs/"]')];
            return links.map(a => {
                const row = a.closest('article, .d-table-row') || a.parentElement;
                const statusEl = row.querySelector('svg[aria-label]');
                return {
                    href: a.href,
                    text: a.textContent.trim().slice(0, 80),
                    status: statusEl ? statusEl.getAttribute('aria-label') : 'unknown'
                };
            }).filter(r => r.status !== 'unknown');
        }''')

        # 2. Latest run details
        latest_run = runs[0] if runs else None
        artifacts = []
        annotations = []

        if latest_run:
            page.goto(latest_run['href'])
            page.wait_for_timeout(2000)

            artifacts = page.evaluate('''() => {
                const items = [...document.querySelectorAll('tr, .Box-body > div')];
                return items.map(row => {
                    const cells = [...row.querySelectorAll('td, span, a')]
                        .map(c => c.textContent.trim());
                    return cells.filter(c =>
                        c.includes('DebtBro-AAB') ||
                        c.includes('DebtBro- Alt') ||
                        c.includes('MB') ||
                        c.includes('KB')
                    );
                }).filter(arr => arr.length > 0);
            }''')

            annotations = page.evaluate('''() => {
                return [...document.querySelectorAll('.annotation--contracted, [class*="annotation"]')]
                    .map(a => a.textContent.trim()).filter(t => t.length > 0);
            }''')

        # Print results
        print(json.dumps({
            "latest_run": latest_run,
            "artifacts": artifacts,
            "annotations": annotations
        }, indent=2))

        browser.close()


if __name__ == "__main__":
    main()
