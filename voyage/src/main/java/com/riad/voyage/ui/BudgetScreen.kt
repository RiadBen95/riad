package com.riad.voyage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.riad.voyage.BudgetState
import com.riad.voyage.data.BudgetCalculator
import com.riad.voyage.data.BudgetInput
import com.riad.voyage.data.BudgetItem
import com.riad.voyage.data.BudgetLine
import com.riad.voyage.data.Quiz
import com.riad.voyage.t
import com.riad.voyage.tf
import kotlin.math.roundToInt

/**
 * Budget du séjour : chaque poste est pré-rempli (vols gardés, hôtel, programme, estimations
 * selon le pays) et reste modifiable. Montants affichés dans la devise des réglages.
 */
@Composable
fun BudgetScreen(
    state: BudgetState,
    currency: String,
    onBack: () -> Unit,
    onInput: ((BudgetInput) -> BudgetInput) -> Unit,
    onOverride: (BudgetItem, Double?) -> Unit,
    onTarget: (Double?) -> Unit,
    onChoose: (BudgetItem) -> Unit = {},
) {
    // Conversion euro → devise affichée (euro si le taux n'est pas encore connu).
    val rate = if (currency.equals("EUR", true)) 1.0 else state.rates[currency.uppercase()]
    val shown = if (rate != null) currency.uppercase() else "EUR"
    val k = rate ?: 1.0
    fun money(eur: Double) = formatPrice((eur * k).roundToInt(), shown)

    var editing by remember { mutableStateOf<BudgetLine?>(null) }
    var editingTarget by remember { mutableStateOf(false) }

    val input = state.input
    val lines = state.lines
    val total = state.totalEur
    val people = input.travellers.coerceAtLeast(1)
    val days = BudgetCalculator.days(input.nights)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(t("← Retour")) }
            Text(t("Budget du séjour"), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                listOf(t(input.city), input.country.takeIf { it.isNotBlank() }?.let { t(it) }).filterNotNull().joinToString(", "),
                color = MaterialTheme.colorScheme.primary,
            )
        }

        // Total
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("Total estimé"), style = MaterialTheme.typography.labelLarge)
                    Text(money(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                    Text(
                        tf("{0} par personne", money(total / people)) + " · " + tf("{0} par jour", money(total / days)),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.targetEur?.let { target ->
                        val diff = target - total
                        Text(
                            if (diff >= 0) tf("Dans votre budget : il reste {0}", money(diff))
                            else tf("Au-dessus de votre budget de {0}", money(-diff)),
                            fontWeight = FontWeight.Bold,
                            color = if (diff >= 0) com.riad.voyage.ui.theme.dealColor() else MaterialTheme.colorScheme.error,
                        )
                    }
                    TextButton(onClick = { editingTarget = true }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (state.targetEur == null) t("Fixer un budget maximum ›") else t("Modifier le budget maximum ›"))
                    }
                }
            }
        }

        // Paramètres du séjour
        item {
            Stepper(t("Voyageurs"), input.travellers, 1..10, { n -> onInput { it.copy(travellers = n) } })
            Stepper(t("Nuits"), input.nights, 0..30, { n -> onInput { it.copy(nights = n) } })
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(t("Ajouter repas, transports et imprévus (estimation)"), modifier = Modifier.weight(1f))
                Switch(checked = input.includeDaily, onCheckedChange = { v -> onInput { it.copy(includeDaily = v) } })
            }
            if (input.includeDaily) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Quiz.Budget.entries.forEach { b ->
                        FilterChip(selected = input.level == b, onClick = { onInput { it.copy(level = b) } }, label = { Text(t(b.label)) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Quiz.Mobility.entries.forEach { m ->
                        FilterChip(selected = input.mobility == m, onClick = { onInput { it.copy(mobility = m) } }, label = { Text(t(m.label)) })
                    }
                }
            }
        }

        item { Text(t("Détail par poste"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(lines, key = { it.item.name }) { line ->
            BudgetRow(line, money(line.amountEur), if (total > 0) (line.amountEur / total).toFloat() else 0f, ::money) { editing = line }
        }

        // Ce qui n'est pas encore dans le voyage n'est pas compté : proposer de l'ajouter.
        val absent = listOf(BudgetItem.FLIGHT, BudgetItem.HOTEL, BudgetItem.ACTIVITIES).filter { item -> lines.none { it.item == item } }
        if (absent.isNotEmpty()) item {
            Text(t("Pas encore dans votre voyage (non compté) :"), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                absent.forEach { item ->
                    TextButton(onClick = { onChoose(item) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(when (item) {
                            BudgetItem.FLIGHT -> t("+ Vol")
                            BudgetItem.HOTEL -> t("+ Hôtel")
                            else -> t("+ Activités")
                        })
                    }
                }
            }
        }

        item {
            Text(
                t("Le budget ne compte que ce qui est dans votre voyage : vols gardés, hôtel choisi, activités. Les lieux en accès libre sont gratuits ; les restaurants comptent dans les repas. Touchez un poste pour saisir votre propre montant."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { line ->
        AmountDialog(
            title = t(line.item.label),
            current = line.amountEur * k,
            currency = shown,
            canReset = line.item in state.overrides,
            onDismiss = { editing = null },
            onSave = { v -> onOverride(line.item, v / k); editing = null },
            onReset = { onOverride(line.item, null); editing = null },
        )
    }
    if (editingTarget) {
        AmountDialog(
            title = t("Budget maximum"),
            current = state.targetEur?.let { it * k },
            currency = shown,
            canReset = state.targetEur != null,
            onDismiss = { editingTarget = false },
            onSave = { v -> onTarget(v / k); editingTarget = false },
            onReset = { onTarget(null); editingTarget = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetRow(line: BudgetLine, amount: String, share: Float, money: (Double) -> String, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line.item.emoji, style = MaterialTheme.typography.titleLarge)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(t(line.item.label), fontWeight = FontWeight.Bold)
                    Text(t(line.source), style = MaterialTheme.typography.bodySmall,
                        color = if (line.fromData) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(amount, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            line.details.take(12).forEach { d ->
                Row(Modifier.padding(start = 34.dp)) {
                    Text("• " + t(d.name), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(if (d.amountEur > 0) money(d.amountEur) else t("Gratuit"), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (line.details.size > 12) Text(tf("+ {0} autres", line.details.size - 12), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 34.dp))
            if (line.freeCount > 0) Text(tf("+ {0} lieu(x) en accès libre (places, ponts, jardins…)", line.freeCount),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 34.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))) {
                Box(Modifier.fillMaxWidth(share.coerceIn(0f, 1f)).height(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
private fun AmountDialog(
    title: String,
    current: Double?,
    currency: String,
    canReset: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onReset: () -> Unit,
) {
    var text by remember { mutableStateOf(current?.roundToInt()?.toString().orEmpty()) }
    val value = text.replace(',', '.').replace(" ", "").toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it.filter { c -> c.isDigit() || c == ',' || c == '.' } },
                label = { Text(tf("Montant ({0})", currency)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text(t("Enregistrer")) } },
        dismissButton = {
            Row {
                if (canReset) TextButton(onClick = onReset) { Text(t("Réinitialiser")) }
                Box(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text(t("Annuler")) }
            }
        },
    )
}
