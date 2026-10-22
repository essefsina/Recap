package com.essef.recap

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/**
 * On-device AI: Vosk speech recognition (bundled offline model), plus local
 * title / summary generation from the transcript. No audio ever leaves the phone.
 */
object AiEngine {

    data class Segment(val speaker: String, val startSec: Double, val text: String)

    @Volatile private var model: Model? = null
    @Volatile private var modelFailed = false

    fun ensureModel(ctx: Context): Boolean {
        model?.let { return true }
        if (modelFailed) return false
        synchronized(this) {
            model?.let { return true }
            return try {
                val dir = File(ctx.filesDir, "model-en-us")
                if (!File(dir, "am/final.mdl").exists() && !File(dir, "am").exists()) {
                    copyAssetDir(ctx, "model-en-us", dir)
                } else if (!dir.exists() || (dir.list()?.isEmpty() != false)) {
                    copyAssetDir(ctx, "model-en-us", dir)
                }
                model = Model(dir.absolutePath)
                true
            } catch (e: Exception) {
                modelFailed = true
                false
            }
        }
    }

    private fun copyAssetDir(ctx: Context, assetPath: String, outDir: File) {
        val list = ctx.assets.list(assetPath) ?: return
        if (list.isEmpty()) {
            // it's a file
            ctx.assets.open(assetPath).use { input ->
                FileOutputStream(outDir).use { input.copyTo(it) }
            }
            return
        }
        outDir.mkdirs()
        for (entry in list) {
            val childAsset = "$assetPath/$entry"
            val childOut = File(outDir, entry)
            val sub = ctx.assets.list(childAsset)
            if (sub != null && sub.isNotEmpty()) {
                copyAssetDir(ctx, childAsset, childOut)
            } else {
                ctx.assets.open(childAsset).use { input ->
                    FileOutputStream(childOut).use { input.copyTo(it) }
                }
            }
        }
    }

    // ---------- WAV handling ----------

    class WavInfo(val channels: Int, val rate: Int, val channelPcm: List<ByteArray>)

    fun readWav(file: File): WavInfo? {
        return try {
            val bytes = file.readBytes()
            if (bytes.size < 44) return null
            val channels = bytes[22].toInt() and 0xff or ((bytes[23].toInt() and 0xff) shl 8)
            val rate = (bytes[24].toInt() and 0xff) or ((bytes[25].toInt() and 0xff) shl 8) or
                ((bytes[26].toInt() and 0xff) shl 16) or ((bytes[27].toInt() and 0xff) shl 24)
            // find data chunk
            var off = 12
            var dataOff = 44
            var dataLen = bytes.size - 44
            while (off + 8 <= bytes.size) {
                val id = String(bytes, off, 4)
                val len = (bytes[off + 4].toInt() and 0xff) or ((bytes[off + 5].toInt() and 0xff) shl 8) or
                    ((bytes[off + 6].toInt() and 0xff) shl 16) or ((bytes[off + 7].toInt() and 0xff) shl 24)
                if (id == "data") { dataOff = off + 8; dataLen = len; break }
                off += 8 + len
            }
            val frames = dataLen / (2 * channels)
            val per = Array(channels) { ByteArray(frames * 2) }
            for (f in 0 until frames) {
                for (ch in 0 until channels) {
                    val src = dataOff + (f * channels + ch) * 2
                    per[ch][f * 2] = bytes[src]
                    per[ch][f * 2 + 1] = bytes[src + 1]
                }
            }
            WavInfo(channels, rate, per.toList())
        } catch (e: Exception) { null }
    }

    fun resampleTo16k(pcm: ByteArray, fromRate: Int): ByteArray {
        if (fromRate == 16000) return pcm
        val n = pcm.size / 2
        val samples = ShortArray(n)
        for (i in 0 until n) {
            samples[i] = ((pcm[i * 2].toInt() and 0xff) or ((pcm[i * 2 + 1].toInt()) shl 8)).toShort()
        }
        val ratio = fromRate.toDouble() / 16000.0
        val outN = (n / ratio).toInt()
        val out = ByteArray(outN * 2)
        for (i in 0 until outN) {
            val pos = i * ratio
            val i0 = pos.toInt()
            val i1 = minOf(i0 + 1, n - 1)
            val frac = pos - i0
            val v = (samples[i0] * (1 - frac) + samples[i1] * frac).roundToInt()
            out[i * 2] = (v and 0xff).toByte()
            out[i * 2 + 1] = (v shr 8 and 0xff).toByte()
        }
        return out
    }

    // ---------- Transcription ----------

