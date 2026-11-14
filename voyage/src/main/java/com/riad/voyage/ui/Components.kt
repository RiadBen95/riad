package com.riad.voyage.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.data.Place
import com.riad.voyage.t
import com.riad.voyage.tf
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.delay

/**
 * Lien web sûr : seuls http(s) sont acceptés. Les liens viennent en partie de données ouvertes
 * (OpenStreetMap, Wikidata) : un autre type (intent:, file:, content:…) pourrait déclencher une
 * action inattendue dans une autre app. Sans schéma (« www.site.fr »), https est ajouté.
 */
fun safeWebUri(url: String): Uri? {
    val u = url.trim().let { if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(it)) it else "https://$it" }
    val uri = runCatching { Uri.parse(u) }.getOrNull() ?: return null
    return uri.takeIf { it.scheme?.lowercase() in setOf("http", "https") && !it.host.isNullOrBlank() }
}

fun openUrl(context: Context, url: String) {
    val uri = safeWebUri(url) ?: return Toast.makeText(context, t("Lien non pris en charge"), Toast.LENGTH_SHORT).show()
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, t("Aucun navigateur installé"), Toast.LENGTH_SHORT).show()
    }
}

/** Champ ville/aéroport avec suggestions au fil de la saisie. */
@Composable
fun PlaceField(
    label: String,
    selected: Place?,
    onSelect: (Place) -> Unit,
    suggest: suspend (String) -> List<Place>,
    modifier: Modifier = Modifier,
    placeholder: String = t("Ville ou aéroport"),
    onTextChange: ((String) -> Unit)? = null,
    initialText: String? = null,
    citiesOnly: Boolean = false,   // Sorties : pas de code ni de mention d'aéroport
) {
    var text by remember(selected) { mutableStateOf(initialText?.takeIf { selected == null } ?: selected?.label ?: "") }
    var editing by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<Place>>(emptyList()) }
    val focus = LocalFocusManager.current

    LaunchedEffect(text, editing) {
        if (!editing || text.trim().length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        suggestions = suggest(text)
    }

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; editing = true; onTextChange?.invoke(it) },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            trailingIcon = {
                if (selected != null && !editing && !citiesOnly) {
                    Text(
                        selected.code,
                        modifier = Modifier.padding(end = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            supportingText = if (editing && text.length >= 2 && suggestions.isEmpty()) {
                { Text(t("Recherche…")) }
            } else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (editing && suggestions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                suggestions.take(6).forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                editing = false
                                suggestions = emptyList()
                                text = p.label
                                focus.clearFocus()
                                onSelect(p)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!citiesOnly) {
                            Text(
                                p.code,
                                modifier = Modifier.width(52.dp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (citiesOnly) p.country.ifBlank { " " } else p.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LocalDate.toUtcMillis() = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun Long.toLocalDate() = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateButton(
    label: String,
    date: LocalDate,
    minDate: LocalDate,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatLongDay(date), style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (open) {
        val minMillis = minDate.toUtcMillis()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.toUtcMillis(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= minMillis
                override fun isSelectableYear(year: Int) = year >= minDate.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(it.toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(t("Annuler")) } },
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        FilledTonalButton(onClick = { onChange(value - 1) }, enabled = value > range.first) { Text("−") }
        Text(
            value.toString(),
            modifier = Modifier.width(40.dp),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        FilledTonalButton(onClick = { onChange(value + 1) }, enabled = value < range.last) { Text("+") }
    }
}

@Composable
fun DemoBanner(onConfigure: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Mode démo : prix fictifs"), fontWeight = FontWeight.Bold)
            Text(
                t("Ajoutez votre clé API Travelpayouts (gratuite) pour afficher les vrais prix."),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onConfigure, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(t("Configurer la clé ›"))
            }
        }
    }
}

/** Titre de section commun à tous les écrans (icône facultative). */
@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            androidx.compose.material3.Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/**
 * Météo des prochains jours pour une ville (une carte par jour). Avec [from]/[to], seuls les jours
 * du séjour sont affichés, ou un message s'ils sont au-delà des 16 jours de prévision.
 */
@Composable
fun WeatherStrip(
    city: String,
    load: suspend (String) -> List<com.riad.voyage.data.DayWeather>,
    from: LocalDate? = null,
    to: LocalDate? = null,
    modifier: Modifier = Modifier,
) {
    if (city.isBlank()) return
    val days by androidx.compose.runtime.produceState<List<com.riad.voyage.data.DayWeather>?>(null, city) { value = load(city) }
    val all = days ?: return
    if (all.isEmpty()) return
    val shown = if (from == null) all.take(7) else all.filter { !it.date.isBefore(from) && !it.date.isAfter(to ?: from) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(tf("Météo à {0}", t(city)) + " · " + t("prévisions Open-Meteo"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (shown.isEmpty()) {
            Text(t("Prévisions disponibles 16 jours avant le départ."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown.size) { i ->
                val w = shown[i]
                androidx.compose.material3.Card {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatDay(w.date), style = MaterialTheme.typography.labelSmall)
                        Text(w.emoji, style = MaterialTheme.typography.titleLarge)
                        Text("${w.tMax.toInt()}° / ${w.tMin.toInt()}°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (w.rainProb >= 30) Text("💧 ${w.rainProb} %", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Météo d'un voyage : les jours du séjour à venir (rien si le séjour est passé). */
@Composable
fun TripWeather(trip: com.riad.voyage.data.Trip, load: suspend (String) -> List<com.riad.voyage.data.DayWeather>, modifier: Modifier = Modifier) {
    val today = LocalDate.now()
    val start = trip.start
    val last = trip.end ?: start
    if (last != null && last.isBefore(today)) return
    WeatherStrip(trip.city, load, start?.coerceAtLeast(today), last, modifier)
}
