package com.riad.voyage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.FlightForm
import com.riad.voyage.data.Place
import java.time.LocalDate

@Composable
fun FlightSearchScreen(
    form: FlightForm,
    demo: Boolean,
    onUpdate: ((FlightForm) -> FlightForm) -> Unit,
    onSwap: () -> Unit,
    onSearch: () -> Unit,
    suggest: suspend (String) -> List<Place>,
    onConfigure: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Trouver un vol", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Comparez les compagnies et trouvez le meilleur prix.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (demo) DemoBanner(onConfigure)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = form.roundTrip, onClick = { onUpdate { it.copy(roundTrip = true) } }, label = { Text("Aller-retour") })
            FilterChip(selected = !form.roundTrip, onClick = { onUpdate { it.copy(roundTrip = false) } }, label = { Text("Aller simple") })
        }

        PlaceField("Départ", form.origin, { p -> onUpdate { it.copy(origin = p) } }, suggest)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onSwap) { Text("⇅  Inverser") }
        }
        PlaceField("Destination", form.destination, { p -> onUpdate { it.copy(destination = p) } }, suggest)

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
                    label = if (form.flexibleMonth) "Retour (mois)" else "Retour",
                    date = form.returnDate,
                    minDate = form.departure,
                    onPick = { d -> onUpdate { it.copy(returnDate = d) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        SwitchRow(
            title = "Dates flexibles",
            subtitle = "Chercher le meilleur prix sur tout le mois",
            checked = form.flexibleMonth,
            onChange = { v -> onUpdate { it.copy(flexibleMonth = v) } },
        )
        SwitchRow(
            title = "Vols directs uniquement",
            subtitle = null,
            checked = form.directOnly,
            onChange = { v -> onUpdate { it.copy(directOnly = v) } },
        )
        Stepper("Passagers (adultes)", form.adults, 1..9, { n -> onUpdate { it.copy(adults = n) } })

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = onSearch,
            enabled = form.origin != null && form.destination != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) { Text("Rechercher les meilleurs prix", style = MaterialTheme.typography.titleMedium) }

        Text(
            "Les prix viennent des recherches récentes des voyageurs sur Aviasales. " +
                "Le prix final est confirmé sur le site du vendeur avant paiement.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
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