    private fun recognize(pcm16k: ByteArray, speaker: String, model: Model, onProgress: (Int) -> Unit): List<Segment> {
        val segs = mutableListOf<Segment>()
        val rec = Recognizer(model, 16000f)
        try { rec.setWords(true) } catch (e: Exception) { }
        val chunk = 16000 // 0.5s of 16kHz 16-bit mono
        var fed = 0
        var segStart = 0.0
        var i = 0
        while (i < pcm16k.size) {
            val len = minOf(chunk, pcm16k.size - i)
            val part = pcm16k.copyOfRange(i, i + len)
            if (rec.acceptWaveForm(part, len)) {
                val txt = extractText(rec.result)
                if (txt.isNotBlank()) {
                    segs.add(Segment(speaker, segStart, txt))
                }
                segStart = (fed + len).toDouble() / 32000.0
            }
            fed += len
            i += len
            onProgress((i * 100) / maxOf(1, pcm16k.size))
        }
        val tail = extractText(rec.finalResult)
        if (tail.isNotBlank()) segs.add(Segment(speaker, segStart, tail))
        rec.close()
        return segs
    }

    private fun extractText(resultJson: String): String {
        return try {
            JSONObject(resultJson).optString("text").trim()
        } catch (e: Exception) { "" }
    }

    fun transcribeWav(
        ctx: Context, wav: File, source: String, callerName: String,
        onProgress: (String) -> Unit
    ): List<Segment> {
        if (!ensureModel(ctx)) return emptyList()
        val m = model ?: return emptyList()
        val info = readWav(wav) ?: return emptyList()
        val caller = callerName.ifBlank { "Caller" }
        val out = mutableListOf<Segment>()
        if (info.channels >= 2) {
            onProgress("Listening to the caller…")
            val chCaller = resampleTo16k(info.channelPcm[0], info.rate)
            out.addAll(recognize(chCaller, caller, m) { })
            onProgress("Listening to you…")
            val chYou = resampleTo16k(info.channelPcm[1], info.rate)
            out.addAll(recognize(chYou, "You", m) { })
        } else {
            onProgress("Listening…")
            val mono = resampleTo16k(info.channelPcm[0], info.rate)
            val label = if (source == "mic") "You" else "Call"
            out.addAll(recognize(mono, label, m) { p -> onProgress("Listening… $p%") })
        }
        return out.sortedBy { it.startSec }
    }

    fun segmentsToText(segs: List<Segment>): String =
        segs.joinToString("\n") { "${it.speaker}|${"%.1f".format(it.startSec)}|${it.text}" }

    fun plainText(segs: List<Segment>): String =
        segs.joinToString(" ") { it.text }

    // ---------- Voicemail decode (AMR/MP3/AAC -> 16k WAV) ----------

    fun decodeToWav(ctx: Context, uri: Uri, outFile: File): Boolean {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(ctx, uri, null)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { track = i; format = f; break }
            }
            if (track < 0 || format == null) { extractor.release(); return false }
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()
            val pcm = mutableListOf<Byte>()
            val bufInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val inRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 8000
            val inChannels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1
            val tmpSamples = mutableListOf<Short>()
            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val size = extractor.readSampleData(inBuf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(bufInfo, 10000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)!!
                    val chunk = ByteArray(bufInfo.size)
                    outBuf.position(bufInfo.offset)
                    outBuf.limit(bufInfo.offset + bufInfo.size)
                    outBuf.get(chunk)
                    // downmix to mono shorts
                    val frames = chunk.size / 2 / inChannels
                    for (f in 0 until frames) {
                        var acc = 0
                        for (ch in 0 until inChannels) {
                            val idx = (f * inChannels + ch) * 2
                            acc += (chunk[idx].toInt() and 0xff) or ((chunk[idx + 1].toInt()) shl 8)
                        }
                        tmpSamples.add((acc / inChannels).toShort())
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            codec.stop(); codec.release(); extractor.release()
            val mono = ByteArray(tmpSamples.size * 2)
            for (i in tmpSamples.indices) {
                mono[i * 2] = (tmpSamples[i].toInt() and 0xff).toByte()
                mono[i * 2 + 1] = (tmpSamples[i].toInt() shr 8 and 0xff).toByte()
            }
            val pcm16 = resampleTo16k(mono, inRate)
            writeWavFile(outFile, pcm16, 16000, 1)
            true
        } catch (e: Exception) {
            try { extractor.release() } catch (e2: Exception) { }
            false
        }
    }

