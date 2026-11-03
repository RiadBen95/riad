package com.riad.guitartuner

import com.riad.guitartuner.dsp.YinPitchDetector
import com.riad.guitartuner.music.Note
import com.riad.guitartuner.music.NoteMath
import com.riad.guitartuner.music.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class PitchDetectionTest {
    private val sampleRate = 44_100
    private val size = 4096
    private val detector = YinPitchDetector(sampleRate, size)

    /** Signal proche d'une corde pincée : fondamentale + harmoniques décroissantes + bruit. */
    private fun pluck(freq: Double, fundamental: Double = 1.0, noise: Double = 0.02): FloatArray {
        val rnd = Random(42)
        return FloatArray(size) { i ->
            val t = i.toDouble() / sampleRate
            var v = fundamental * sin(2 * PI * freq * t)
            for (h in 2..8) v += sin(2 * PI * freq * h * t + h) / h
            (0.3 * v * exp(-t * 2) + noise * (rnd.nextDouble() - 0.5)).toFloat()
        }
    }

    @Test
    fun detectsEveryStandardStringWithin1Cent() {
        for (note in Tuning.ALL.first().strings) {
            val f = note.frequency()
            val result = detector.detect(pluck(f))
            assertNotNull("${note.label} non détectée", result)
            val cents = NoteMath.cents(result!!.frequency, f)
            assert(abs(cents) < 1.0) { "${note.label}: écart de $cents cents" }
        }
    }

    @Test
    fun detectsWeakFundamentalWithoutOctaveError() {
        // Corde de Mi grave captée par un petit micro : fondamentale faible.
        val f = Note(40).frequency()
        val result = detector.detect(pluck(f, fundamental = 0.3))!!
        assertEquals(40, NoteMath.nearestNote(result.frequency).midi)
    }

    @Test
    fun detectsDetunedString() {
        val target = Note(45).frequency()
        val f = target * Math.pow(2.0, -12.0 / 1200) // La2 à -12 cents
        val cents = NoteMath.cents(detector.detect(pluck(f))!!.frequency, target)
        assertEquals(-12.0, cents, 1.0)
    }

    @Test
    fun silenceReturnsNull() {
        assertNull(detector.detect(FloatArray(size)))
    }

    @Test
    fun noteNames() {
        assertEquals("A4", Note(69).label)
        assertEquals("Mi", Note(40).solfege)
        assertEquals(82.41, Note(40).frequency(), 0.01)
        assertEquals(432.0, Note(69).frequency(432.0), 1e-9)
    }

    @Test
    fun closestStringPicksNearest() {
        val standard = Tuning.ALL.first()
        assertEquals(0, standard.closestString(80.0, 440.0))
        assertEquals(1, standard.closestString(112.0, 440.0))
        assertEquals(5, standard.closestString(335.0, 440.0))
    }
}
