package com.riad.voyage.ui

import com.riad.voyage.tf
import com.riad.voyage.t
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.riad.voyage.alerts.AlertChecker
import com.riad.voyage.data.PriceAlert
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(
    alerts: List<PriceAlert>,
    demo: Boolean,
    checking: Boolean,
    onCheck: () -> Unit,
    onRemove: (String) -> Unit,
    onConfigure: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(t("Alertes de prix"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                t("L'application vérifie ces trajets environ toutes les 12 heures et vous envoie une notification si le prix baisse."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (demo) item { DemoBanner(onConfigure) }
        if (alerts.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        t("Aucun trajet suivi. Lancez une recherche de vol, puis touchez « 🔔 Suivre ce prix »."),
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        } else {
            item {
                Button(onClick = onCheck, enabled = !checking, modifier = Modifier.fillMaxWidth()) {
                    Text(if (checking) t("Vérification…") else t("Vérifier les prix maintenant"))
                }
            }
            items(alerts, key = { it.id }) { a -> AlertCard(a, onRemove) }
        }
    }
}

@Composable
private fun AlertCard(a: PriceAlert, onRemove: (String) -> Unit) {
    val expired = AlertChecker.isExpired(a)
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${a.originName} → ${a.destinationName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    buildString {
                        append(formatDateOrMonth(a.departure))
                        a.returnDate?.let { append(" – ").append(formatDateOrMonth(it)) }
                        if (a.directOnly) append(" · direct")
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    when {
                        expired -> t("Date passée")
                        a.lastPrice != null -> tf("Dernier prix : {0}", formatPrice(a.lastPrice, a.currency))
                        else -> t("Pas encore de prix")
                    },
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                if (a.lastChecked > 0) {
                    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, com.riad.voyage.I18n.lang.locale)
                    Text(
                        tf("Vérifié le {0}", df.format(Date(a.lastChecked))),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { onRemove(a.id) }) { Text(t("Supprimer")) }
        }
    }
}
