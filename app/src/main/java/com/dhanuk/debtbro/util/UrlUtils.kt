package com.dhanuk.debtbro.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun openUrl(context: Context, url: String) {
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.onFailure { e ->
        val msg = when (e) {
            is ActivityNotFoundException -> "No browser found to open this link"
            else -> "Could not open link"
        }
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
