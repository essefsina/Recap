package com.essef.recap

import android.content.Context
import org.json.JSONObject
import java.io.File

/** One saved call recording + its AI metadata (JSON sidecar next to the WAV). */
data class Recording(
    val id: String,
    val file: File,
    var number: String = "",
    var contactName: String = "",
    var direction: Int = 1, // 1 incoming, 2 outgoing
    var startMs: Long = 0,
    var durationSec: Long = 0,
    var simLabel: String = "",
    var source: String = "", // how audio was captured: dual / voicecall-stereo / voicecall / voicecomm / mic
    var callerSilent: Boolean = false,
    var silent: Boolean = false, // every capture path came back silent on this device
    var diag: String = "", // per-path measured levels, e.g. "dual=x;voicecall=0;mic=812"
    var title: String = "",
    var detectedCaller: String = "",
    var summary: String = "",
    var transcript: String = "" // lines: "speaker|startSec|text"
) {
    val hasTranscript get() = transcript.isNotBlank()
    val hasSummary get() = summary.isNotBlank()
    val displayTitle get() = title.ifBlank { "Untitled recording" }

    fun toJson(): JSONObject {
        val j = JSONObject()
        j.put("number", number); j.put("contactName", contactName)
        j.put("direction", direction); j.put("startMs", startMs)
        j.put("durationSec", durationSec); j.put("simLabel", simLabel)
        j.put("source", source); j.put("callerSilent", callerSilent); j.put("silent", silent); j.put("diag", diag)
        j.put("title", title); j.put("detectedCaller", detectedCaller)
        j.put("summary", summary); j.put("transcript", transcript)
        return j
    }

    fun save(ctx: Context) {
        sidecar().writeText(toJson().toString())
    }

    fun sidecar(): File = File(file.parentFile, file.nameWithoutExtension + ".json")

    companion object {
        fun load(f: File): Recording? {
            val side = File(f.parentFile, f.nameWithoutExtension + ".json")
            if (!side.exists()) return null
            return try {
                val j = JSONObject(side.readText())
                Recording(
                    id = f.nameWithoutExtension, file = f,
                    number = j.optString("number"), contactName = j.optString("contactName"),
                    direction = j.optInt("direction", 1), startMs = j.optLong("startMs"),
                    durationSec = j.optLong("durationSec"), simLabel = j.optString("simLabel"),
                    source = j.optString("source"), callerSilent = j.optBoolean("callerSilent"),
                    silent = j.optBoolean("silent"), diag = j.optString("diag"),
                    title = j.optString("title"), detectedCaller = j.optString("detectedCaller"),
                    summary = j.optString("summary"), transcript = j.optString("transcript")
                )
            } catch (e: Exception) { null }
        }
    }
}

object RecStore {
    fun dir(ctx: Context): File {
        val d = File(ctx.filesDir, "recordings")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun all(ctx: Context): List<Recording> {
        val files = dir(ctx).listFiles { f -> f.extension == "wav" || f.extension == "m4a" } ?: return emptyList()
        return files.mapNotNull { Recording.load(it) }.sortedByDescending { it.startMs }
    }

    fun byId(ctx: Context, id: String): Recording? {
        for (ext in listOf("wav", "m4a")) {
            val f = File(dir(ctx), "$id.$ext")
            if (f.exists()) return Recording.load(f)
        }
        return null
    }

    /** Find the recording that belongs to a call-log entry (same number, close in time). */
    fun match(ctx: Context, number: String, dateMs: Long): Recording? {
        val want = digits(number)
        if (want.isEmpty()) return null
        return all(ctx).firstOrNull { r ->
            digits(r.number) == want && kotlin.math.abs(r.startMs - dateMs) < 180_000
        }
    }

    fun digits(s: String): String = s.filter { it.isDigit() }.takeLast(10)

    /** Enforce the Settings auto-delete choice (0 = never). Deletes audio + sidecar. */
    fun autoDelete(ctx: Context) {
        val days = ctx.getSharedPreferences("recap", Context.MODE_PRIVATE).getInt("autoDeleteDays", 0)
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L
        for (r in all(ctx)) {
            if (r.startMs in 1 until cutoff) {
                r.file.delete()
                r.sidecar().delete()
            }
        }
    }
}
