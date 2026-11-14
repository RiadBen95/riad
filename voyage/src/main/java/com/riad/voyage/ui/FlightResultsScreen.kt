package com.riad.voyage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.riad.voyage.ResultsState
import com.riad.voyage.Sort
import com.riad.voyage.data.Airlines
import com.riad.voyage.data.DayPrice
import com.riad.voyage.data.Flight
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.ui.theme.dealColor
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightResultsScreen(
    state: ResultsState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSort: (Sort) -> Unit,
    onPickDay: (LocalDate) -> Unit,
    onBook: (Flight) -> Unit,
    onActivities: () -> Unit,
    onPlanStay: () -> Unit = {},
    onHotels: () -> Unit = {},
    onSaveFlight: (Flight) -> Unit = {},
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
            TextButton(onClick = onBack) { Text(t("← Modifier")) }
            Column(Modifier.weight(1f)) {
                Text(
                    "${q.origin?.name} → ${q.destination?.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val dates = buildString {
                    append(formatDateOrMonth(q.departureParam))
                    q.returnParam?.let { append(" – ").append(formatDateOrMonth(it)) }
                    append(" · ").append(tf("{0} passager(s)", q.adults))
                }
                Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(t("Recherche des meilleurs prix…"), modifier = Modifier.padding(top = 12.dp))
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
                Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(t("Réessayer")) }
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
                    // Enchaînement vers le séjour : programme personnalisé avec la ville et les dates du vol.
                    Button(onClick = onPlanStay, modifier = Modifier.fillMaxWidth()) {
                        Text(tf("✨ Planifier mon séjour à {0}", t(q.destination?.name ?: "")))
                    }
                    OutlinedButton(onClick = onHotels, modifier = Modifier.fillMaxWidth()) {
                        Text(tf("🏨 Hôtels à {0}", t(q.destination?.name ?: "")))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onFollow, modifier = Modifier.weight(1f)) { Text(t("🔔 Suivre ce prix")) }
                        OutlinedButton(onClick = onActivities, modifier = Modifier.weight(1f)) { Text(t("🎟️ Que faire sur place")) }
                    }
                    Text(t("Prix indicatifs vus récemment (Travelpayouts) : le prix final est confirmé sur le site de réservation."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp))
                }
                if (state.flights.isEmpty()) {
                    item {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(t("Aucun prix récent trouvé pour ces dates"), fontWeight = FontWeight.Bold)
                                Text(
                                    t("Essayez « Dates flexibles », un autre jour du calendrier, ou lancez la recherche complète en direct sur Aviasales."),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Button(onClick = onOpenSite) { Text(t("Chercher en direct sur Aviasales")) }
                            }
                        }
                    }
                } else {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                tf("{0} offres", state.flights.size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Sort.entries.forEach { s ->
                                FilterChip(
                                    selected = state.sort == s,
                                    onClick = { onSort(s) },
                                    label = { Text(t(s.label)) },
                                    modifier = Modifier.padding(start = 6.dp),
                                )
                            }
                        }
                    }
                    val cheapest = state.flights.minOf { it.price }
                    items(state.sortedFlights) { f ->
                        FlightCard(f, q.adults, state.currency, f.price == cheapest, { onBook(f) }, { onSaveFlight(f) })
                    }
                    item {
                        TextButton(onClick = onOpenSite, modifier = Modifier.fillMaxWidth()) {
                            Text(t("Voir toutes les offres en direct sur Aviasales ›"))
                        }
                    }
                }
            }
        }
    }
}

private val dowFmt get() = DateTimeFormatter.ofPattern("EEE", com.riad.voyage.I18n.lang.locale)
private val monFmt get() = DateTimeFormatter.ofPattern("MMM", com.riad.voyage.I18n.lang.locale)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceCalendar(days: List<DayPrice>, selected: LocalDate?, currency: String, onPick: (LocalDate) -> Unit) {
    val min = days.minOf { it.price }
    val deal = dealColor()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(t("Prix aller le plus bas par jour"), style = MaterialTheme.typography.titleSmall)
        Text(
            t("Touchez un jour pour le choisir. En vert : le moins cher du mois."),
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
    onSave: () -> Unit,
) {
    val deal = dealColor()
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cheapest) {
                Text(t("★ Meilleur prix"), color = deal, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Row(verticalAlignment = Alignment.Top) {
                if (f.airline.isNotBlank()) {
                    // Logos des compagnies fournis par le CDN d'Aviasales.
                    AsyncImage(
                        model = "https://pics.avs.io/96/96/${f.airline}.png",
                        contentDescription = Airlines.name(f.airline),
                        imageLoader = Images.get(LocalContext.current),
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White),
                    )
                    Spacer(Modifier.width(12.dp))
                }
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
                        if (adults > 1) tf("{0} / pers.", formatPrice(f.price, currency)) else t("par personne"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Leg("Aller", f.departureAt, f.durationTo, f.transfers)
            f.returnAt?.let { Leg("Retour", it, f.durationBack, f.returnTransfers) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onBook) { Text(t("Réserver")) }
                TextButton(onClick = onSave) { Text(t("♡ Garder ce vol")) }
            }
        }
    }
}

@Composable
private fun Leg(label: String, at: String, duration: Int, transfers: Int) {
    Row {
        Text(t(label), modifier = Modifier.width(60.dp), style = MaterialTheme.typography.labelLarge)
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
