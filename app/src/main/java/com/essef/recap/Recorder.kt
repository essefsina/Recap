package com.essef.recap

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.sqrt

/**
 * Call recording engine — two capture stacks, nine paths, all metered.
 *
 * AudioRecord paths, in order: dual (VOICE_UPLINK + VOICE_DOWNLINK),
 * voicecall-stereo, voicecall, voicecomm (VOICE_COMMUNICATION),
 * voice-recognition, mic, unprocessed. Then the MediaRecorder stack (a
 * genuinely different code path through the framework): mr-voicecall,
 * mr-voicecomm, mr-mic — saved as .m4a.
 *
 * Capture sessions hold voice-communication audio focus and every
 * AudioRecord is built with setContext() attribution: Android's audio policy
 * silences captures that aren't attributed to the call app while a call is
 * up (v0.6.0 measured exactly that — even the plain mic read digital zeros
 * in-call on Essef's Motorola while the same mic was loud outside a call).
 *
 * A source INITIALIZING proves nothing on modern Android: several OEMs hand
 * back a live recorder that only ever delivers zeros. A watchdog meters the
 * actual samples (RMS for AudioRecord, getMaxAmplitude for MediaRecorder);
 * a path that stays digitally silent through two checks is torn down and the
 * engine hops to the next candidate, discarding the silent segment. Every
 * path's measured level lands in Recording.diag, so a screenshot of the
 * detail screen is a full diagnostic report.
 *
 * At stop, quiet-but-real audio gets a bounded peak gain lift (<= 16x).
 * A recording silent on every path is flagged `silent`.
 */
object Recorder {

    data class Meta(val number: String, val name: String, val direction: Int, val simLabel: String)

    private val AR_CANDIDATES = listOf("dual", "voicecall-stereo", "voicecall", "voicecomm", "voice-recognition", "mic", "unprocessed")
    private val MR_CANDIDATES = listOf("mr-voicecall", "mr-voicecomm", "mr-mic")
    private const val SILENCE_RMS = 12.0
    private const val MR_SILENCE_AMP = 400

    private class Seg(val mode: String, val records: List<AudioRecord>, val files: List<File>) {
        @Volatile var running = true
        var threads: List<Thread> = emptyList()
        var silentChecks = 0
        val sums = DoubleArray(2)
        val counts = LongArray(2)
        val startedAt = System.currentTimeMillis()
        fun rms(ch: Int): Double = if (counts[ch] > 0) sqrt(sums[ch] / counts[ch]) else 0.0
        fun bestRms(): Double = maxOf(rms(0), rms(1))
    }

    private class Session(
        val ctx: Context,
        val meta: Meta,
        val rate: Int,
        val id: String,
        val dir: File,
        val starter: String
    ) {
        @Volatile var running = true
        // AudioRecord state
        var seg: Seg? = null
        var nextAr = 0
        // MediaRecorder state
        var mr: MediaRecorder? = null
        var mrMode: String = ""
        var mrFile: File? = null
        var mrPeak = 0
        var mrStartedAt = 0L
        var mrSilentChecks = 0
        var nextMr = 0
        // Bookkeeping
        @Volatile var hasAudio = false // watchdog saw real (non-silent) levels
        var bestMode: String = ""
        var bestRms: Double = 0.0
        var lastMode: String = ""
        val diag = linkedMapOf<String, String>()
        val startMs = System.currentTimeMillis()
        var watchdog: Thread? = null
    }

    @Volatile private var cur: Session? = null
    val isRecording: Boolean get() = cur != null
    var onStateChange: ((Boolean) -> Unit)? = null

    // ---------- AudioRecord plumbing ----------

    /** Capture attributes used for the session's audio-focus request,
     *  identifying this as the call app's voice-communication capture —
     *  the attribution Android's audio policy looks for when deciding
     *  whether an in-call capture gets real audio. (AudioRecord itself has
     *  no attributes API; per-record attribution is setContext(), below.) */
    private val voiceAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private var focusReq: AudioFocusRequest? = null

