package com.dhanuk.debtbro.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.util.LocalizedString
import kotlinx.coroutines.launch

/**
 * Handles the POST_NOTIFICATIONS runtime permission flow for Android 13+ (API 33).
 *
 * Fix history (2026-07-03, offline-mode audit):
 *  - Previously branched on `context is AppCompatActivity`, but
 *    `MainActivity` extends `ComponentActivity`, so both rationale and
 *    permanently-denied branches were dead code — the rationale dialog
 *    re-popped on every launch and the system permission was never actually
 *    requested through the launcher. Now we use `Activity` directly: the
 *    rationale API is on `Activity` (via ActivityCompat), and the launcher
 *    works from any `ActivityResultRegistry` owner (which `ComponentActivity`
 *    is).
 *  - Now re-queries permission state on every `ON_RESUME` (e.g. when the
 *    user returns from the system Settings page) so the UI reflects the
 *    new grant status without a process restart.
 *
 * Flow:
 *  1. If permission already granted -> nothing shown
 * 2. If not yet asked -> show rationale dialog with "Why we need it" explanation
 * 3. If user taps "Allow" on rationale -> launch system permission request
 * 4. If user denies twice -> show settings dialog (system dialog won't show again)
 */
@Composable
fun NotificationPermissionHandler(
    appPreferences: AppPreferences,
    onPermissionHandled: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Don't show anything on Android < 13 (permission not runtime on older OS)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        content()
        return
    }

    val permission = Manifest.permission.POST_NOTIFICATIONS
    var showRationale by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var permissionStatus by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        )
    }
    val rationaleDismissed by appPreferences.notificationRationaleDismissed
        .collectAsStateWithLifecycle(initialValue = false)

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionStatus = isGranted
        if (!isGranted) {
            // Activity is the LocalContext; shouldShowRequestPermissionRationale
            // works against any Activity (CompatActivity or ComponentActivity).
            // If the system no longer offers the rationale, the user has
            // permanently denied us — point to system Settings.
            val activity = context as? Activity
            if (activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            ) {
                showSettings = true
            }
        }
    }

    if (permissionStatus) {
        content()
        return
    }

    // Re-query on resume: if the user granted from Settings while we were
    // backgrounded, collapse the dialogs and let content render.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val isGranted = ContextCompat.checkSelfPermission(
                    context, permission
                ) == PackageManager.PERMISSION_GRANTED
                permissionStatus = isGranted
                if (!isGranted && !rationaleDismissed) {
                    val activity = context as? Activity
                    if (activity != null &&
                        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                    ) {
                        // Either first launch (never asked) OR permanently
                        // denied. If rationale returns false AND we've shown
                        // the rationale before on a prior launch, the user
                        // has permanently denied us — route to Settings.
                        // Respect the persisted "don't ask again" preference
                        // (Phase D, 2026-07-03) so we don't keep nagging on
                        // every resume if they already dismissed us.
                        showSettings = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        ) {
            // System says we should show rationale (user denied once but
            // not permanently).
            showRationale = true
        } else if (ContextCompat.checkSelfPermission(context, permission)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // First time asking — show our educational rationale before
            // launching the system prompt. But honor the user's previous
            // "Not now" as a persistent preference (Phase D, 2026-07-03).
            // If we were not yet granted AND rationale is not surfaced
            // by the system, fall back to the persistent-dismissed flag.
            if (!rationaleDismissed) {
                showRationale = true
            }
        }
        onPermissionHandled()
    }

    // Auto-clear the dismissed flag once the user grants the permission,
    // so the rationale becomes available again on a future install/upgrade.
    LaunchedEffect(permissionStatus) {
        if (permissionStatus && rationaleDismissed) {
            appPreferences.setNotificationRationaleDismissed(false)
        }
    }

    content()

    when {
        showRationale -> {
            NotificationRationaleDialog(
                onAllow = {
                    showRationale = false
                    launcher.launch(permission)
                },
                onDeny = {
                    showRationale = false
                    scope.launch { appPreferences.setNotificationRationaleDismissed(true) }
                }
            )
        }
        showSettings -> {
            NotificationSettingsDialog(
                onDismiss = { showSettings = false }
            )
        }
    }
}

@Composable
private fun NotificationRationaleDialog(onAllow: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = { Text(LocalizedString.get("notification_permission_title"), fontWeight = FontWeight.Bold) },
        text = {
            androidx.compose.foundation.layout.Column(
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    LocalizedString.get("notification_permission_desc"),
                    textAlign = TextAlign.Center
                )
                Text(
                    "\u2022 Reminders for due dates\n\u2022 Weekly spending summaries\n\u2022 Payment alerts from friends",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAllow) {
                Text(LocalizedString.get("allow"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDeny) {
                Text(LocalizedString.get("not_now"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun NotificationSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        },
        title = { Text("Notifications Blocked", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                LocalizedString.get("notification_permission_denied"),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
                onDismiss()
            }) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(LocalizedString.get("cancel"))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
