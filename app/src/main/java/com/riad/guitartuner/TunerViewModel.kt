package com.riad.guitartuner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riad.guitartuner.audio.AudioCapture
import com.riad.guitartuner.dsp.YinPitchDetector
import com.riad.guitartuner.music.Note
import com.riad.guitartuner.music.NoteMath
import com.riad.guitartuner.music.Tuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TunerUiState(
    val tuning: Tuning = Tuning.ALL.first(),
    /** Corde verrouillée par l'utilisateur, ou null en mode automatique. */
    val lockedString: Int? = null,
    val a4: Int = 440,
    /** Corde actuellement visée (auto ou verrouillée). */
    val targetString: Int? = null,
    val targetNote: Note? = null,
    val frequency: Double? = null,
    val cents: Double? = null,
    val listening: Boolean = false,
    val error: String? = null,
) {
    val inTune: Boolean get() = cents?.let { kotlin.math.abs(it) <= IN_TUNE_CENTS } ?: false

    companion object {
        const val IN_TUNE_CENTS = 5.0
    }
}

class TunerViewModel : ViewModel() {
    private val capture = AudioCapture()
    private val detector = YinPitchDetector(capture.sampleRate, capture.windowSize)

    private val _state = MutableStateFlow(TunerUiState())
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    private var job: Job? = null
    private val recent = ArrayDeque<Double>()
    private var silentFrames = 0

    fun start() {
        if (job != null) return
        _state.update { it.copy(listening = true, error = null) }
        job = viewModelScope.launch {
            capture.frames()
                .map { detector.detect(it) }
                .flowOn(Dispatchers.Default)
                .catch { e -> _state.update { it.copy(listening = false, error = e.message) } }
                .collect { result ->
                    if (result == null || result.probability < 0.8) onSilence() else onPitch(result.frequency)
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(listening = false) }
    }

    fun selectTuning(tuning: Tuning) {
        _state.update { it.copy(tuning = tuning, lockedString = null, targetString = null, targetNote = null) }
        recent.clear()
    }

    /** Verrouille une corde ; un second appui sur la même corde repasse en automatique. */
    fun toggleString(index: Int) {
        _state.update { it.copy(lockedString = if (it.lockedString == index) null else index) }
    }

    fun changeA4(delta: Int) {
        _state.update { it.copy(a4 = (it.a4 + delta).coerceIn(415, 466)) }
    }

    private fun onSilence() {
        // On garde la dernière lecture affichée un court instant avant de l'effacer.
        if (++silentFrames > 25) {
            recent.clear()
            _state.update { it.copy(frequency = null, cents = null) }
        }
    }

    private fun onPitch(raw: Double) {
        silentFrames = 0
        // Filtre médian pour rejeter les sauts d'octave ponctuels.
        recent.addLast(raw)
        while (recent.size > 5) recent.removeFirst()
        val freq = recent.sorted()[recent.size / 2]

        _state.update { s ->
            val a4 = s.a4.toDouble()
            val (index, note) = when {
                s.tuning.isChromatic -> null to NoteMath.nearestNote(freq, a4)
                s.lockedString != null -> s.lockedString to s.tuning.strings[s.lockedString]
                else -> s.tuning.closestString(freq, a4).let { it to s.tuning.strings[it] }
            }
            s.copy(
                targetString = index,
                targetNote = note,
                frequency = freq,
                cents = NoteMath.cents(freq, note.frequency(a4)),
            )
        }
    }

    override fun onCleared() {
        stop()
    }
}
