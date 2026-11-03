package com.riad.guitartuner.dsp

import kotlin.math.sqrt

/** Résultat d'une détection de hauteur. */
data class PitchResult(val frequency: Double, val probability: Double, val rms: Double)

/**
 * Détecteur de fréquence fondamentale basé sur l'algorithme YIN
 * (de Cheveigné & Kawahara, 2002), limité à la plage utile d'une guitare.
 */
class YinPitchDetector(
    private val sampleRate: Int,
    private val bufferSize: Int,
    minFrequency: Double = 60.0,
    maxFrequency: Double = 1200.0,
    private val threshold: Double = 0.15,
    private val silenceRms: Double = 0.005,
) {
    private val tauMin = (sampleRate / maxFrequency).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minFrequency).toInt().coerceAtMost(bufferSize / 2)
    private val window = bufferSize - tauMax
    private val yin = DoubleArray(tauMax + 1)

    init {
        require(tauMax > tauMin) { "Tampon trop petit pour la fréquence minimale demandée" }
    }

    /** Analyse [samples] (valeurs normalisées dans [-1, 1]). Retourne null s'il n'y a pas de note. */
    fun detect(samples: FloatArray): PitchResult? {
        require(samples.size >= bufferSize)

        var energy = 0.0
        for (i in 0 until bufferSize) energy += samples[i] * samples[i]
        val rms = sqrt(energy / bufferSize)
        if (rms < silenceRms) return null

        // 1. Fonction de différence
        yin[0] = 1.0
        for (tau in 1..tauMax) {
            var sum = 0.0
            for (i in 0 until window) {
                val d = samples[i] - samples[i + tau]
                sum += d * d
            }
            yin[tau] = sum
        }

        // 2. Différence moyenne cumulée normalisée
        var running = 0.0
        for (tau in 1..tauMax) {
            running += yin[tau]
            yin[tau] = if (running == 0.0) 1.0 else yin[tau] * tau / running
        }

        // 3. Seuil absolu : premier minimum local sous le seuil
        var tau = tauMin
        var found = -1
        while (tau <= tauMax) {
            if (yin[tau] < threshold) {
                while (tau + 1 <= tauMax && yin[tau + 1] < yin[tau]) tau++
                found = tau
                break
            }
            tau++
        }
        if (found < 0) return null

        // 4. Interpolation parabolique pour une précision sub-échantillon
        val refined = if (found in 1 until tauMax) {
            val s0 = yin[found - 1]
            val s1 = yin[found]
            val s2 = yin[found + 1]
            val denom = 2 * (2 * s1 - s2 - s0)
            if (denom != 0.0) found + (s2 - s0) / denom else found.toDouble()
        } else {
            found.toDouble()
        }

        return PitchResult(sampleRate / refined, 1.0 - yin[found], rms)
    }
}
