package com.riad.voyage.ui

import com.riad.voyage.data.parseDateTime
import com.riad.voyage.t
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

private val FR: Locale get() = com.riad.voyage.I18n.lang.locale

fun formatPrice(amount: Int, currency: String): String {
    val nf = NumberFormat.getCurrencyInstance(FR)
    runCatching { nf.currency = Currency.getInstance(currency.uppercase()) }
    nf.maximumFractionDigits = 0
    nf.minimumFractionDigits = 0
    return nf.format(amount)
}

fun formatDuration(minutes: Int): String {
    if (minutes <= 0) return ""
    val h = minutes / 60
    val m = minutes % 60
    return if (h == 0) "$m min" else if (m == 0) "$h h" else "$h h ${"%02d".format(m)}"
}

fun formatStops(n: Int): String = when (n) {
    0 -> t("Direct")
    1 -> t("1 escale")
    else -> "$n escales"
}

private val dayFmt get() = DateTimeFormatter.ofPattern("EEE d MMM", FR)
private val longDayFmt get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", FR)
private val monthFmt get() = DateTimeFormatter.ofPattern("MMMM yyyy", FR)
private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

fun formatDay(d: LocalDate): String = d.format(dayFmt)
fun formatLongDay(d: LocalDate): String = d.format(longDayFmt)
fun formatMonth(d: LocalDate): String = d.format(monthFmt)

fun formatDateTime(iso: String): String {
    val dt = parseDateTime(iso) ?: return iso.take(10)
    return "${dt.format(dayFmt)} · ${dt.format(timeFmt)}"
}

fun formatDateOrMonth(param: String): String = runCatching {
    if (param.length == 7) (if (com.riad.voyage.I18n.lang == com.riad.voyage.Lang.FR) "tout " else "") + formatMonth(LocalDate.parse("$param-01")) else formatDay(LocalDate.parse(param))
}.getOrDefault(param)
