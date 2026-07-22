package com.dhanuk.debtbro.presentation.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactPickerBottomSheet(
    onDismiss: () -> Unit,
    onContactSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val extra = LocalExtraColors.current

    var permissionDenied by remember { mutableStateOf(false) }
    val contactPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri ->
        uri?.let {
            try {
                val name = runCatching {
                    context.contentResolver.query(
                        it, arrayOf(
                            android.provider.ContactsContract.Contacts.DISPLAY_NAME
                        ), null, null, null
                    )?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIndex = c.getColumnIndex(android.provider.ContactsContract.Contacts.DISPLAY_NAME)
                            if (nameIndex >= 0) c.getString(nameIndex) else null
                        } else null
                    }
                }.getOrNull()
                if (!name.isNullOrBlank()) onContactSelected(name)
            } catch (_: SecurityException) {
            }
        }
        onDismiss()
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            contactPickerLauncher.launch(null)
        } else {
            permissionDenied = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(UITokens.SheetContentPadding).padding(bottom = UITokens.SheetBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(UITokens.SpaceMedium)
        ) {
            Icon(Icons.Default.ContactPage, LocalizedString.get("select_contact"), modifier = Modifier.size(UITokens.IconXXL), tint = MaterialTheme.colorScheme.primary)
            Text(LocalizedString.get("select_contact"), color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontTitle, fontWeight = FontWeight.Bold)
            Text(LocalizedString.get("pick_from_contacts_desc"), color = extra.subtitleGray, textAlign = TextAlign.Center)
            if (permissionDenied) {
                Text(
                    LocalizedString.get("contacts_access_denied_toast"),
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    fontSize = UITokens.FontBody
                )
            }
            Button(
                onClick = {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.READ_CONTACTS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (granted) {
                        contactPickerLauncher.launch(null)
                    } else {
                        permissionDenied = false
                        permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = UITokens.ShapeMedium
            ) {
                Icon(Icons.Default.Contacts, LocalizedString.get("pick_contact"), tint = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(8.dp))
                Text(LocalizedString.get("pick_contact"), color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
