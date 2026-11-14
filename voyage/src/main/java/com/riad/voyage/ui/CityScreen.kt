package com.riad.voyage.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riad.voyage.CitySection
import com.riad.voyage.I18n
import com.riad.voyage.data.CountryInfo
import com.riad.voyage.data.Net
import com.riad.voyage.data.Practical
import com.riad.voyage.t
import com.riad.voyage.tf
import java.util.Currency
import java.util.Locale

/** Page « Ville » : lieux à visiter, articles et infos pratiques de la destination. */
@Composable
fun CityScreen(
    section: CitySection,
    onSection: (CitySection) -> Unit,
    places: @Composable () -> Unit,
    articles: @Composable () -> Unit,
    info: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = section.ordinal) {
            CitySection.entries.forEach { s ->
                Tab(selected = s == section, onClick = { onSection(s) }, text = { Text(t(s.label), maxLines = 1) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (section) {
                CitySection.PLACES -> places()
                CitySection.ARTICLES -> articles()
                CitySection.INFO -> info()
            }
        }
    }
}

/** Infos pratiques du pays : monnaie et convertisseur, prises, pourboire, urgences, ambassade, phrases utiles. */
@Composable
fun PracticalInfo(
    city: String?,
    country: String,
    info: CountryInfo?,
    myCurrency: String,
    rates: suspend () -> Map<String, Double>,
) {
    val context = LocalContext.current
    val locale = I18n.lang.locale
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(t("Infos pratiques"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (city == null) {
                Text(t("Cherchez une ville dans « Lieux », ou créez un séjour, pour voir ses infos pratiques."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(listOf(t(city), t(country)).filter { it.isNotBlank() }.joinToString(", "), color = MaterialTheme.colorScheme.primary)
            }
        }
        if (city != null && info == null) {
            item { Text(t("Pas encore d'infos pratiques pour ce pays."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        item {
            InfoCard("💱 " + t("Monnaie et convertisseur")) {
                info?.let {
                    val name = runCatching { Currency.getInstance(it.currency).getDisplayName(locale) }.getOrDefault(it.currency)
                    Text("$name (${it.currency})", fontWeight = FontWeight.Bold)
                }
                CurrencyConverter(from = info?.currency ?: "EUR", to = myCurrency.uppercase(), rates = rates)
            }
        }
        if (info == null) return@LazyColumn
        item {
            InfoCard("🔌 " + t("Électricité")) {
                Text(tf("Prises {0} · {1} V", info.plugs, info.volts))
                Text(
                    if (info.needsAdapter) t("Adaptateur nécessaire pour les prises françaises.") else t("Prises françaises compatibles."),
                    color = if (info.needsAdapter) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        }
        item { InfoCard("💶 " + t("Pourboire")) { Text(t(info.tipping.label)) } }
        item {
            InfoCard("🚨 " + t("Numéros d'urgence")) {
                listOf(t("Urgences") to info.general, t("Police") to info.police, t("Ambulance") to info.ambulance, t("Pompiers") to info.fire)
                    .distinctBy { it.second + it.first }
                    .forEach { (label, number) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                runCatching { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }.padding(vertical = 4.dp),
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                            Text(number, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
            }
        }
        item {
            InfoCard("🏛️ " + t("Ambassade et consulat")) {
                val home = Locale("", Locale.getDefault().country.ifBlank { "FR" }).getDisplayCountry(locale)
                Text(tf("Coordonnées de l'ambassade ou du consulat de {0} sur place.", home), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    openUrl(context, "https://www.google.com/search?q=" + Net.enc("${t("ambassade")} $home ${t(country)}"))
                }, contentPadding = PaddingValues(0.dp)) { Text(t("Rechercher l'ambassade ›")) }
            }
        }
        item {
            val phrases = Practical.phrases(info.lang)
            val language = Locale(info.lang).getDisplayLanguage(locale).replaceFirstChar { it.uppercase() }
            InfoCard("🗣️ " + tf("Phrases utiles ({0})", language)) {
                phrases.forEach { p ->
                    Column(Modifier.padding(vertical = 3.dp)) {
                        Text(t(p.meaning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(p.local, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

/** Convertisseur rapide entre la monnaie locale et la vôtre (taux du jour, base euro). */
@Composable
fun CurrencyConverter(from: String, to: String, rates: suspend () -> Map<String, Double>) {
    var a by remember(from, to) { mutableStateOf(from) }
    var b by remember(from, to) { mutableStateOf(to) }
    var amount by remember { mutableStateOf("100") }
    val table by produceState<Map<String, Double>?>(null) { value = rates() }
    val value = amount.replace(',', '.').toDoubleOrNull()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
            label = { Text(a) }, singleLine = true, modifier = Modifier.weight(1f),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        TextButton(onClick = { val x = a; a = b; b = x }) { Text("⇄") }
        val result = table?.let { r ->
            val ra = if (a == "EUR") 1.0 else r[a]
            val rb = if (b == "EUR") 1.0 else r[b]
            if (value != null && ra != null && rb != null) value / ra * rb else null
        }
        Column(Modifier.weight(1f)) {
            Text(b, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    table == null -> "…"
                    result == null -> "—"
                    else -> String.format(I18n.lang.locale, "%,.2f", result)
                },
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
