package com.essef.recap

import android.content.Context
import org.json.JSONObject
import java.io.File

/** AI metadata for a voicemail (the audio itself lives in the carrier's provider). */
data class VmMeta(
    val vmId: Long,
    var title: String = "",
    var detectedCaller: String = "",
    var summary: String = "",
    var transcript: String = ""
) {
    val hasTranscript get() = transcript.isNotBlank()
    val hasSummary get() = summary.isNotBlank()
}

object VmStore {
    private fun file(ctx: Context, vmId: Long): File {
        val d = File(ctx.filesDir, "voicemail")
        if (!d.exists()) d.mkdirs()
        return File(d, "$vmId.json")
    }

    fun load(ctx: Context, vmId: Long): VmMeta {
        val f = file(ctx, vmId)
        if (!f.exists()) return VmMeta(vmId)
        return try {
            val j = JSONObject(f.readText())
            VmMeta(
                vmId = vmId,
                title = j.optString("title"),
                detectedCaller = j.optString("detectedCaller"),
                summary = j.optString("summary"),
                transcript = j.optString("transcript")
            )
        } catch (e: Exception) { VmMeta(vmId) }
    }

    fun save(ctx: Context, m: VmMeta) {
        val j = JSONObject()
        j.put("title", m.title); j.put("detectedCaller", m.detectedCaller)
        j.put("summary", m.summary); j.put("transcript", m.transcript)
        file(ctx, m.vmId).writeText(j.toString())
    }
}
