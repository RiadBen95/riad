package com.riad.voyage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.data.ActivityLinks

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivitiesScreen(
    city: String,
    category: ActivityLinks.Category,
    onCity: (String) -> Unit,
    onCategory: (ActivityLinks.Category) -> Unit,
) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Sorties", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Musées, attractions, visites et restaurants : réservez vos billets à l'avance, souvent moins cher et sans file d'attente.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = city,
            onValueChange = onCity,
            label = { Text("Ville") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ActivityLinks.Category.entries) { c ->
                FilterChip(
                    selected = c == category,
                    onClick = { onCategory(c) },
                    label = { Text("${c.emoji} ${c.label}") },
                )
            }
        }
        if (city.isBlank()) {
            Text("Indiquez d'abord une ville.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            ActivityLinks.partners(city, category).forEach { p ->
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
            "Astuce : les billets coupe-file des grands musées partent vite en haute saison, réservez quelques jours avant.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
