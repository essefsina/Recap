package com.essef.recap

import android.content.Context
import android.graphics.Color

/** Color themes (approved design): five matching hues, each with light + dark surfaces. */
data class ThemeColors(
    val name: String,
    val primary: Int,
    val bg: Int,
    val surface: Int,
    val container: Int,
    val softContainer: Int,
    val onSurface: Int,
    val muted: Int,
    val faint: Int,
    val playerBg: Int
)

object Themes {
    private fun c(hex: String) = Color.parseColor(hex)

    private data class Hue(val name: String, val primary: String, val contL: String, val softL: String)

    private val hues = listOf(
        Hue("Forest", "#0C7255", "#D9EDE2", "#E0EFE6"),
        Hue("Ocean", "#0B5FA5", "#D8EAF8", "#E2EFFA"),
        Hue("Violet", "#6C4AB0", "#E7DEF7", "#EEE8FA"),
        Hue("Sunset", "#B45F12", "#F7E8D2", "#FAEEDD"),
        Hue("Rose", "#B03A5B", "#F8DEE7", "#FAE9EF")
    )

    val names: List<String> get() = hues.map { it.name }
    fun primaryOf(name: String): Int = c(hues.firstOrNull { it.name == name }?.primary ?: "#0C7255")

    fun get(ctx: Context): ThemeColors {
        val p = Prefs.get(ctx)
        val h = hues.firstOrNull { it.name == p.themeName } ?: hues[0]
        return if (!p.darkMode) {
            ThemeColors(
                h.name, c(h.primary),
                bg = c("#F5F7F4"), surface = c("#FFFFFF"), container = c(h.contL),
                softContainer = c(h.softL), onSurface = c("#13211B"),
                muted = c("#5C6B64"), faint = c("#8A978F"), playerBg = c("#13211B")
            )
        } else {
            ThemeColors(
                h.name, c(h.primary),
                bg = c("#0F1512"), surface = c("#1A211D"), container = c("#24312A"),
                softContainer = c("#202B25"), onSurface = c("#E8F0EA"),
                muted = c("#9DB4A8"), faint = c("#6E7F76"), playerBg = c("#000000")
            )
        }
    }
}

class Prefs private constructor(val map: Map<String, Any?>) {
    val themeName: String get() = map["theme"] as? String ?: "Forest"
    val darkMode: Boolean get() = map["dark"] as? Boolean ?: false
    /** "auto" = record every call, "ask" = ask on each call. */
    val recMode: String get() = map["recMode"] as? String ?: "auto"
    val consent: Boolean get() = map["consent"] as? Boolean ?: true
    /** WAV sample rate: 16000 (AI-ready) or 44100 (high). */
    val sampleRate: Int get() = (map["sampleRate"] as? Int) ?: 16000
    val autoTitle: Boolean get() = map["autoTitle"] as? Boolean ?: true
    val autoSummarize: Boolean get() = map["autoSummarize"] as? Boolean ?: true
    /** Phone-account id of the SIM to always call from; "" = ask before every call. */
    val defaultSimId: String get() = map["defaultSim"] as? String ?: ""
    /** Floating call bubble over other apps (games, fullscreen) during a call. */
    val floatBubble: Boolean get() = map["floatBubble"] as? Boolean ?: true

    companion object {
        @Volatile private var cache: Prefs? = null

        fun get(ctx: Context): Prefs {
            cache?.let { return it }
            val sp = ctx.getSharedPreferences("recap", Context.MODE_PRIVATE)
            val p = Prefs(sp.all)
            cache = p
            return p
        }

        fun put(ctx: Context, key: String, value: Any) {
            val sp = ctx.getSharedPreferences("recap", Context.MODE_PRIVATE)
            val e = sp.edit()
            when (value) {
                is String -> e.putString(key, value)
                is Boolean -> e.putBoolean(key, value)
                is Int -> e.putInt(key, value)
            }
            e.apply()
            cache = null
        }
    }
}
