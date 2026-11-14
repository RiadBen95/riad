package com.riad.voyage.ui

import Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.riad.voyage.KeyStatus
import com.riad.voyage.data.LitePayment
import com.riad.voyage.data.QuotaStore
import com.riad.voyage.data.RatingSource
import com.riad.voyage.data.Settings
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.ui.theme.dealColor

private val currencies = listOf("EUR", "USD", "GBP", "CHF", "CAD", "DZD", "MAD", "TND")

@Composable
fun SettingsScreen(
    settings: Settings,
    keyStatus: KeyStatus,
    onSave: (Settings) -> Unit,
    quotas: QuotaStore,
    onQuotaLimit: (RatingSource, Int) -> Unit,
    onLanguage: (com.riad.voyage.Lang?) -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    var token by remember(settings) { mutableStateOf(settings.token) }
    var marker by remember(settings) { mutableStateOf(settings.marker) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var showToken by remember { mutableStateOf(false) }
    var googleKey by remember(settings) { mutableStateOf(settings.googleKey) }
    var tripadvisorKey by remember(settings) { mutableStateOf(settings.tripadvisorKey) }
    var foursquareKey by remember(settings) { mutableStateOf(settings.foursquareKey) }
    var liteApiKey by remember(settings) { mutableStateOf(settings.liteApiKey) }
    var tiqetsPartner by remember(settings) { mutableStateOf(settings.tiqetsPartner) }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(t("Réglages"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text(t("Langue de l'application"), style = MaterialTheme.typography.titleMedium)
        LanguagePicker(onLanguage)

        Text(t("Devise des prix"), style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(currencies) { c ->
                FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) })
            }
        }

        Text(t("Accès aux vrais prix"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(t("Clé API Travelpayouts (token)")) },
            singleLine = true,
            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                androidx.compose.material3.TextButton(onClick = { showToken = !showToken }) {
                    Text(if (showToken) t("Masquer") else t("Afficher"))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = marker,
            onValueChange = { marker = it },
            label = { Text(t("Identifiant partenaire (marker), facultatif")) },
            supportingText = { Text(t("Permet de toucher une commission sur les réservations faites via l'app.")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(t("Notes des restaurants, cafés et bars (facultatif)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            t("L'app utilise Google tant que sa limite gratuite du mois n'est pas atteinte, puis Tripadvisor, puis Foursquare. Quand toutes sont épuisées, elle revient au guide Wikivoyage et à la carte."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RatingKeyField(RatingSource.GOOGLE, t("Clé Google Places API"), googleKey, { googleKey = it }, quotas, onQuotaLimit)
        RatingKeyField(RatingSource.TRIPADVISOR, t("Clé Tripadvisor Content API"), tripadvisorKey, { tripadvisorKey = it }, quotas, onQuotaLimit)
        RatingKeyField(RatingSource.FOURSQUARE, t("Clé Foursquare Places API"), foursquareKey, { foursquareKey = it }, quotas, onQuotaLimit)

        Text(t("Hôtels : prix en direct (facultatif)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        Text(
            t("Avec une clé LiteAPI (gratuite sur liteapi.travel), l'onglet Hôtels affiche les prix réels pour vos dates. Sans clé, il propose les sites de réservation."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = liteApiKey,
            onValueChange = { liteApiKey = it },
            label = { Text(t("Clé LiteAPI")) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            // Type de clé, reconnu à son début (la clé elle-même reste masquée).
            supportingText = {
                val k = liteApiKey.trim()
                when {
                    k.isEmpty() -> {}
                    LitePayment.isSandbox(k) -> Text(t("🧪 Clé de test : réservations et paiements fictifs."))
                    k.startsWith("prod") -> Text(t("✅ Clé réelle : réservations et paiements réels."),
                        color = MaterialTheme.colorScheme.error)
                    else -> Text(t("Type de clé non reconnu : elle devrait commencer par « sand_ » ou « prod_ »."))
                }
            },
        )

        OutlinedTextField(
            value = tiqetsPartner,
            onValueChange = { tiqetsPartner = it },
            label = { Text(t("Identifiant partenaire Tiqets (facultatif)")) },
            supportingText = { Text(t("Ajouté aux liens de billets Tiqets pour toucher une commission.")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val edited = Settings(
            token = token.trim(), marker = marker.trim(), currency = currency,
            googleKey = googleKey.trim(), tripadvisorKey = tripadvisorKey.trim(), foursquareKey = foursquareKey.trim(),
            liteApiKey = liteApiKey.trim(), tiqetsPartner = tiqetsPartner.trim(),
        )
        val unchanged = edited == settings
        Button(
            onClick = {
                focus.clearFocus()
                keyboard?.hide()
                onSave(edited)
            },
            enabled = !unchanged,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (unchanged) t("Enregistré ✓") else t("Enregistrer")) }

        KeyStatusCard(settings, keyStatus)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Obtenir une clé gratuite"), fontWeight = FontWeight.Bold)
                Text(t("1. Créez un compte sur travelpayouts.com."), style = MaterialTheme.typography.bodyMedium)
                Text(t("2. Rejoignez le programme « Aviasales »."), style = MaterialTheme.typography.bodyMedium)
                Text(
                    t("3. Dans votre profil, section API, copiez le « token » et collez-le ci-dessus. Le « marker » est votre identifiant partenaire, affiché au même endroit."),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { openUrl(context, "https://www.travelpayouts.com/") }) {
                    Text(t("Ouvrir travelpayouts.com"))
                }
            }
        }
        Text(
            t("La clé est enregistrée uniquement sur ce téléphone."),
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
        status is KeyStatus.Failed -> MaterialTheme.colorScheme.error to tf("Clé enregistrée, mais le test a échoué : {0}", status.message)
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

@Composable
private fun RatingKeyField(
    source: RatingSource,
    label: String,
    value: String,
    onChange: (String) -> Unit,
    quotas: QuotaStore,
    onQuotaLimit: (RatingSource, Int) -> Unit,
) {
    var limitText by remember { mutableStateOf(quotas.limit(source).toString()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(label) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    tf("Utilisé ce mois-ci : {0}", quotas.used(source)) +
                        if (quotas.exhaustedToday(source)) t(" · quota du fournisseur atteint aujourd'hui") else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { t ->
                        limitText = t.filter { it.isDigit() }.take(6)
                        limitText.toIntOrNull()?.let { onQuotaLimit(source, it) }
                    },
                    label = { Text(t("Limite / mois")) },
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                    ),
                    modifier = Modifier.width(130.dp),
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LanguagePicker(onLanguage: (com.riad.voyage.Lang?) -> Unit) {
    val current = com.riad.voyage.I18n.chosen
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilterChip(selected = current == null, onClick = { onLanguage(null) }, label = { Text(t("Automatique (langue du téléphone)")) })
        com.riad.voyage.Lang.entries.forEach { l ->
            FilterChip(selected = current == l, onClick = { onLanguage(l) }, label = { Text(l.nativeName) })
        }
    }
}
