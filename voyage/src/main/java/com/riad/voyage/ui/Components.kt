package com.riad.voyage.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.data.Place
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Aucun navigateur installé", Toast.LENGTH_SHORT).show()
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
    placeholder: String = "Ville ou aéroport",
    onTextChange: ((String) -> Unit)? = null,
    initialText: String? = null,
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
                if (selected != null && !editing) {
                    Text(
                        selected.code,
                        modifier = Modifier.padding(end = 12.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            supportingText = if (editing && text.length >= 2 && suggestions.isEmpty()) {
                { Text("Recherche…") }
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
                        Text(
                            p.code,
                            modifier = Modifier.width(52.dp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Column {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                p.subtitle,
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
            dismissButton = { TextButton(onClick = { open = false }) { Text("Annuler") } },
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
            Text("Mode démo : prix fictifs", fontWeight = FontWeight.Bold)
            Text(
                "Ajoutez votre clé API Travelpayouts (gratuite) pour afficher les vrais prix.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onConfigure, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("Configurer la clé ›")
            }
        }
    }
}