    private fun writeWavFile(out: File, data: ByteArray, rate: Int, channels: Int) {
        val raf = RandomAccessFile(out, "rw")
        val header = ByteArray(44)
        fun putInt(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte(); header[off + 1] = (v shr 8 and 0xff).toByte()
            header[off + 2] = (v shr 16 and 0xff).toByte(); header[off + 3] = (v shr 24 and 0xff).toByte()
        }
        fun putShort(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte(); header[off + 1] = (v shr 8 and 0xff).toByte()
        }
        "RIFF".toByteArray().copyInto(header, 0); putInt(4, 36 + data.size)
        "WAVE".toByteArray().copyInto(header, 8); "fmt ".toByteArray().copyInto(header, 12)
        putInt(16, 16); putShort(20, 1); putShort(22, channels)
        putInt(24, rate); putInt(28, rate * channels * 2); putShort(32, channels * 2); putShort(34, 16)
        "data".toByteArray().copyInto(header, 36); putInt(40, data.size)
        raf.write(header); raf.write(data); raf.close()
    }

    // ---------- Titles & summaries (local, extractive) ----------

    private val WEEKDAYS = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")

    fun detectCaller(text: String): String {
        val patterns = listOf(
            Regex("""this is ([a-z]+(?: [a-z]+)?) from ([a-z][a-z .&]+)""", RegexOption.IGNORE_CASE),
            Regex("""my name is ([a-z]+(?: [a-z]+)?)(?: from ([a-z][a-z .&]+))?""", RegexOption.IGNORE_CASE),
            Regex("""(?:it's|it is|i'm|i am) ([a-z]+) from ([a-z][a-z .&]+)""", RegexOption.IGNORE_CASE)
        )
        for (p in patterns) {
            val m = p.find(text) ?: continue
            val name = m.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val org = m.groupValues.getOrNull(2)?.trim()?.trim('.', ',') ?: ""
            val bad = setOf("calling", "here", "sorry", "just", "not", "the", "a")
            if (name.lowercase() in bad) continue
            return if (org.isNotEmpty() && org.length < 30) {
                name + " · " + org.split(" ").joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
            } else name
        }
        return ""
    }

    fun makeTitle(transcriptText: String): String {
        val t = " " + transcriptText.lowercase() + " "
        fun has(vararg words: String) = words.all { t.contains(it) }
        fun weekday(): String? = WEEKDAYS.firstOrNull { t.contains(" $it") || t.contains(" $it ") }
            ?.replaceFirstChar { it.uppercase() }
        val label = when {
            has("appointment") && (has("reschedul") || has("move") || has("moved")) ->
                "Appointment Rescheduled" + (weekday()?.let { " to $it" } ?: "")
            has("appointment") && has("cancel") -> "Appointment Cancelled"
            has("appointment") && (has("confirm") || has("reminder") || has("remind")) ->
                "Appointment Reminder" + (weekday()?.let { " · $it" } ?: "")
            has("appointment") -> "Appointment"
            has("package") || has("delivery") -> "Delivery Update"
            has("invoice") || has("payment") || has("bill") -> "Payment & Billing"
            has("insurance") && has("quote") -> "Car Insurance Quote"
            has("quote") -> "Quote Follow-Up"
            has("interview") -> "Interview"
            has("prescription") || has("refill") || has("pharmacy") -> "Pharmacy"
            has("reservation") -> "Reservation"
            has("invoice") -> "Invoice"
            has("call me back") || has("call back") || has("callback") -> "Callback Requested"
            has("meeting") -> "Meeting"
            else -> ""
        }
        return label
    }

    fun summarize(transcriptText: String): String {
        val sentences = transcriptText
            .replace(Regex("\\s+"), " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.split(" ").size in 4..45 }
        if (sentences.isEmpty()) {
            val words = transcriptText.split(" ").take(60).joinToString(" ")
            return words
        }
        val stop = setOf("the", "a", "an", "and", "or", "to", "of", "in", "on", "is", "it", "i", "you", "we", "that", "this", "for", "with", "me", "my", "your", "so", "um", "uh", "yeah", "okay")
        val freq = mutableMapOf<String, Int>()
        for (w in transcriptText.lowercase().split(Regex("[^a-z]+"))) {
            if (w.length > 2 && w !in stop) freq[w] = (freq[w] ?: 0) + 1
        }
        val scored = sentences.map { s ->
            val score = s.lowercase().split(Regex("[^a-z]+")).sumOf { freq[it] ?: 0 }
            s to score
        }
        val top = scored.sortedByDescending { it.second }.take(3).map { it.first }
        val paragraph = sentences.filter { it in top }.joinToString(" ")
        val actionWords = listOf("bring", "arrive", "call", "send", "pay", "confirm", "don't forget", "need to", "please", "remember")
        val bullets = sentences.filter { s ->
            val low = s.lowercase()
            actionWords.any { low.contains(it) } || WEEKDAYS.any { low.contains(it) } ||
                Regex("\\d{1,2}:\\d{2}").containsMatchIn(s)
        }.distinct().take(3)
        return if (bullets.isEmpty()) paragraph
        else paragraph + "\n" + bullets.joinToString("\n") { "• $it" }
    }
}
