package com.dhanuk.debtbro.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

object ActivityFinder {
    fun find(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun getOrThrow(context: Context): Activity {
        return find(context) ?: error("No Activity found in context hierarchy")
    }
}
