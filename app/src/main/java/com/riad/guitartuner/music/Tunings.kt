package com.riad.guitartuner.music

import kotlin.math.abs

/** Un accordage : liste des cordes, de la plus grave à la plus aiguë. */
data class Tuning(val name: String, val strings: List<Note>) {
    val isChromatic: Boolean get() = strings.isEmpty()

    /** Corde la plus proche (en demi-tons) de [frequency]. */
    fun closestString(frequency: Double, a4: Double): Int {
        val midi = NoteMath.midiOf(frequency, a4)
        return strings.indices.minBy { abs(strings[it].midi - midi) }
    }

    companion object {
        private fun notes(vararg midi: Int) = midi.map { Note(it) }

        val CHROMATIC = Tuning("Chromatique", emptyList())

        val ALL = listOf(
            Tuning("Standard (EADGBE)", notes(40, 45, 50, 55, 59, 64)),
            Tuning("Demi-ton plus bas", notes(39, 44, 49, 54, 58, 63)),
            Tuning("Drop D (DADGBE)", notes(38, 45, 50, 55, 59, 64)),
            Tuning("DADGAD", notes(38, 45, 50, 55, 57, 62)),
            Tuning("Open G (DGDGBD)", notes(38, 43, 50, 55, 59, 62)),
            Tuning("Open D (DADF♯AD)", notes(38, 45, 50, 54, 57, 62)),
            Tuning("Drop C (CGCFAD)", notes(36, 43, 48, 53, 57, 62)),
            CHROMATIC,
        )
    }
}
