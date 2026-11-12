package com.riad.voyage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.riad.voyage.data.Settings

private val currencies = listOf("EUR", "USD", "GBP", "CHF", "CAD", "DZD", "MAD", "TND")

@Composable
fun SettingsScreen(settings: Settings, onSave: (Settings) -> Unit) {
    val context = LocalContext.current
    var token by remember(settings) { mutableStateOf(settings.token) }
    var marker by remember(settings) { mutableStateOf(settings.marker) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var showToken by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text("Devise des prix", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(currencies) { c ->
                FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
            }
        }

        Text("Accès aux vrais prix", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Clé API Travelpayouts (token)") },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                androidx.compose.material3.TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) "Masquer" else "Afficher")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = marker,
            onValueChange = { marker = it },
            label = { Text("Identifiant partenaire (marker), facultatif") },
            supportingText = { Text("Permet de toucher une commission sur les réservations faites via l'app.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(Settings(token = token, marker = marker, currency = currency)) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Obtenir une clé gratuite", fontWeight = FontWeight.Bold)
                Text("1. Créez un compte sur travelpayouts.com.", style = MaterialTheme.typography.bodyMedium)
                Text("2. Rejoignez le programme « Aviasales ».", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "3. Dans votre profil, section API, copiez le « token » et collez-le ci-dessus. " +
                        "Le « marker » est votre identifiant partenaire, affiché au même endroit.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { openUrl(context, "https://www.travelpayouts.com/") }) {
                    Text("Ouvrir travelpayouts.com")
                }
            }
        }
        Text(
            "La clé est enregistrée uniquement sur ce téléphone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
