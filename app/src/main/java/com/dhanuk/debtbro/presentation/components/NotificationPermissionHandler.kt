package com.dhanuk.debtbro.presentation.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dhanuk.debtbro.util.LocalizedString

/**
 * Handles the POST_NOTIFICATIONS runtime permission flow for Android 13+ (API 33).
 *
 * Wallet-friendly and consent-friendly flow:
 * 1. If permission already granted -> nothing shown
 * 2. If not yet asked -> show rationale dialog with "Why we need it" explanation
 * 3. If user taps "Allow" on rationale -> launch system permission request
 * 4. If user denies twice -> show settings dialog (system dialog won't show again)
 */
@Composable
fun NotificationPermissionHandler(
    onPermissionHandled: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

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

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionStatus = isGranted
        if (!isGranted) {
            if (context is AppCompatActivity && !ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
                // User denied permanently, show settings dialog
                showSettings = true
            }
        }
    }

    if (permissionStatus) {
        content()
        return
    }

    LaunchedEffect(Unit) {
        if (context is AppCompatActivity && ActivityCompat.shouldShowRequestPermissionRationale(context, permission)) {
            showRationale = true
        } else if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
            // First time ask or denied but not permanently
            showRationale = true
        }
        onPermissionHandled()
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
            androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
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
            Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.primary,                modifier = Modifier.size(40.dp))
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