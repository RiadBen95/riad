package com.riad.guitartuner.music

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

private val ENGLISH = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
private val SOLFEGE = arrayOf("Do", "Do♯", "Ré", "Ré♯", "Mi", "Fa", "Fa♯", "Sol", "Sol♯", "La", "La♯", "Si")

/** Une note identifiée par son numéro MIDI (69 = La4). */
data class Note(val midi: Int) {
    val name: String get() = ENGLISH[midi.mod(12)]
    val solfege: String get() = SOLFEGE[midi.mod(12)]
    val octave: Int get() = midi / 12 - 1
    val label: String get() = "$name$octave"

    fun frequency(a4: Double = 440.0): Double = a4 * 2.0.pow((midi - 69) / 12.0)
}

object NoteMath {
    /** Numéro MIDI fractionnaire correspondant à [frequency]. */
    fun midiOf(frequency: Double, a4: Double = 440.0): Double = 69.0 + 12.0 * log2(frequency / a4)

    fun nearestNote(frequency: Double, a4: Double = 440.0): Note = Note(midiOf(frequency, a4).roundToInt())

    /** Écart en cents entre [frequency] et [target]. Positif = trop haut. */
    fun cents(frequency: Double, target: Double): Double = 1200.0 * log2(frequency / target)
}
