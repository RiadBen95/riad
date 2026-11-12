package com.riad.voyage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riad.voyage.ResultsState
import com.riad.voyage.Sort
import com.riad.voyage.data.Airlines
import com.riad.voyage.data.DayPrice
import com.riad.voyage.data.Flight
import com.riad.voyage.ui.theme.dealColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightResultsScreen(
    state: ResultsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSort: (Sort) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onBook: (Flight) -> Unit,
    onHotels: (Flight?) -> Unit,
    onFollow: () -> Unit,
    onOpenSite: () -> Unit,
    onConfigure: () -> Unit,
) {
    val q = state.query ?: return
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Modifier") }
            Column(Modifier.weight(1f)) {
                Text(
                    "${q.origin?.name} → ${q.destination?.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val dates = buildString {
                    append(formatDateOrMonth(q.departureParam))
                    q.returnParam?.let { append(" – ").append(formatDateOrMonth(it)) }
                    append(" · ").append(q.adults).append(if (q.adults > 1) " passagers" else " passager")
                }
                Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text("Recherche des meilleurs prix…", modifier = Modifier.padding(top = 12.dp))
                }
            }

            state.error != null -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.error, textAlign = TextAlign.Center)
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Réessayer") }
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.demo) item { DemoBanner(onConfigure) }
                if (state.calendar.isNotEmpty()) {
                    item {
                        PriceCalendar(
                            days = state.calendar,
                            selected = if (q.flexibleMonth) null else q.departure,
                            currency = state.currency,
                            onPick = onPickDay,
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onFollow, modifier = Modifier.weight(1f)) { Text("🔔 Suivre ce prix") }
                        OutlinedButton(onClick = { onHotels(null) }, modifier = Modifier.weight(1f)) { Text("🏨 Hôtels") }
                    }
                }
                if (state.flights.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Aucun prix récent trouvé pour ces dates", fontWeight = FontWeight.Bold)
                                Text(
                                    "Essayez « Dates flexibles », un autre jour du calendrier, ou lancez " +
                                        "la recherche complète en direct sur Aviasales.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = onOpenSite) { Text("Chercher en direct sur Aviasales") }
                            }
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${state.flights.size} offres",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Sort.entries.forEach { s ->
                                FilterChip(
                                    selected = state.sort == s,
                                    onClick = { onSort(s) },
                                    label = { Text(s.label) },
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                    val cheapest = state.flights.minOf { it.price }
                    items(state.sortedFlights) { f ->
                        FlightCard(f, q.adults, state.currency, f.price == cheapest, { onBook(f) }, { onHotels(f) })
                    }
                    item {
                        TextButton(onClick = onOpenSite, modifier = Modifier.fillMaxWidth()) {
                            Text("Voir toutes les offres en direct sur Aviasales ›")
                        }
                    }
                }
            }
        }
    }
}

private val dowFmt = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)
private val monFmt = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceCalendar(days: List<DayPrice>, selected: LocalDate?, currency: String, onPick: (LocalDate) -> Unit) {
    val min = days.minOf { it.price }
    val deal = dealColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Prix aller le plus bas par jour", style = MaterialTheme.typography.titleSmall)
        Text(
            "Touchez un jour pour le choisir. En vert : le moins cher du mois.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(days) { d ->
                val isSelected = d.date == selected
                val isCheapest = d.price == min
                Surface(
                    onClick = { onPick(d.date) },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isCheapest) BorderStroke(2.dp, deal) else null,
                ) {
                    Column(
                        Modifier
                            .width(72.dp)
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(d.date.format(dowFmt), style = MaterialTheme.typography.labelSmall)
                        Text("${d.date.dayOfMonth} ${d.date.format(monFmt)}", fontWeight = FontWeight.Bold)
                        Text(
                            formatPrice(d.price, currency),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isCheapest) deal else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isCheapest) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FlightCard(
    f: Flight,
    adults: Int,
    currency: String,
    cheapest: Boolean,
    onBook: () -> Unit,
    onHotels: () -> Unit,
) {
    val deal = dealColor()
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cheapest) {
                Text("★ Meilleur prix", color = deal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(Airlines.name(f.airline), style = MaterialTheme.typography.titleMedium)
                    if (f.flightNumber.isNotBlank()) {
                        Text(
                            "Vol ${f.airline} ${f.flightNumber} · ${f.originAirport} → ${f.destinationAirport}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatPrice(f.price * adults, currency),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (cheapest) deal else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (adults > 1) "${formatPrice(f.price, currency)} / pers." else "par personne",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Leg("Aller", f.departureAt, f.durationTo, f.transfers)
            f.returnAt?.let { Leg("Retour", it, f.durationBack, f.returnTransfers) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBook) { Text("Réserver") }
                TextButton(onClick = onHotels) { Text("Hôtels sur place") }
            }
        }
    }
}

@Composable
private fun Leg(label: String, at: String, duration: Int, transfers: Int) {
    Row {
        Text(label, modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelLarge)
        Column {
            Text(formatDateTime(at), style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(formatDuration(duration), formatStops(transfers)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (transfers == 0) dealColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
