package com.riad.voyage.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.random.Random

/** Données fictives, pour essayer l'application avant d'avoir une clé API. */
object DemoData {
    private val airlines = listOf("AF", "AH", "TO", "U2", "FR", "VY", "TK", "AT", "LH", "IB")

    fun flights(
        origin: String,
        destination: String,
        departure: LocalDate,
        returnDate: LocalDate?,
        flexibleMonth: Boolean,
        directOnly: Boolean,
        adults: Int,
    ): List<Flight> {
        val rnd = Random("$origin$destination$departure$returnDate".hashCode())
        val base = 60 + rnd.nextInt(140)
        return (0 until 12).mapNotNull { i ->
            val transfers = if (directOnly) 0 else listOf(0, 0, 1, 1, 2)[rnd.nextInt(5)]
            val day = if (flexibleMonth) {
                YearMonth.from(departure).atDay(1 + rnd.nextInt(YearMonth.from(departure).lengthOfMonth()))
            } else departure
            if (day.isBefore(LocalDate.now())) return@mapNotNull null
            val time = LocalTime.of(6 + rnd.nextInt(16), rnd.nextInt(12) * 5)
            val durTo = 90 + rnd.nextInt(90) + transfers * (80 + rnd.nextInt(120))
            val ret = returnDate?.let { r -> if (flexibleMonth) day.plusDays(java.time.temporal.ChronoUnit.DAYS.between(departure, r)) else r }
            val retTransfers = if (ret == null) 0 else if (directOnly) 0 else rnd.nextInt(2)
            val price = (base + i * (5 + rnd.nextInt(20)) - transfers * 15 + (if (ret != null) base / 2 else 0))
                .coerceAtLeast(29)
            val airline = airlines[rnd.nextInt(airlines.size)]
            Flight(
                origin = origin, destination = destination,
                originAirport = origin, destinationAirport = destination,
                price = price, airline = airline, flightNumber = (100 + rnd.nextInt(8900)).toString(),
                departureAt = OffsetDateTime.of(day, time, ZoneOffset.ofHours(1)).toString(),
                returnAt = ret?.let { OffsetDateTime.of(it, LocalTime.of(8 + rnd.nextInt(12), 0), ZoneOffset.ofHours(1)).toString() },
                transfers = transfers, returnTransfers = retTransfers,
                durationTo = durTo,
                durationBack = if (ret == null) 0 else durTo + rnd.nextInt(40) - 20,
                link = TravelpayoutsApi.searchUrl(origin, destination, day, ret, adults, ""),
            )
        }.sortedBy { it.price }
    }

    fun calendar(origin: String, destination: String, month: YearMonth): List<DayPrice> {
        val rnd = Random("$origin$destination$month".hashCode())
        val today = LocalDate.now()
        val base = 50 + rnd.nextInt(100)
        return (1..month.lengthOfMonth()).map { month.atDay(it) }
            .filter { !it.isBefore(today) }
            .map { d ->
                val weekend = d.dayOfWeek.value >= 5
                DayPrice(d, base + rnd.nextInt(80) + if (weekend) 30 else 0, rnd.nextInt(2), airlines[rnd.nextInt(airlines.size)])
            }
    }
}
