package com.riad.voyage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.HotelForm
import com.riad.voyage.data.HotelLinks
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotelScreen(form: HotelForm, onUpdate: ((HotelForm) -> HotelForm) -> Unit) {
    val context = LocalContext.current
    val nights = ChronoUnit.DAYS.between(form.checkIn, form.checkOut)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Trouver un hôtel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Saisissez votre séjour une fois, puis comparez les prix sur chaque site en un geste.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = form.city,
            onValueChange = { v -> onUpdate { it.copy(city = v) } },
            label = { Text("Ville, quartier ou hôtel") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateButton("Arrivée", form.checkIn, LocalDate.now(), { d -> onUpdate { it.copy(checkIn = d) } }, Modifier.weight(1f))
            DateButton("Départ", form.checkOut, form.checkIn.plusDays(1), { d -> onUpdate { it.copy(checkOut = d) } }, Modifier.weight(1f))
        }
        Text(
            if (nights > 1) "$nights nuits" else "1 nuit",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Stepper("Adultes", form.adults, 1..16, { n -> onUpdate { it.copy(adults = n) } })
        Stepper("Chambres", form.rooms, 1..8, { n -> onUpdate { it.copy(rooms = n) } })

        Text("Comparer les prix chez", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        if (form.city.isBlank()) {
            Text("Indiquez d'abord une destination.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            HotelLinks.partners(form.city, form.checkIn, form.checkOut, form.adults, form.rooms).forEach { p ->
                Card(onClick = { openUrl(context, p.url) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(p.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Voir ›", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Text(
            "Astuce : ouvrez deux ou trois sites pour le même hôtel, les écarts de prix sont fréquents. " +
                "Vérifiez les conditions d'annulation avant de payer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