    private fun grabFocus(ctx: Context) {
        try {
            val am = ctx.getSystemService(AudioManager::class.java) ?: return
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(voiceAttrs)
                .setOnAudioFocusChangeListener { }
                .build()
            focusReq = req
            am.requestAudioFocus(req)
        } catch (e: Exception) { }
    }

    private fun dropFocus(ctx: Context) {
        try {
            val am = ctx.getSystemService(AudioManager::class.java) ?: return
            focusReq?.let { am.abandonAudioFocusRequest(it) }
        } catch (e: Exception) { }
        focusReq = null
    }

    private fun tryRecord(ctx: Context, source: Int, rate: Int, channels: Int): AudioRecord? {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(rate, channels, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) return null
            val rec = AudioRecord.Builder()
                .setAudioSource(source)
                .setContext(ctx)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(channels)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .build()
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release(); null
            } else rec
        } catch (e: Exception) { null }
    }

    private fun buildSeg(s: Session, mode: String): Seg? {
        val tag = s.id + "." + mode.replace('-', '_')
        return when (mode) {
            "dual" -> {
                val up = tryRecord(s.ctx, MediaRecorder.AudioSource.VOICE_UPLINK, s.rate, AudioFormat.CHANNEL_IN_MONO)
                val down = tryRecord(s.ctx, MediaRecorder.AudioSource.VOICE_DOWNLINK, s.rate, AudioFormat.CHANNEL_IN_MONO)
                if (up == null || down == null) {
                    up?.release(); down?.release(); null
                } else {
                    // files[0] = caller (downlink), files[1] = you (uplink)
                    Seg(mode, listOf(down, up), listOf(File(s.dir, "$tag.down.pcm"), File(s.dir, "$tag.up.pcm")))
                }
            }
            "voicecall-stereo" -> {
                val r = tryRecord(s.ctx, MediaRecorder.AudioSource.VOICE_CALL, s.rate, AudioFormat.CHANNEL_IN_STEREO)
                if (r == null) null else Seg(mode, listOf(r), listOf(File(s.dir, "$tag.pcm")))
            }
            "voicecall" -> {
                val r = tryRecord(s.ctx, MediaRecorder.AudioSource.VOICE_CALL, s.rate, AudioFormat.CHANNEL_IN_MONO)
                if (r == null) null else Seg(mode, listOf(r), listOf(File(s.dir, "$tag.pcm")))
            }
            "voicecomm" -> {
                val r = tryRecord(s.ctx, MediaRecorder.AudioSource.VOICE_COMMUNICATION, s.rate, AudioFormat.CHANNEL_IN_MONO)
                if (r == null) null else Seg(mode, listOf(r), listOf(File(s.dir, "$tag.pcm")))
            }
            "voice-recognition" -> {
                // Different HAL input profile (speech-tuned); on some OEMs it
                // is not zeroed when the plain mic path is.
                val r = tryRecord(s.ctx, MediaRecorder.AudioSource.VOICE_RECOGNITION, s.rate, AudioFormat.CHANNEL_IN_MONO)
                if (r == null) null else Seg(mode, listOf(r), listOf(File(s.dir, "$tag.pcm")))
            }
            "unprocessed" -> {
                val r = tryRecord(s.ctx, MediaRecorder.AudioSource.UNPROCESSED, s.rate, AudioFormat.CHANNEL_IN_MONO)
                if (r == null) null else Seg(mode, listOf(r), listOf(File(s.dir, "$tag.pcm")))
            }
            else -> { // mic
                val r = tryRecord(s.ctx, MediaRecorder.AudioSource.MIC, s.rate, AudioFormat.CHANNEL_IN_MONO)
                if (r == null) null else Seg(mode, listOf(r), listOf(File(s.dir, "$tag.pcm")))
            }
        }
    }

    private fun launchSeg(s: Session, seg: Seg) {
        seg.threads = when (seg.mode) {
            "dual" -> listOf(
                pump(seg, seg.records[0], 0, seg.files[0]),
                pump(seg, seg.records[1], 1, seg.files[1])
            )
            "voicecall-stereo" -> listOf(pumpInterleaved(seg, seg.records[0], seg.files[0]))
            else -> listOf(pump(seg, seg.records[0], 0, seg.files[0]))
        }
    }

    private fun teardownSeg(seg: Seg, deleteFiles: Boolean) {
        seg.running = false
        for (r in seg.records) try { r.stop() } catch (e: Exception) { }
        for (t in seg.threads) try { t.join(1200) } catch (e: Exception) { }
        for (r in seg.records) try { r.release() } catch (e: Exception) { }
        if (deleteFiles) seg.files.forEach { try { it.delete() } catch (e: Exception) { } }
    }

    private fun pump(seg: Seg, rec: AudioRecord, channel: Int, file: File): Thread {
        val t = Thread {
            val fos = FileOutputStream(file)
            val buf = ShortArray(2048)
            try {
                rec.startRecording()
                while (seg.running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        var sum = 0.0
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            val v = buf[i]
                            sum += v.toDouble() * v
                            bytes[i * 2] = (v.toInt() and 0xff).toByte()
                            bytes[i * 2 + 1] = (v.toInt() shr 8 and 0xff).toByte()
                        }
                        seg.sums[channel] = seg.sums[channel] + sum
                        seg.counts[channel] = seg.counts[channel] + n
                        fos.write(bytes)
                    } else if (n < 0) break
                }
            } catch (e: Exception) { }
            try { fos.close() } catch (e: Exception) { }
            try { rec.stop() } catch (e: Exception) { }
            try { rec.release() } catch (e: Exception) { }
        }
        t.start()
        return t
    }

    private fun pumpInterleaved(seg: Seg, rec: AudioRecord, file: File): Thread {
        val t = Thread {
            val fos = FileOutputStream(file)
            val buf = ShortArray(4096)
            try {
                rec.startRecording()
                while (seg.running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n > 0) {
                        val frames = n / 2
                        var sumL = 0.0; var sumR = 0.0
                        val bytes = ByteArray(n * 2)
                        for (i in 0 until n) {
                            val v = buf[i]
                            if (i % 2 == 0) sumL += v.toDouble() * v else sumR += v.toDouble() * v
                            bytes[i * 2] = (v.toInt() and 0xff).toByte()
                            bytes[i * 2 + 1] = (v.toInt() shr 8 and 0xff).toByte()
                        }
                        seg.sums[0] = seg.sums[0] + sumL; seg.counts[0] = seg.counts[0] + frames
                        seg.sums[1] = seg.sums[1] + sumR; seg.counts[1] = seg.counts[1] + frames
                        fos.write(bytes)
                    } else if (n < 0) break
                }
            } catch (e: Exception) { }
            try { fos.close() } catch (e: Exception) { }
            try { rec.stop() } catch (e: Exception) { }
            try { rec.release() } catch (e: Exception) { }
        }
        t.start()
        return t
    }

    // ---------- MediaRecorder plumbing ----------

    private fun mrSource(mode: String): Int = when (mode) {
        "mr-voicecall" -> MediaRecorder.AudioSource.VOICE_CALL
        "mr-voicecomm" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        else -> MediaRecorder.AudioSource.MIC
    }

    private fun startMr(s: Session, mode: String): Boolean {
        val out = File(s.dir, s.id + ".m4a")
        try { if (out.exists()) out.delete() } catch (e: Exception) { }
        val mr = MediaRecorder()
        return try {
            mr.setAudioSource(mrSource(mode))
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setAudioSamplingRate(44100)
            mr.setAudioChannels(1)
            mr.setAudioEncodingBitRate(96000)
            mr.setOutputFile(out.absolutePath)
            mr.prepare()
            mr.start()
            s.mr = mr
            s.mrMode = mode
            s.mrFile = out
            s.mrPeak = 0
            s.mrSilentChecks = 0
            s.mrStartedAt = System.currentTimeMillis()
            s.lastMode = mode
            true
        } catch (e: Exception) {
            try { mr.release() } catch (e2: Exception) { }
            s.diag[mode] = "x"
            false
        }
    }

    private fun stopMr(s: Session, deleteFile: Boolean) {
        val mr = s.mr ?: return
        try { mr.stop() } catch (e: Exception) { }
        try { mr.release() } catch (e: Exception) { }
        s.mr = null
        if (deleteFile) try { s.mrFile?.delete() } catch (e: Exception) { }
    }

    // ---------- Lifecycle ----------

    @Synchronized
    fun start(ctx: Context, meta: Meta, starter: String = "activity"): String {
        if (cur != null) return cur!!.lastMode
        val rate = Prefs.get(ctx).sampleRate
        val id = "rec_" + System.currentTimeMillis()
        val dir = RecStore.dir(ctx)
        val s = Session(ctx.applicationContext, meta, rate, id, dir, starter)
        s.diag["starter"] = starter
        s.diag["rate"] = rate.toString()
        try {
            val am = ctx.getSystemService(AudioManager::class.java)
            s.diag["amode"] = am?.mode?.toString() ?: "?"
        } catch (e: Exception) { s.diag["amode"] = "?" }
        grabFocus(s.ctx)
        var started: Seg? = null
        while (s.nextAr < AR_CANDIDATES.size) {
            val mode = AR_CANDIDATES[s.nextAr++]
            val seg = buildSeg(s, mode)
            if (seg != null) { started = seg; break }
            s.diag[mode] = "x"
        }
        if (started == null) {
            // AudioRecord stack refused everything: go straight to MediaRecorder.
            var ok = false
            while (s.nextMr < MR_CANDIDATES.size && !ok) {
                ok = startMr(s, MR_CANDIDATES[s.nextMr++])
            }
            if (!ok) { dropFocus(s.ctx); return "" }
            s.bestMode = s.mrMode
        } else {
            s.seg = started
            s.bestMode = started.mode
            s.lastMode = started.mode
            launchSeg(s, started)
        }
        cur = s
        s.watchdog = Thread { watchdogLoop(s) }.apply { isDaemon = true; start() }
        onStateChange?.invoke(true)
        return s.lastMode
    }

    /**
     * If the live session was started by someone else (the background
     * InCallService) and has captured nothing but silence so far, discard it
     * and start fresh from [starter]'s foreground context. Rationale: every
     * in-call session through v0.7.0 was service-started and read digital
     * zeros on all ten paths, while the out-of-call mic test — started from
     * the foreground Settings screen — worked perfectly. Android zeroes
     * microphone captures begun while an app is in the background; this is
     * the clean test of that last variable. No-ops when audio is flowing.
     */
    @Synchronized
    fun restartIfSilent(ctx: Context, starter: String): Boolean {
        val s = cur ?: return false
        if (s.hasAudio || s.starter == starter) return false
        val meta = s.meta
        cur = null
        s.running = false
        dropFocus(s.ctx)
        if (s.mr != null) stopMr(s, deleteFile = true)
        s.seg?.let { teardownSeg(it, deleteFiles = true) }
        s.seg = null
        onStateChange?.invoke(false)
        start(ctx, meta, starter)
        cur?.diag?.put("restarted", "1")
        return true
    }

    private fun watchdogLoop(s: Session) {
        while (s.running && System.currentTimeMillis() - s.startMs < 90_000) {
            try { Thread.sleep(1600) } catch (e: Exception) { return }
            if (!s.running) return
            val keep = synchronized(this) { watchdogTick(s) }
            if (!keep) return
        }
    }

    /** One watchdog decision. Returns false when watching is done (audio is
     *  flowing, or every path has been tried). Called under the Recorder lock. */
    private fun watchdogTick(s: Session): Boolean {
        if (!s.running || cur !== s) return false

        // --- MediaRecorder engine active ---
        val mr = s.mr
        if (mr != null) {
            try {
                val amp = mr.maxAmplitude
                if (amp > s.mrPeak) s.mrPeak = amp
            } catch (e: Exception) { }
            s.diag[s.mrMode] = s.mrPeak.toString()
            if (s.mrPeak >= MR_SILENCE_AMP) { s.hasAudio = true; return false } // real audio — lock on
            if (System.currentTimeMillis() - s.mrStartedAt < 3200) return true
            s.mrSilentChecks++
            if (s.mrSilentChecks < 2) return true
            if (s.nextMr >= MR_CANDIDATES.size) return false // tried everything
            stopMr(s, deleteFile = true)
            var ok = false
            while (s.nextMr < MR_CANDIDATES.size && !ok) {
                ok = startMr(s, MR_CANDIDATES[s.nextMr++])
            }
            return ok
        }

        // --- AudioRecord engine active ---
        val seg = s.seg ?: return false
        val segRms = seg.bestRms()
        s.diag[seg.mode] = segRms.toInt().toString()
        if (segRms > s.bestRms) { s.bestRms = segRms; s.bestMode = seg.mode }
        if (segRms >= SILENCE_RMS) { s.hasAudio = true; return false } // real audio — lock on
        if (System.currentTimeMillis() - seg.startedAt < 3200) return true
        // Require two consecutive silent checks (~5s of digital silence) so a
        // quiet greeting gap never hops us off a working path.
        seg.silentChecks++
        if (seg.silentChecks < 2) return true

        // Silent segment: hop if anything is left to try.
        val arLeft = s.nextAr < AR_CANDIDATES.size
        if (!arLeft && s.nextMr >= MR_CANDIDATES.size) return false
        teardownSeg(seg, deleteFiles = true)
        s.seg = null
        if (arLeft) {
            var next: Seg? = null
            while (s.nextAr < AR_CANDIDATES.size && next == null) {
                val mode = AR_CANDIDATES[s.nextAr++]
                next = buildSeg(s, mode)
                if (next == null) s.diag[mode] = "x"
            }
            if (next != null) {
                s.seg = next
                s.lastMode = next.mode
                launchSeg(s, next)
                return true
            }
        }
        // AudioRecord stack exhausted: switch engines.
        var ok = false
        while (s.nextMr < MR_CANDIDATES.size && !ok) {
            ok = startMr(s, MR_CANDIDATES[s.nextMr++])
        }
        return ok
    }

    @Synchronized
    fun stop(ctx: Context): Recording? {
        val s = cur ?: return null
        cur = null
        s.running = false
        dropFocus(s.ctx)
        try { s.watchdog?.join(500) } catch (e: Exception) { }

        val durSec = (System.currentTimeMillis() - s.startMs) / 1000
        val diagStr = s.diag.entries.joinToString(";") { "${it.key}=${it.value}" }

        // --- MediaRecorder finish ---
        if (s.mr != null) {
            val mode = s.mrMode
            val peak = s.mrPeak
            stopMr(s, deleteFile = false)
            val f = s.mrFile
            val silent = peak < MR_SILENCE_AMP
            if (f != null && f.exists() && f.length() > 1000) {
                val rec = Recording(
                    id = s.id, file = f,
                    number = s.meta.number, contactName = s.meta.name,
                    direction = s.meta.direction, startMs = s.startMs,
                    durationSec = durSec, simLabel = s.meta.simLabel,
                    source = mode, callerSilent = mode == "mr-mic" && !silent,
                    silent = silent, diag = diagStr
                )
                rec.save(ctx)
                onStateChange?.invoke(false)
                return rec
            }
            try { f?.delete() } catch (e: Exception) { }
            onStateChange?.invoke(false)
            return null
        }

        // --- AudioRecord finish ---
        val seg = s.seg
        if (seg == null) {
            onStateChange?.invoke(false)
            return null
        }
        teardownSeg(seg, deleteFiles = false)
        if (seg.bestRms() > s.bestRms) { s.bestRms = seg.bestRms(); s.bestMode = seg.mode }
        s.diag[seg.mode] = seg.bestRms().toInt().toString()
        val diagFinal = s.diag.entries.joinToString(";") { "${it.key}=${it.value}" }

        val channels = if (seg.mode == "dual" || seg.mode == "voicecall-stereo") 2 else 1
        val raw: ByteArray = when {
            seg.mode == "dual" -> interleave(seg.files[0], seg.files[1])
            else -> if (seg.files[0].exists()) seg.files[0].readBytes() else ByteArray(0)
        }
        seg.files.forEach { try { it.delete() } catch (e: Exception) { } }

        val rms0 = seg.rms(0)
        val rms1 = seg.rms(1)
        val silent = maxOf(rms0, rms1) < 25.0
        val data = applyGain(raw, silent)
        val outFile = File(s.dir, s.id + ".wav")
        writeWav(outFile, data, s.rate, channels)

        val callerSilent = when (seg.mode) {
            "dual", "voicecall-stereo" -> rms0 < 40 && rms1 >= 40
            "mic", "voice-recognition", "unprocessed" -> !silent
            else -> false
        }
        val rec = Recording(
            id = s.id, file = outFile,
            number = s.meta.number, contactName = s.meta.name,
            direction = s.meta.direction, startMs = s.startMs,
            durationSec = durSec, simLabel = s.meta.simLabel,
            source = if (silent) s.lastMode else s.bestMode,
            callerSilent = callerSilent, silent = silent, diag = diagFinal
        )
        if (outFile.exists() && outFile.length() > 44) {
            rec.save(ctx)
            onStateChange?.invoke(false)
            return rec
        }
        outFile.delete()
        onStateChange?.invoke(false)
        return null
    }

    // ---------- WAV helpers ----------

    private fun applyGain(data: ByteArray, silent: Boolean): ByteArray {
        if (silent || data.size < 2) return data
        var peak = 0
        var i = 0
        while (i + 1 < data.size) {
            val v = (data[i].toInt() and 0xff) or (data[i + 1].toInt() shl 8)
            val a = if (v < 0) -v else v
            if (a > peak) peak = a
            i += 2
        }
        if (peak == 0 || peak >= 5000) return data
        val factor = minOf(0.89 * 32767.0 / peak, 16.0)
        if (factor <= 1.05) return data
        val out = ByteArray(data.size)
        i = 0
        while (i + 1 < data.size) {
            val v = (data[i].toInt() and 0xff) or (data[i + 1].toInt() shl 8)
            var nv = (v * factor).toInt()
            if (nv > 32767) nv = 32767
            if (nv < -32768) nv = -32768
            out[i] = (nv and 0xff).toByte()
            out[i + 1] = (nv shr 8 and 0xff).toByte()
            i += 2
        }
        return out
    }

    private fun interleave(left: File, right: File): ByteArray {
        val lBytes = if (left.exists()) left.readBytes() else ByteArray(0)
        val rBytes = if (right.exists()) right.readBytes() else ByteArray(0)
        val frames = minOf(lBytes.size, rBytes.size) / 2
        val data = ByteArray(frames * 4)
        for (i in 0 until frames) {
            data[i * 4] = lBytes[i * 2]; data[i * 4 + 1] = lBytes[i * 2 + 1]
            data[i * 4 + 2] = rBytes[i * 2]; data[i * 4 + 3] = rBytes[i * 2 + 1]
        }
        return data
    }

    private fun writeWav(out: File, data: ByteArray, rate: Int, channels: Int) {
        val raf = RandomAccessFile(out, "rw")
        val byteRate = rate * channels * 2
        val header = ByteArray(44)
        fun putInt(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte(); header[off + 1] = (v shr 8 and 0xff).toByte()
            header[off + 2] = (v shr 16 and 0xff).toByte(); header[off + 3] = (v shr 24 and 0xff).toByte()
        }
        fun putShort(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte(); header[off + 1] = (v shr 8 and 0xff).toByte()
        }
        "RIFF".toByteArray().copyInto(header, 0)
        putInt(4, 36 + data.size)
        "WAVE".toByteArray().copyInto(header, 8)
        "fmt ".toByteArray().copyInto(header, 12)
        putInt(16, 16); putShort(20, 1); putShort(22, channels)
        putInt(24, rate); putInt(28, byteRate); putShort(32, channels * 2); putShort(34, 16)
        "data".toByteArray().copyInto(header, 36)
        putInt(40, data.size)
        raf.write(header)
        raf.write(data)
        raf.close()
    }
}
