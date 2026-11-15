package com.riad.voyage.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Sections des Réglages, dans l'ordre d'usage. */
private enum class SettingsSection(val icon: String, val title: String) {
    GENERAL("⚙️", "Général"), AI("✨", "Envolée AI"), FLIGHTS("✈️", "Vols"), HOTELS("🏨", "Hôtels"),
    PLACES("🍽️", "Restaurants et lieux"), ACTIVITIES("🎟️", "Activités et billets"), DATA("🔒", "Données et confidentialité"),
}

/**
 * Réglages : une page de sections avec l'état de chacune ; une section touchée s'ouvre.
 * Enregistrement automatique en quittant une section (plus de grand bouton « Enregistrer »).
 */
@Composable
fun SettingsScreen(
    settings: Settings,
    keyStatus: KeyStatus,
    onSave: (Settings) -> Unit,
    quotas: QuotaStore,
    onQuotaLimit: (RatingSource, Int) -> Unit,
    onLanguage: (com.riad.voyage.Lang?) -> Unit,
    onDiagnostic: (() -> java.io.File)? = null,
    onClearHistory: () -> Unit = {},
    usePrefs: Boolean = false,
    onUsePrefs: (Boolean) -> Unit = {},
    onGeminiModels: (Settings) -> Unit = {},
) {
    val context = LocalContext.current
    var token by remember(settings) { mutableStateOf(settings.token) }
    var marker by remember(settings) { mutableStateOf(settings.marker) }
    var currency by remember(settings) { mutableStateOf(settings.currency) }
    var showToken by remember { mutableStateOf(false) }
    var googleKey by remember(settings) { mutableStateOf(settings.googleKey) }
    var tripadvisorKey by remember(settings) { mutableStateOf(settings.tripadvisorKey) }
    var foursquareKey by remember(settings) { mutableStateOf(settings.foursquareKey) }
    var liteApiKey by remember(settings) { mutableStateOf(settings.liteApiKey) }
    var tiqetsPartner by remember(settings) { mutableStateOf(settings.tiqetsPartner) }
    var claudeKey by remember(settings) { mutableStateOf(settings.claudeKey) }
    var geminiKey by remember(settings) { mutableStateOf(settings.geminiKey) }
    var aiOff by remember(settings) { mutableStateOf(settings.aiOff) }
    var claudeOn by remember(settings) { mutableStateOf(settings.claudeOn) }
    var claudeFreeText by remember(settings) { mutableStateOf(settings.claudeFreeText) }
    var claudeVerify by remember(settings) { mutableStateOf(settings.claudeVerify) }
    var claudeModel by remember(settings) { mutableStateOf(settings.claudeModel) }
    var geminiModel by remember(settings) { mutableStateOf(settings.geminiModel) }
    var section by rememberSaveable { mutableStateOf<String?>(null) }

    val edited = Settings(
        token = token.trim(), marker = marker.trim(), currency = currency,
        googleKey = googleKey.trim(), tripadvisorKey = tripadvisorKey.trim(), foursquareKey = foursquareKey.trim(),
        liteApiKey = liteApiKey.trim(), tiqetsPartner = tiqetsPartner.trim(),
        claudeKey = claudeKey.trim(), geminiKey = geminiKey.trim(), claudeOn = claudeOn, claudeFreeText = claudeFreeText,
        claudeVerify = claudeVerify, claudeModel = claudeModel, aiOff = aiOff,
        geminiModel = geminiModel, geminiModels = settings.geminiModels,
    )
    // Quitter une section (ou la page) enregistre ce qui a changé.
    fun close() {
        if (edited != settings) onSave(edited)
        section = null
    }
    androidx.activity.compose.BackHandler(enabled = section != null) { close() }
    // En quittant les Réglages (autre onglet) : dernières valeurs enregistrées aussi.
    val latest by rememberUpdatedState(edited)
    val saved by rememberUpdatedState(settings)
    DisposableEffect(Unit) { onDispose { if (latest != saved) onSave(latest) } }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val open = section?.let { SettingsSection.valueOf(it) }
        if (open == null) {
            Text(t("Réglages"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            val model = com.riad.voyage.data.ClaudeModel.of(claudeModel)
            SettingsSection.entries.forEach { sec ->
                val summary = when (sec) {
                    SettingsSection.GENERAL -> (com.riad.voyage.I18n.chosen?.nativeName ?: t("Automatique")) + " · " + currency
                    SettingsSection.AI -> if (!edited.ai) t("Désactivée") else listOfNotNull(t("Activée"),
                        if (model.gemini) (settings.geminiModels + com.riad.voyage.data.GeminiModel.defaults).firstOrNull { it.id == geminiModel }?.label ?: geminiModel else model.label,
                        t("recherches web").takeIf { claudeVerify }, tf("{0} fonctions", com.riad.voyage.data.AiFeature.entries.count { it.name !in aiOff })).joinToString(" · ")
                    SettingsSection.FLIGHTS -> when {
                        token.isBlank() -> t("Mode démo (prix fictifs)")
                        keyStatus is KeyStatus.Failed -> t("⚠ Clé à vérifier")
                        else -> t("✓ Prix réels")
                    }
                    SettingsSection.HOTELS -> when {
                        liteApiKey.isBlank() -> t("Non configuré")
                        LitePayment.isSandbox(liteApiKey.trim()) -> t("🧪 Clé de test")
                        else -> t("✅ Clé réelle")
                    }
                    SettingsSection.PLACES -> listOfNotNull(
                        tf("Google {0} / {1}", quotas.used(RatingSource.GOOGLE), quotas.limit(RatingSource.GOOGLE)).takeIf { googleKey.isNotBlank() },
                        "Tripadvisor".takeIf { tripadvisorKey.isNotBlank() }, "Foursquare".takeIf { foursquareKey.isNotBlank() },
                    ).joinToString(" · ").ifBlank { t("Non configuré") }
                    SettingsSection.ACTIVITIES -> if (tiqetsPartner.isBlank()) t("Non configuré") else "Tiqets ✓"
                    SettingsSection.DATA -> t("Historique, préférences, diagnostic")
                }
                OutlinedCard(onClick = { section = sec.name }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(sec.icon, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t(sec.title), fontWeight = FontWeight.Bold)
                            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("›", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            return@Column
        }

        TextButton(onClick = { close() }, contentPadding = PaddingValues(0.dp)) { Text("‹ " + t("Réglages")) }
        Text(open.icon + " " + t(open.title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        when (open) {
            SettingsSection.GENERAL -> {
                Text(t("Langue de l'application"), style = MaterialTheme.typography.titleMedium)
                LanguagePicker(onLanguage)
                Text(t("Devise des prix"), style = MaterialTheme.typography.titleMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(currencies) { c -> FilterChip(selected = currency == c, onClick = { currency = c }, label = { Text(c) }) }
                }
            }

            SettingsSection.AI -> {
                Hint(t("Envolée AI lit vos envies et vos billets, modifie le programme quand vous le demandez et le vérifie. Sans clé, l'app fonctionne comme avant."))
                // 1. Moteur et modèle ; 2. clé du moteur choisi seulement.
                Text(t("Moteur et modèle"), style = MaterialTheme.typography.titleMedium)
                com.riad.voyage.data.ClaudeModel.entries.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = claudeModel == m.id, onClick = { claudeModel = m.id })
                        Column(Modifier.weight(1f)) {
                            Text(m.label, fontWeight = FontWeight.Bold)
                            Text(t(m.strengths), style = MaterialTheme.typography.bodySmall)
                            Text(t(m.price), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val gemini = com.riad.voyage.data.ClaudeModel.of(claudeModel).gemini
                if (gemini) {
                    SecretField(t("Clé API Gemini (gratuite)"), geminiKey, { geminiKey = it },
                        t("Gratuite sur aistudio.google.com (compte Google)."), "https://aistudio.google.com/apikey")
                    // Modèles gratuits : liste d'office, ou ceux que Google ouvre à la clé (Pro exclus : pas gratuits).
                    Text(t("Modèle Gemini"), style = MaterialTheme.typography.titleMedium)
                    val known = com.riad.voyage.data.GeminiModel.defaults.associateBy { it.id }
                    (settings.geminiModels.map { known[it.id] ?: it } + com.riad.voyage.data.GeminiModel.defaults).distinctBy { it.id }.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = geminiModel == m.id, onClick = { geminiModel = m.id })
                            Column(Modifier.weight(1f)) {
                                Text(m.label, fontWeight = FontWeight.Bold)
                                Text(m.note.takeIf { it.isNotBlank() }?.let { t(it) } ?: m.id, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (geminiKey.isNotBlank()) TextButton(onClick = { onGeminiModels(edited) }, contentPadding = PaddingValues(0.dp)) {
                        Text(t("↻ Actualiser la liste depuis Google"))
                    }
                    Hint(t("Si le modèle choisi est surchargé, Envolée AI réessaie puis passe à Flash-Lite. Les modèles Pro ne sont pas proposés : ils ne sont pas gratuits."))
                }
                else SecretField(t("Clé API Anthropic"), claudeKey, { claudeKey = it },
                    t("Payée à l'usage sur console.anthropic.com."), "https://console.anthropic.com/settings/keys")
                if ((if (gemini) geminiKey else claudeKey).isNotBlank()) {
                    SwitchRow(t("Activer Envolée AI"), null, claudeOn, true) { claudeOn = it }
                    SwitchRow(t("Recherches sur le web"),
                        t("Vérifie les lieux inconnus, les dates (jours fériés, événements, fermetures), l'accessibilité et ajoute des conseils pratiques. ≈ 30 centimes par séjour complet avec Claude ; quota gratuit limité avec Gemini."),
                        claudeVerify, claudeOn) { claudeVerify = it }
                    if (claudeOn) {
                        val groups = listOf(
                            t("Comprendre et modifier") to listOf(com.riad.voyage.data.AiFeature.EDIT, com.riad.voyage.data.AiFeature.IMPORT, com.riad.voyage.data.AiFeature.QUESTIONS),
                            t("Vérifier") to listOf(com.riad.voyage.data.AiFeature.DATES, com.riad.voyage.data.AiFeature.ACCESS, com.riad.voyage.data.AiFeature.FOOD),
                            t("Rédiger") to listOf(com.riad.voyage.data.AiFeature.TIPS, com.riad.voyage.data.AiFeature.BUDGET, com.riad.voyage.data.AiFeature.STORY, com.riad.voyage.data.AiFeature.SOUVENIR),
                        )
                        Foldable(t("Comprendre et modifier") + " · " + t("Saisie libre des envies")) {
                            SwitchRow(t("Saisie libre des envies"), t("Décrivez le séjour souhaité en une phrase ; Envolée AI remplit le questionnaire."),
                                claudeFreeText, true) { claudeFreeText = it }
                            groups[0].second.forEach { f -> SwitchRow(t(f.label), t(f.description), f.name !in aiOff, true) { on -> aiOff = if (on) aiOff - f.name else aiOff + f.name } }
                        }
                        groups.drop(1).forEach { (title, features) ->
                            Foldable(title) {
                                features.forEach { f -> SwitchRow(t(f.label), t(f.description), f.name !in aiOff, true) { on -> aiOff = if (on) aiOff - f.name else aiOff + f.name } }
                            }
                        }
                    }
                }
            }

            SettingsSection.FLIGHTS -> {
                Hint(t("Avec une clé Travelpayouts, les prix des vols sont réels ; sans clé, l'app montre des prix fictifs."))
                OutlinedTextField(
                    value = token, onValueChange = { token = it },
                    label = { Text(t("Clé API Travelpayouts (token)")) }, singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) t("Masquer") else t("Afficher")) } },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = marker, onValueChange = { marker = it },
                    label = { Text(t("Identifiant partenaire (marker), facultatif")) },
                    supportingText = { Text(t("Permet de toucher une commission sur les réservations faites via l'app.")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                KeyStatusCard(settings, keyStatus)
                Foldable(t("Obtenir une clé gratuite")) {
                    Text(t("1. Créez un compte sur travelpayouts.com."), style = MaterialTheme.typography.bodyMedium)
                    Text(t("2. Rejoignez le programme « Aviasales »."), style = MaterialTheme.typography.bodyMedium)
                    Text(t("3. Dans votre profil, section API, copiez le « token » et collez-le ci-dessus. Le « marker » est votre identifiant partenaire, affiché au même endroit."),
                        style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { openUrl(context, "https://www.travelpayouts.com/") }) { Text(t("Ouvrir travelpayouts.com")) }
                }
            }

            SettingsSection.HOTELS -> {
                Hint(t("Avec une clé LiteAPI (gratuite sur liteapi.travel), l'onglet Hôtels affiche les prix réels pour vos dates. Sans clé, il propose les sites de réservation."))
                OutlinedTextField(
                    value = liteApiKey, onValueChange = { liteApiKey = it },
                    label = { Text(t("Clé LiteAPI")) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(),
                    // Type de clé, reconnu à son début (la clé elle-même reste masquée).
                    supportingText = {
                        val k = liteApiKey.trim()
                        when {
                            k.isEmpty() -> {}
                            LitePayment.isSandbox(k) -> Text(t("🧪 Clé de test : réservations et paiements fictifs."))
                            k.startsWith("prod") -> Text(t("✅ Clé réelle : réservations et paiements réels."), color = MaterialTheme.colorScheme.error)
                            else -> Text(t("Type de clé non reconnu : elle devrait commencer par « sand_ » ou « prod_ »."))
                        }
                    },
                )
                WhereToGet(t("Gratuite sur liteapi.travel (clé de test « sand_ », puis réelle « prod_ »)."), "https://www.liteapi.travel/")
            }

            SettingsSection.PLACES -> {
                Hint(t("L'app utilise Google tant que sa limite gratuite du mois n'est pas atteinte, puis Tripadvisor, puis Foursquare. Quand toutes sont épuisées, elle revient au guide Wikivoyage et à la carte."))
                RatingKeyField(RatingSource.GOOGLE, t("Clé Google Places API"), googleKey, { googleKey = it }, quotas, onQuotaLimit)
                WhereToGet(t("Google Cloud : activez « Places API (New) » ; un crédit gratuit mensuel couvre un usage personnel."), "https://console.cloud.google.com/apis/library/places.googleapis.com")
                RatingKeyField(RatingSource.TRIPADVISOR, t("Clé Tripadvisor Content API"), tripadvisorKey, { tripadvisorKey = it }, quotas, onQuotaLimit)
                WhereToGet(t("Tripadvisor : programme développeurs (quota gratuit mensuel)."), "https://www.tripadvisor.com/developers")
                RatingKeyField(RatingSource.FOURSQUARE, t("Clé Foursquare Places API"), foursquareKey, { foursquareKey = it }, quotas, onQuotaLimit)
                WhereToGet(t("Foursquare : compte développeur (quota gratuit mensuel)."), "https://foursquare.com/developers/")
            }

            SettingsSection.ACTIVITIES -> {
                OutlinedTextField(
                    value = tiqetsPartner, onValueChange = { tiqetsPartner = it },
                    label = { Text(t("Identifiant partenaire Tiqets (facultatif)")) },
                    supportingText = { Text(t("Ajouté aux liens de billets Tiqets pour toucher une commission.")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                WhereToGet(t("Programme d'affiliation Tiqets."), "https://www.tiqets.com/affiliate/")
            }

            SettingsSection.DATA -> {
                SwitchRow(t("Reprendre mes préférences"), t("Les nouvelles envolées partent de vos préférences habituelles (régime, budget, rythme…), sinon d'une page vierge."),
                    usePrefs, true, onUsePrefs)
                OutlinedButton(onClick = onClearHistory, modifier = Modifier.fillMaxWidth()) {
                    Text(t("Effacer l'historique et les préférences mémorisées"))
                }
                onDiagnostic?.let { report ->
                    OutlinedButton(onClick = {
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", report())
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                .putExtra(android.content.Intent.EXTRA_SUBJECT, t("Rapport de diagnostic Envolée"))
                                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            context.startActivity(android.content.Intent.createChooser(send, t("Envoyer le rapport")))
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(t("Envoyer un rapport de diagnostic (sans aucune clé)")) }
                }
                Hint(t("Les clés sont chiffrées et restent uniquement sur ce téléphone."))
            }
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Clé masquée, avec où l'obtenir. */
@Composable
private fun SecretField(label: String, value: String, onChange: (String) -> Unit, where: String, url: String) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    WhereToGet(where, url)
}

/** « Où l'obtenir ? » : une ligne et un lien, à côté de la clé concernée. */
@Composable
private fun WhereToGet(text: String, url: String) {
    val context = LocalContext.current
    TextButton(onClick = { openUrl(context, url) }, contentPadding = PaddingValues(0.dp)) {
        Text(t("Où l'obtenir ?") + " " + text, style = MaterialTheme.typography.bodySmall)
    }
}

/** Bloc repliable (aides, groupes de fonctions). */
@Composable
private fun Foldable(title: String, content: @Composable ColumnScope.() -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            TextButton(onClick = { open = !open }, contentPadding = PaddingValues(0.dp)) {
                Text((if (open) "▾ " else "▸ ") + title, fontWeight = FontWeight.Bold)
            }
            if (open) content()
        }
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

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
