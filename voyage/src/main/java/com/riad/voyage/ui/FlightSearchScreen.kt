package com.riad.voyage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.FlightForm
import com.riad.voyage.data.Place
import com.riad.voyage.data.PopularPlaces
import com.riad.voyage.data.RecentSearch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val quickDestinations = listOf("ALG", "BCN", "IST", "RAK", "LON", "ROM", "LIS", "DXB", "TUN", "ORN", "PAR")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightSearchScreen(
    form: FlightForm,
    demo: Boolean,
    onUpdate: ((FlightForm) -> FlightForm) -> Unit,
    onSwap: () -> Unit,
    onSearch: () -> Unit,
    suggest: suspend (String) -> List<Place>,
    onConfigure: () -> Unit,
    recents: List<RecentSearch>,
    onRecent: (RecentSearch) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Trouver un vol", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        if (demo) DemoBanner(onConfigure)

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = form.roundTrip,
                onClick = { onUpdate { it.copy(roundTrip = true) } },
                shape = SegmentedButtonDefaults.itemShape(0, 2),
            ) { Text("Aller-retour") }
            SegmentedButton(
                selected = !form.roundTrip,
                onClick = { onUpdate { it.copy(roundTrip = false) } },
                shape = SegmentedButtonDefaults.itemShape(1, 2),
            ) { Text("Aller simple") }
        }

        // Départ et destination, avec le bouton d'inversion à cheval sur les deux champs.
        Box {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaceField("Départ", form.origin, { p -> onUpdate { it.copy(origin = p) } }, suggest, Modifier.padding(end = 56.dp))
                PlaceField("Destination", form.destination, { p -> onUpdate { it.copy(destination = p) } }, suggest, Modifier.padding(end = 56.dp))
            }
            FilledTonalIconButton(
                onClick = onSwap,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp),
            ) { Icon(AppIcons.Swap, contentDescription = "Inverser départ et destination") }
        }

        if (form.destination == null) {
            Text("Idées de destinations", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ideas = (recents.map { it.destination } + quickDestinations.mapNotNull { PopularPlaces.find(it) })
                    .distinctBy { it.code }
                    .filter { it.code != form.origin?.code }
                items(ideas) { p ->
                    AssistChip(onClick = { onUpdate { it.copy(destination = p) } }, label = { Text(p.name) })
                }
            }
        }

        if (form.roundTrip && !form.flexibleMonth) {
            DateRangeButton(form.departure, form.returnDate) { dep, ret -> onUpdate { it.copy(departure = dep, returnDate = ret) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton(
                    label = if (form.flexibleMonth) "Aller (mois)" else "Aller",
                    date = form.departure,
                    minDate = LocalDate.now(),
                    onPick = { d -> onUpdate { it.copy(departure = d) } },
                    modifier = Modifier.weight(1f),
                )
                if (form.roundTrip) {
                    DateButton(
                        label = "Retour (mois)",
                        date = form.returnDate,
                        minDate = form.departure,
                        onPick = { d -> onUpdate { it.copy(returnDate = d) } },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OutlinedCard(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Stepper("Passagers", form.adults, 1..9, { n -> onUpdate { it.copy(adults = n) } })
                SwitchRow(
                    title = "Dates flexibles",
                    subtitle = "Meilleur prix sur tout le mois",
                    checked = form.flexibleMonth,
                    onChange = { v -> onUpdate { it.copy(flexibleMonth = v) } },
                )
                SwitchRow(
                    title = "Vols directs uniquement",
                    subtitle = null,
                    checked = form.directOnly,
                    onChange = { v -> onUpdate { it.copy(directOnly = v) } },
                )
            }
        }

        Button(
            onClick = onSearch,
            enabled = form.origin != null && form.destination != null,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            Icon(AppIcons.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Rechercher les meilleurs prix", style = MaterialTheme.typography.titleMedium)
        }

        if (recents.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Icon(AppIcons.History, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Recherches récentes", style = MaterialTheme.typography.titleSmall)
            }
            recents.take(4).forEach { r ->
                Card(onClick = { onRecent(r) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.origin.name} → ${r.destination.name}", fontWeight = FontWeight.Medium)
                            Text(
                                buildString {
                                    append(formatDateOrMonth(r.departure))
                                    r.returnDate?.let { append(" – ").append(formatDateOrMonth(it)) }
                                    append(" · ").append(r.adults).append(" pass.")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        Text(
            "Les prix viennent des recherches récentes des voyageurs sur Aviasales. " +
                "Le prix final est confirmé sur le site du vendeur avant paiement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun LocalDate.utcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.utcDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Aller et retour choisis dans un seul calendrier. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeButton(departure: LocalDate, returnDate: LocalDate, onPick: (LocalDate, LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val nights = ChronoUnit.DAYS.between(departure, returnDate)
    OutlinedCard(onClick = { open = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(AppIcons.Calendar, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Aller – retour", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${formatDay(departure)}  →  ${formatDay(returnDate)}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            }
            Text("$nights nuit${if (nights > 1) "s" else ""}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
    if (open) {
        val today = LocalDate.now().utcMillis()
        val state = rememberDateRangePickerState(
            initialSelectedStartDateMillis = departure.utcMillis(),
            initialSelectedEndDateMillis = returnDate.utcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null && state.selectedEndDateMillis != null,
                    onClick = {
                        val start = state.selectedStartDateMillis
                        val end = state.selectedEndDateMillis
                        if (start != null && end != null) onPick(start.utcDate(), end.utcDate())
                        open = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
        ) {
            DateRangePicker(
                state = state,
                title = { Text("Choisissez l'aller puis le retour", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
