package com.riad.voyage.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.ContextCompat
import com.riad.voyage.R
import com.riad.voyage.data.CountryInfo
import com.riad.voyage.data.DayWeather
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.PackItem
import com.riad.voyage.data.Practical
import com.riad.voyage.data.Trip
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.tk
import java.io.File

/** Carnet de voyage en PDF aux couleurs d'Envolée : couverture, aperçu, programme, infos pratiques, bagages. */
object TripPdf {
    data class Content(
        val trip: Trip,
        val weather: List<DayWeather>,
        val info: CountryInfo?,
        val packing: List<PackItem>,
        val budgetTotal: String?,
        val budgetPerPerson: String?,
    )

    fun write(context: Context, c: Content): File {
        val doc = PdfDocument()
        Pages(context, doc).apply {
            cover(c)
            glance(c)
            program(c)
            practical(c)
            packing(c)
            text(t("Sources : Open-Meteo, OpenStreetMap, Wikipédia, Wikivoyage. Prix et infos pratiques indicatifs, à vérifier avant le départ."),
                9f, GREY, gapBefore = 16f)
            finish()
        }
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, "Envolee-" + c.trip.city.replace(Regex("[^\\p{L}\\p{N}]+"), "-") + ".pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}

private const val W = 595            // A4 en points
private const val H = 842
private const val M = 40f            // marge
private const val NAVY = 0xFF173F94.toInt()
private const val NIGHT = 0xFF071A3D.toInt()
private const val CORAL = 0xFFFF7A59.toInt()
private const val INK = 0xFF1B1B1F.toInt()
private const val GREY = 0xFF6B7280.toInt()

/** Écriture page par page : un curseur vertical, nouvelle page quand la place manque. */
private class Pages(private val context: Context, private val doc: PdfDocument) {
    private var page: PdfDocument.Page? = null
    private lateinit var canvas: Canvas
    private var y = M
    private var number = 0

    private fun newPage() {
        finishPage()
        number++
        page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, number).create())
        canvas = page!!.canvas
        y = M
    }

    private fun finishPage() {
        val p = page ?: return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = GREY }
        canvas.drawText(t("Préparé avec Envolée") + " · " + number, M, H - 24f, paint)
        doc.finishPage(p)
        page = null
    }

    fun finish() = finishPage()

    private fun ensure(height: Float) { if (page == null || y + height > H - 50) newPage() }

    fun text(s: String, size: Float, color: Int = INK, bold: Boolean = false, indent: Float = 0f, gapBefore: Float = 0f, gapAfter: Float = 3f) {
        if (s.isBlank()) return
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val layout = StaticLayout.Builder.obtain(s, 0, s.length, paint, (W - 2 * M - indent).toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        ensure(gapBefore + layout.height)
        y += gapBefore
        canvas.save()
        canvas.translate(M + indent, y)
        layout.draw(canvas)
        canvas.restore()
        y += layout.height + gapAfter
    }

    private fun section(title: String) {
        ensure(60f)
        y += 14f
        text(title, 16f, NAVY, bold = true, gapAfter = 4f)
        canvas.drawRect(M, y, M + 48f, y + 3f, Paint().apply { color = CORAL })
        y += 12f
    }

    fun cover(c: TripPdf.Content) {
        newPage()
        val band = 330f
        canvas.drawRect(0f, 0f, W.toFloat(), band, Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, band, NAVY, NIGHT, Shader.TileMode.CLAMP)
        })
        ContextCompat.getDrawable(context, R.drawable.logo_mark)?.apply {
            setBounds(M.toInt(), 46, M.toInt() + 44, 90)
            draw(canvas)
        }
        val white = 0xFFFFFFFF.toInt()
        y = 52f
        text("envolée", 26f, white, bold = true, indent = 56f)
        y = 150f
        text(t(c.trip.city), 40f, white, bold = true)
        val trip = c.trip
        text(listOfNotNull(
            trip.start?.let { s -> formatDay(s) + (trip.end?.takeIf { it != s }?.let { " → " + formatDay(it) } ?: "") },
            trip.travellers?.let { tf("{0} voyageur(s)", it) },
        ).joinToString(" · "), 14f, white)
        trip.programTitle?.let { text(it, 12f, 0xFFCFD8F0.toInt(), gapBefore = 6f) }
        y = band + 24f
    }

    fun glance(c: TripPdf.Content) {
        section(t("En un coup d'œil"))
        if (c.weather.isNotEmpty()) {
            text(t("Météo prévue"), 11f, bold = true)
            c.weather.forEach { w -> text(formatDay(w.date) + " : " + w.label, 10f, indent = 8f, gapAfter = 1f) }
            y += 6f
        }
        c.trip.flights.forEach { f ->
            text("✈️ ${t(f.origin)} → ${t(f.destination)} · ${formatDateTime(f.departureAt)} · ${formatPrice(f.price, f.currency)}", 10f)
        }
        c.trip.places.filter { it.kind.startsWith("Hôtel") }.forEach { h -> text("🏨 ${h.name} · ${tk(h.kind)}", 10f) }
        c.budgetTotal?.let { total ->
            text("💰 " + t("Budget estimé") + " : " + total + (c.budgetPerPerson?.let { " · " + tf("{0} par personne", it) } ?: ""), 10f, bold = true, gapBefore = 4f)
        }
    }

    fun program(c: TripPdf.Content) {
        if (c.trip.program.isEmpty()) return
        section(t("Programme"))
        c.trip.program.forEach { day ->
            text(day.title + (day.weather?.let { " · $it" } ?: ""), 12f, NAVY, bold = true, gapBefore = 8f)
            day.steps.forEach { s ->
                text("${s.time}   ${s.emoji} ${s.place.name}", 11f, bold = true, gapBefore = 3f, gapAfter = 1f)
                text(listOf(t(s.moment), tk(s.place.kind), "⏱ " + ExperienceBuilder.formatDuration(s.durationMin)).filter { it.isNotBlank() }
                    .joinToString(" · "), 9f, GREY, indent = 44f, gapAfter = 1f)
                if (s.why.isNotBlank()) text(s.why, 9f, indent = 44f)
            }
        }
    }

    fun practical(c: TripPdf.Content) {
        val info = c.info ?: return
        section(t("Infos pratiques"))
        text(t("Monnaie") + " : " + info.currency, 10f)
        text(tf("Prises {0} · {1} V", info.plugs, info.volts) + if (info.needsAdapter) " · " + t("Adaptateur de prise") else "", 10f)
        text(t("Pourboire") + " : " + t(info.tipping.label), 10f)
        text(t("Numéros d'urgence") + " : " + listOf(t("Urgences") to info.general, t("Police") to info.police, t("Ambulance") to info.ambulance,
            t("Pompiers") to info.fire).distinct().joinToString(" · ") { "${it.first} ${it.second}" }, 10f, bold = true)
        val phrases = Practical.phrases(info.lang)
        if (phrases.isNotEmpty()) {
            text(t("Phrases utiles"), 11f, bold = true, gapBefore = 6f)
            phrases.forEach { p -> text(t(p.meaning) + " : " + p.local, 10f, indent = 8f, gapAfter = 1f) }
        }
    }

    fun packing(c: TripPdf.Content) {
        val all = c.packing.map { t(it.key) + (it.count?.let { n -> " × $n" } ?: "") to it.key } + c.trip.extraItems.map { it to it }
        if (all.isEmpty()) return
        section(t("Bagages"))
        all.forEach { (label, key) -> text((if (key in c.trip.packed) "☑ " else "☐ ") + label, 10f, gapAfter = 1f) }
    }
}
