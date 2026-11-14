package com.riad.voyage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class I18nTest {
    private val tsv = File("src/main/res/raw/translations.tsv").readText()

    @Test
    fun everyRowHasFrenchAndEnglish() {
        tsv.lines().filter { it.isNotBlank() && !it.startsWith("#") }.forEach { line ->
            val cols = line.split('\t')
            assertEquals("« ${cols[0]} »", 2, cols.size)
            assertTrue("« ${cols[0]} » a une traduction vide", cols.all { it.isNotBlank() })
            // Les valeurs {0}, {1}… doivent se retrouver dans chaque langue.
            Regex("\\{\\d}").findAll(cols[0]).forEach { ph -> cols.forEach { assertTrue("${cols[0]} → $it", it.contains(ph.value)) } }
        }
    }

    @Test
    fun translatesAndFormats() {
        try {
            I18n.loadForTest(tsv.lines().filterNot { it.startsWith("#") }.joinToString("\n"), Lang.EN)
            assertEquals("Flights", t("Vols"))
            assertEquals("Question 2 of 8", tf("Question {0} sur {1}", 2, 8))
            assertEquals("Restaurant · €€", tk("Restaurant · €€"))
            assertEquals("Texte inconnu", t("Texte inconnu"))
        } finally {
            I18n.loadForTest("", Lang.FR)
        }
        assertEquals("Vols", t("Vols"))
    }
}
