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
import com.riad.voyage.KeyStatus
import com.riad.voyage.data.Settings
import com.riad.voyage.ui.theme.dealColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment

private val currencies = listOf("EUR", "USD", "GBP", "CHF", "CAD", "DZD", "MAD", "TND")

@Composable
fun SettingsScreen(settings: Settings, keyStatus: KeyStatus, onSave: (Settings) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var token by remember(settings) { mutableStateOf(settings.token) }
    var marker by remember(settings) { mutableStateOf(settings.marker) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var showToken by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
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
        val edited = Settings(token = token.trim(), marker = marker.trim(), currency = currency)
        val unchanged = edited == settings
        Button(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                onSave(edited)
            },
            enabled = !unchanged,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (unchanged) "Enregistré ✓" else "Enregistrer") }

        KeyStatusCard(settings, keyStatus)

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

@Composable
private fun KeyStatusCard(settings: Settings, status: KeyStatus) {
    val deal = dealColor()
    val (color, text) = when {
        settings.isDemo -> MaterialTheme.colorScheme.onSurfaceVariant to "Aucune clé enregistrée : l'app est en mode démo (prix fictifs)."
        status is KeyStatus.Testing -> MaterialTheme.colorScheme.onSurfaceVariant to "Clé enregistrée. Vérification en cours…"
        status is KeyStatus.Ok -> deal to "✓ Clé enregistrée et valide : les vrais prix sont activés."
        status is KeyStatus.Failed -> MaterialTheme.colorScheme.error to "Clé enregistrée, mais le test a échoué : ${status.message}"
        else -> deal to "Clé enregistrée : les vrais prix sont activés."
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status is KeyStatus.Testing && !settings.isDemo) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}
