package com.example.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorder(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null

    companion object {
        private const val TAG = "AudioRecorder"
    }

    /**
     * Starts audio recording from the device's microphone.
     * Keeps recording inside the safe application internal files dir to bypass heavy permission flows.
     */
    fun startRecording(): File? {
        try {
            val audioDir = File(context.cacheDir, "recordings")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            audioFile = File(audioDir, "temp_voice_input.mp4")
            if (audioFile?.exists() == true) {
                audioFile?.delete()
            }

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(16000) // 16kHz sampling rate: perfect balance for speech & API bandwidth
                setAudioEncodingBitRate(32000)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            Log.d(TAG, "Recording started at: ${audioFile?.absolutePath}")
            return audioFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed starting recorder: ${e.message}")
            e.printStackTrace()
            return null
        }
    }

    /**
     * Stops the active audio recording.
     */
    fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Failed stopping mediaRecorder (might have stopped too quickly): ${e.message}")
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    /**
     * Gets current sound level mapped [0-32767]
     */
    fun getAmplitude(): Int {
        return try {
            mediaRecorder?.maxAmplitude ?: 0
        } catch (e: Exception) {
            0
        }
    }
}
