package com.example.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.example.analysis.BeatAnalyzer
import com.example.model.PcmTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes audio files (MP3, AAC, WAV, M4A, FLAC) into in-memory 16-bit stereo PCM tracks
 * using Android's standard MediaCodec & MediaExtractor.
 */
class AudioDecoder(private val context: Context) {

    suspend fun decodeFromUri(
        uri: Uri,
        id: String,
        title: String,
        artist: String
    ): PcmTrack? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(context, uri, null)
            var audioTrackIndex = -1
            var inputFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    inputFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || inputFormat == null) {
                extractor.release()
                return@withContext null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: ""
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val pcmStream = ByteArrayOutputStream()
            val bufferInfo = MediaCodec.BufferInfo()
            var isEos = false
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channelCount = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val timeoutUs = 5000L

            while (!isEos) {
                val inIndex = codec.dequeueInputBuffer(timeoutUs)
                if (inIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEos = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outIndex = codec.dequeueOutputBuffer(bufferInfo, timeoutUs)
                while (outIndex >= 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null && bufferInfo.size > 0) {
                        outBuffer.position(bufferInfo.offset)
                        outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        val chunk = ByteArray(bufferInfo.size)
                        outBuffer.get(chunk)
                        pcmStream.write(chunk)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    outIndex = codec.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    break
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

            val rawBytes = pcmStream.toByteArray()
            if (rawBytes.isEmpty()) return@withContext null

            // Convert to 16-bit stereo PCM ShortArray
            val shortCount = rawBytes.size / 2
            val shortBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val pcmShorts = ShortArray(shortCount)
            shortBuffer.get(pcmShorts)

            // Resample / convert to standard stereo 44100Hz if needed
            val stereoPcm = if (channelCount == 1) {
                // Mono to stereo
                val stereo = ShortArray(pcmShorts.size * 2)
                for (i in pcmShorts.indices) {
                    stereo[i * 2] = pcmShorts[i]
                    stereo[i * 2 + 1] = pcmShorts[i]
                }
                stereo
            } else {
                pcmShorts
            }

            // Run beat analysis
            val analysis = BeatAnalyzer.analyze(stereoPcm, 2, sampleRate)

            val beatIntervalMs = (60.0 / analysis.bpm) * 1000.0
            val totalFrames = stereoPcm.size / 2L
            val durationMs = (totalFrames.toDouble() / sampleRate.toDouble()) * 1000.0
            val totalBeats = (durationMs / beatIntervalMs).toInt()
            val beatOffsets = DoubleArray(totalBeats) { analysis.firstBeatMs + (it * beatIntervalMs) }

            return@withContext PcmTrack(
                id = id,
                title = title,
                artist = artist,
                sampleRate = sampleRate,
                channels = 2,
                pcmSamples = stereoPcm,
                bpm = analysis.bpm,
                initialKey = analysis.key,
                waveformOverview = analysis.overviewWaveform,
                beatOffsetsMs = beatOffsets
            )
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                codec?.release()
                extractor.release()
            } catch (ex: Exception) {
                // ignore
            }
            return@withContext null
        }
    }
}
