package com.riad.guitartuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Lit le micro en continu et émet une fenêtre glissante de [windowSize] échantillons
 * normalisés, renouvelée tous les [hopSize] échantillons.
 * L'appelant doit avoir obtenu la permission RECORD_AUDIO.
 */
class AudioCapture(
    val sampleRate: Int = 44_100,
    val windowSize: Int = 4096,
    private val hopSize: Int = 1024,
) {
    @SuppressLint("MissingPermission")
    fun frames(): Flow<FloatArray> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, windowSize * 2),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "Impossible d'initialiser le micro" }

        val window = FloatArray(windowSize)
        val hop = ShortArray(hopSize)
        try {
            record.startRecording()
            while (coroutineContext.isActive) {
                var read = 0
                while (read < hopSize) {
                    val n = record.read(hop, read, hopSize - read)
                    if (n < 0) error("Erreur de lecture du micro ($n)")
                    read += n
                }
                System.arraycopy(window, hopSize, window, 0, windowSize - hopSize)
                for (i in 0 until hopSize) window[windowSize - hopSize + i] = hop[i] / 32768f
                emit(window.copyOf())
            }
        } finally {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop()
            record.release()
        }
    }.flowOn(Dispatchers.Default)
}
