package com.essef.recap

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * The app icon follows the color theme. The manifest carries one
 * activity-alias per theme (IconForest, IconOcean, ...), each with its own
 * launcher icon whose background is that theme's deep shade. apply() enables
 * the alias matching the current theme and disables the rest; it runs at app
 * start and whenever the theme changes. The same artwork, recolored, is used
 * for the in-app logo via drawableFor().
 */
object AppIcon {
    private val suffixes = listOf("Forest", "Ocean", "Violet", "Sunset", "Rose")

    fun drawableFor(themeName: String): Int = when (themeName) {
        "Ocean" -> R.drawable.logo_ocean
        "Violet" -> R.drawable.logo_violet
        "Sunset" -> R.drawable.logo_sunset
        "Rose" -> R.drawable.logo_rose
        else -> R.drawable.logo_forest
    }

    fun apply(ctx: Context) {
        val theme = Prefs.get(ctx).themeName
        val pm = ctx.packageManager
        for (s in suffixes) {
            val cn = ComponentName(ctx.packageName, ctx.packageName + ".Icon" + s)
            val want = if (s == theme) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                       else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            try {
                if (pm.getComponentEnabledSetting(cn) != want) {
                    pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
                }
            } catch (e: Exception) { /* launcher keeps the previous icon */ }
        }
    }
}
