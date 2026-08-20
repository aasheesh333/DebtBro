package com.dhanuk.debtbro.util

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * True when Google Play Services is installed and usable on this device.
 *
 * DebtBro offers "Sign in with Google" (CredentialManager + GetGoogleIdOption),
 * which requires Google Play Services. On devices WITHOUT GMS (some OPPO /
 * ColorOS review devices, HMS-only handsets, etc.) tapping that button would
 * surface a system "Download Google Play services" prompt — which app stores
 * such as OPPO reject as "Mandatory Download from Google Play".
 *
 * Gate every Google Sign-In entry point (SignUp / SignIn / Settings card) on
 * this check so the button is only shown where it can actually work, and
 * email/password + Skip remain the fallback everywhere else.
 */
fun isGmsAvailable(context: Context): Boolean {
    val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
    if (status == ConnectionResult.SUCCESS) return true
    // SUCCESS_API_MISSING_OR_DISABLED-equivalent false positives are caught by
    // the explicit SUCCESS match above; anything else means GMS is missing, too
    // old, or disabled — treat as unavailable so we never trigger the download
    // prompt.
    return false
}
