package com.example.audio

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-performance 16-bit stereo WAV audio mix recorder.
 */
class WavAudioRecorder(private val context: Context) {

    private var currentFile: File? = null
    private var outputStream: FileOutputStream? = null
    private var totalPcmBytesWritten: Long = 0L
    private val sampleRate = 44100
    private val channels = 2
    private val bitsPerSample = 16

    @Volatile
    var isRecording: Boolean = false
        private set

    fun startRecording(): File? {
        if (isRecording) return currentFile

        try {
            val recordingsDir = File(context.getExternalFilesDir(null), "Recordings")
            if (!recordingsDir.exists()) {
                recordingsDir.mkdirs()
            }

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(recordingsDir, "DJ_IMAN_Mix_$timestamp.wav")
            val fos = FileOutputStream(file)

            // Write 44-byte dummy WAV header to be overwritten at stop
            writeWavHeader(fos, 0, 0, sampleRate, channels, bitsPerSample)

            currentFile = file
            outputStream = fos
            totalPcmBytesWritten = 0L
            isRecording = true
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            isRecording = false
            return null
        }
    }

    /**
     * Appends interleaved stereo 16-bit PCM bytes to the recording file.
     */
    fun writeSamples(byteArray: ByteArray, offset: Int, length: Int) {
        if (!isRecording) return
        try {
            outputStream?.write(byteArray, offset, length)
            totalPcmBytesWritten += length
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopRecording(): File? {
        if (!isRecording) return null
        isRecording = false

        try {
            outputStream?.flush()
            outputStream?.close()
            outputStream = null

            val file = currentFile ?: return null
            if (file.exists() && file.length() > 44) {
                // Update header with exact data sizes
                val raf = RandomAccessFile(file, "rw")
                val totalAudioLen = totalPcmBytesWritten
                val totalDataLen = totalAudioLen + 36

                raf.seek(4)
                raf.write((totalDataLen and 0xff).toInt())
                raf.write(((totalDataLen shr 8) and 0xff).toInt())
                raf.write(((totalDataLen shr 16) and 0xff).toInt())
                raf.write(((totalDataLen shr 24) and 0xff).toInt())

                raf.seek(40)
                raf.write((totalAudioLen and 0xff).toInt())
                raf.write(((totalAudioLen shr 8) and 0xff).toInt())
                raf.write(((totalAudioLen shr 16) and 0xff).toInt())
                raf.write(((totalAudioLen shr 24) and 0xff).toInt())

                raf.close()
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Int,
        channels: Int,
        bitsPerSample: Int
    ) {
        val byteRate = longSampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte() // RIFF/WAVE header
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // 4 bytes: size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte() // block align
        header[33] = 0
        header[34] = bitsPerSample.toByte() // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        out.write(header, 0, 44)
    }

    fun getSavedRecordings(): List<File> {
        val dir = File(context.getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) return emptyList()
        return dir.listFiles { file -> file.extension.equals("wav", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            ?.toList() ?: emptyList()
    }
}
