package com.riad.voyage.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riad.voyage.ExperienceState
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.ExperienceBuilder
import com.riad.voyage.data.hhmm
import com.riad.voyage.data.ExperienceDay
import com.riad.voyage.data.ExperienceStep
import com.riad.voyage.data.Place
import com.riad.voyage.data.PointOfInterest
import com.riad.voyage.data.Quiz
import com.riad.voyage.data.WikiSummary
import com.riad.voyage.t
import com.riad.voyage.tf
import com.riad.voyage.tk
import java.net.URLEncoder

@Composable
fun ExperienceScreen(
    state: ExperienceState,
    onAnswers: ((Quiz.Answers) -> Quiz.Answers) -> Unit,
    onStep: (Int) -> Unit,
    onGenerate: () -> Unit,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    suggestCities: suspend (String) -> List<Place>,
    onSave: () -> Unit = {},
    alternatives: (Int, Int) -> List<Pair<ActivityLinks.Category, PointOfInterest>> = { _, _ -> emptyList() },
    onReplace: (Int, Int, Pair<ActivityLinks.Category, PointOfInterest>?) -> Unit = { _, _, _ -> },
    onHotels: () -> Unit = {},
    onBudget: () -> Unit = {},
    wiki: suspend (String) -> WikiSummary? = { null },
    isFavorite: (String) -> Boolean = { false },
    onFavorite: (PointOfInterest) -> Unit = {},
    onReadArticle: (String) -> Unit = {},
    edits: ExperienceEdits = ExperienceEdits(),
) {
    when {
        state.recap && state.editStep == null -> RecapScreen(state.answers, edits)
        state.loading || state.describing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // Étapes faites ✓, en cours (roue), à venir ○ : on voit où en est la composition.
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(32.dp)) {
                Text(t("Création de votre envolée…"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val fromText = state.describing || state.aiSummary != null
                ExperienceState.STAGES.forEachIndexed { i, label ->
                    if (i == 0 && !fromText) return@forEachIndexed
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                            when {
                                i < state.stage -> Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                i == state.stage -> CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                else -> Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(t(label), color = if (i > state.stage) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            fontWeight = if (i == state.stage) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
        state.result != null && !state.recap -> ExperienceResult(state, onRegenerate, onEdit, onSave, alternatives, onReplace, onHotels, onBudget,
            wiki, isFavorite, onFavorite, onReadArticle, edits)
        else -> Questionnaire(state, onAnswers, onStep, onGenerate, suggestCities, edits)
    }
}

/** Modifications du programme depuis l'écran Expérience (ordre, ajout, réglages) et lien avec le voyage. */
class ExperienceEdits(
    val move: (Int, Int, Int) -> Unit = { _, _, _ -> },
    val add: (Int, String) -> Unit = { _, _ -> },
    val refine: ((Quiz.Answers) -> Quiz.Answers) -> Unit = {},
    val detailed: (Boolean) -> Unit = {},
    val linked: Boolean = false,
    val openTrip: () -> Unit = {},
    val describe: ((String) -> Unit)? = null,   // saisie libre avec Claude (null = désactivée)
    val setTime: (Int, Int, Int) -> Unit = { _, _, _ -> },   // nouvelle heure d'une étape (jour, étape, minutes)
    val newEnvolee: () -> Unit = {},
    val prefsSummary: String? = null,                        // préférences habituelles (null : aucune envolée passée)
    val usePrefs: Boolean = false,
    val setUsePrefs: (Boolean) -> Unit = {},
    val askChange: ((String) -> Unit)? = null,               // modifier en parlant (null = désactivé)
    val undo: () -> Unit = {},
    val recapStep: (String) -> Unit = {},
    val recapDone: () -> Unit = {},
    val recapApply: () -> Unit = {},
    val recapClose: () -> Unit = {},
    val answerQuestion: (String) -> Unit = {},   // réponse à la question de précision d'Envolée AI
    val dismissQuestion: () -> Unit = {},
    val importDocs: ((List<android.net.Uri>) -> Unit)? = null,   // partir de réservations déjà faites (null = désactivé)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Questionnaire(
    state: ExperienceState,
    onAnswers: ((Quiz.Answers) -> Quiz.Answers) -> Unit,
    onStep: (Int) -> Unit,
    onGenerate: () -> Unit,
    suggestCities: suspend (String) -> List<Place>,
    edits: ExperienceEdits,
) {
    val a = state.answers
    val single = state.editStep                 // question ouverte depuis « Ajuster mes réponses »
    val steps = single?.let { listOf(it) } ?: state.steps
    val step = if (single != null) 0 else state.step.coerceIn(0, steps.size - 1)
    val id = steps[step]
    val canContinue = when (id) {
        "city" -> a.city.isNotBlank()
        "interests" -> a.interests.isNotEmpty()
        else -> true
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (single == null) {
            Text(t("Votre envolée sur mesure"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                t("Quelques questions sur vos envies et vos goûts : nous composons un programme qui mêle visites, balades, restaurants et sorties."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tf("Question {0} sur {1}", step + 1, steps.size), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { edits.detailed(state.quick) }) {
                    Text(if (state.quick) t("Personnaliser en détail ›") else t("Version rapide ›"))
                }
            }
            }

            when {
                id == "city" -> {
                    // Discret : reprendre ses préférences habituelles, ou partir d'une page vierge (par défaut).
                    if (single == null) edits.prefsSummary?.let { summary ->
                        FilterChip(selected = edits.usePrefs, onClick = { edits.setUsePrefs(!edits.usePrefs) },
                            label = { Text("✨ " + t("Reprendre mes préférences") + " · " + summary, style = MaterialTheme.typography.labelMedium) })
                    }
                    if (single == null) edits.describe?.let { describe -> FreeTextCard(describe, state.aiQuestion, edits.answerQuestion, edits.dismissQuestion) }
                    if (single == null) edits.importDocs?.let { import ->
                        ImportDocumentsButton(t("🎫 Partir de mes réservations (billets, hôtel)"), Modifier.fillMaxWidth(), import)
                    }
                    QuestionTitle(t("Où voulez-vous vous envoler ?"))
                    PlaceField(
                        label = t("Ville"),
                        selected = a.place,
                        onSelect = { p -> onAnswers { it.copy(city = p.name, place = p) } },
                        suggest = suggestCities,
                        placeholder = t("Ex. Paris, Alger, Rome…"),
                        onTextChange = { t -> onAnswers { it.copy(city = t, place = null) } },
                        initialText = a.city,
                        citiesOnly = true,
                    )
                }
                id == "date" -> {
                    QuestionTitle(t("Quand commence votre séjour ?"))
                    DateButton(t("Premier jour"), a.startDate, java.time.LocalDate.now(), { d -> onAnswers { it.copy(startDate = d) } }, Modifier.fillMaxWidth())
                    Text(
                        t("La météo prévue est prise en compte jusqu'à 16 jours à l'avance : musées s'il pleut, parcs et plages au soleil, point de vue au coucher du soleil."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                id == "duration" -> {
                    QuestionTitle(t("Combien de temps avez-vous ?"))
                    // Version rapide : la date de début est demandée ici (pas de question à part).
                    if (state.quick) DateButton(t("Premier jour"), a.startDate, java.time.LocalDate.now(), { d -> onAnswers { it.copy(startDate = d) } }, Modifier.fillMaxWidth())
                    TimeField(t("Heure d'arrivée (facultatif, HH:MM)"), a.arrivalMin) { m -> onAnswers { it.copy(arrivalMin = m) } }
                    if (a.arrivalMin != null) Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = a.restAfterArrival, onCheckedChange = { v -> onAnswers { it.copy(restAfterArrival = v) } })
                        Text(t("Soirée libre à l'hôtel après l'arrivée"))
                    }
                    TimeField(t("Heure de départ le dernier jour (facultatif, HH:MM)"), a.departureMin) { m -> onAnswers { it.copy(departureMin = m) } }
                    val dep = a.departureMin
                    val arr = a.arrivalMin
                    if (a.duration.days == 1 && dep != null && arr != null && dep <= arr) Text(
                        t("Le départ est avant l'arrivée : vérifiez les heures."), color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    ChoiceCard(t("Une demi-journée"), null, a.duration.halfDay) { onAnswers { it.copy(duration = Quiz.Duration.HALF) } }
                    val end = a.startDate.plusDays((a.duration.days - 1).toLong())
                    ChoiceCard(
                        t("Une ou plusieurs journées"),
                        if (a.duration.halfDay) t("Le programme couvre tout votre séjour")
                        else tf("Du {0} au {1}", formatDay(a.startDate), formatDay(end)),
                        !a.duration.halfDay,
                    ) { if (a.duration.halfDay) onAnswers { it.copy(duration = Quiz.Duration.DAY) } }
                    if (!a.duration.halfDay) {
                        Stepper(t("Nombre de jours"), a.duration.days, 1..Quiz.Duration.MAX_DAYS, { n ->
                            onAnswers { it.copy(duration = Quiz.Duration(n)) }
                        })
                    }
                }
                id == "company" -> {
                    QuestionTitle(t("Avec qui partez-vous ?"))
                    Quiz.Company.entries.forEach { c ->
                        ChoiceCard("${c.emoji}  ${t(c.label)}", null, a.company == c) { onAnswers { it.copy(company = c) } }
                    }
                    if (a.company == Quiz.Company.FAMILY) {
                        CheckRow(t("Avec un enfant de moins de 6 ans (rythme doux, sieste)"), a.youngKids) { v -> onAnswers { it.copy(youngKids = v) } }
                        CheckRow(t("Avec une poussette"), a.stroller) { v -> onAnswers { it.copy(stroller = v) } }
                    }
                }
                id == "interests" -> {
                    QuestionTitle(t("Qu'est-ce qui vous fait envie ?"))
                    Text(t("Plusieurs choix possibles."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Quiz.Interest.entries.forEach { i ->
                            val selected = i in a.interests
                            FilterChip(
                                selected = selected,
                                onClick = { onAnswers { it.copy(interests = if (selected) it.interests - i else it.interests + i) } },
                                label = { Text("${i.emoji} ${t(i.label)}") },
                            )
                        }
                    }
                }
                id.startsWith("taste:") -> {
                    val interest = Quiz.Interest.valueOf(id.removePrefix("taste:"))
                    QuestionTitle(tasteQuestion(interest))
                    Text(t("Plusieurs choix possibles, ou aucun si tout vous va."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Quiz.Taste.of(interest).forEach { t ->
                        val selected = t in a.tastes
                        ChoiceCard(if (selected) "✓  ${com.riad.voyage.t(t.label)}" else com.riad.voyage.t(t.label), null, selected) {
                            onAnswers { it.copy(tastes = if (selected) it.tastes - t else it.tastes + t) }
                        }
                    }
                }
                id == "style" -> {
                    QuestionTitle(t("Plutôt incontournables ou pépites ?"))
                    Quiz.Style.entries.forEach { st ->
                        ChoiceCard(t(st.label), t(st.description), a.style == st) { onAnswers { it.copy(style = st) } }
                    }
                }
                id == "depth" -> {
                    QuestionTitle(t("Combien de temps aimez-vous passer dans chaque lieu ?"))
                    Quiz.Depth.entries.forEach { d ->
                        ChoiceCard(t(d.label), t(d.description), a.depth == d) { onAnswers { it.copy(depth = d) } }
                    }
                }
                id == "start" -> {
                    QuestionTitle(t("À quelle heure aimez-vous commencer la journée ?"))
                    Quiz.Start.entries.forEach { st ->
                        ChoiceCard(t(st.label), null, a.start == st) { onAnswers { it.copy(start = st) } }
                    }
                }
                id == "mobility" -> {
                    QuestionTitle(t("Comment vous déplacez-vous ?"))
                    Quiz.Mobility.entries.forEach { m ->
                        ChoiceCard(t(m.label), t(m.description), a.mobility == m) { onAnswers { it.copy(mobility = m) } }
                    }
                }
                id == "diet" -> {
                    QuestionTitle(t("Des préférences pour les repas et les soirées ?"))
                    Quiz.Diet.entries.forEach { d ->
                        ChoiceCard(t(d.label), null, a.diet == d) { onAnswers { it.copy(diet = d) } }
                    }
                }
                id == "pace" -> {
                    QuestionTitle(t("Quel rythme préférez-vous ?"))
                    Quiz.Pace.entries.forEach { p ->
                        ChoiceCard(t(p.label), t(p.description), a.pace == p) { onAnswers { it.copy(pace = p) } }
                    }
                }
                id == "budget" -> {
                    QuestionTitle(t("Quel budget pour les repas et les sorties ?"))
                    Quiz.Budget.entries.forEach { b ->
                        ChoiceCard(t(b.label), t(b.description), a.budget == b) { onAnswers { it.copy(budget = b) } }
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (step > 0) OutlinedButton(onClick = { onStep(-1) }, modifier = Modifier.weight(1f)) { Text(t("Retour")) }
            val last = step == steps.size - 1
            if (single != null) Button(onClick = edits.recapDone, enabled = canContinue, modifier = Modifier.weight(1f)) { Text(t("Valider")) }
            else Button(
                onClick = { if (last) onGenerate() else onStep(1) },
                enabled = canContinue,
                modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
            ) {
                if (last) {
                    Icon(AppIcons.Sparkle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Créer mon envolée"))
                } else Text(t("Suivant"))
            }
        }
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label)
    }
}

/** « Ajuster mes réponses » : toutes les réponses d'un coup d'œil ; une ligne touchée ouvre sa question. */
@Composable
private fun RecapScreen(a: Quiz.Answers, edits: ExperienceEdits) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Ajuster mes réponses"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(t("Touchez une ligne pour la changer, puis recomposez."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Quiz.steps(a, quick = false).forEach { id ->
                val (label, value) = recapLine(id, a)
                OutlinedCard(onClick = { edits.recapStep(id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium)
                        }
                        Text("›", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = edits.recapClose, modifier = Modifier.weight(1f)) { Text(t("Annuler")) }
            Button(onClick = edits.recapApply, modifier = Modifier.weight(1.6f)) {
                Icon(AppIcons.Sparkle, null)
                Spacer(Modifier.width(8.dp))
                Text(t("Recomposer"))
            }
        }
    }
}

/** Libellé et valeur d'une ligne du récapitulatif. */
private fun recapLine(id: String, a: Quiz.Answers): Pair<String, String> = when {
    id == "city" -> t("Ville") to t(a.city)
    id == "date" -> t("Premier jour") to formatDay(a.startDate)
    id == "duration" -> t("Durée et horaires") to listOfNotNull(t(a.duration.label),
        a.arrivalMin?.let { tf("arrivée {0}", com.riad.voyage.data.hhmm(it)!!) } , a.departureMin?.let { tf("départ {0}", com.riad.voyage.data.hhmm(it)!!) },
        t("soirée à l'hôtel").takeIf { a.restAfterArrival && a.arrivalMin != null }).joinToString(" · ")
    id == "company" -> t("Avec qui") to listOfNotNull(t(a.company.label), t("jeune enfant").takeIf { a.youngKids }, t("poussette").takeIf { a.stroller }).joinToString(" · ")
    id == "interests" -> t("Envies") to a.interests.joinToString(", ") { t(it.label) }
    id.startsWith("taste:") -> {
        val i = Quiz.Interest.valueOf(id.removePrefix("taste:"))
        t(i.label) to a.tastes.filter { it.interest == i }.joinToString(", ") { t(it.label) }.ifBlank { t("Tout") }
    }
    id == "style" -> t("Style") to t(a.style.label)
    id == "pace" -> t("Rythme") to t(a.pace.label)
    id == "depth" -> t("Temps de visite") to t(a.depth.label)
    id == "start" -> t("Début de journée") to t(a.start.label)
    id == "mobility" -> t("Déplacements") to t(a.mobility.label)
    id == "diet" -> t("Régime") to t(a.diet.label)
    id == "budget" -> t("Budget") to t(a.budget.label)
    else -> id to ""
}

@Composable
private fun QuestionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceCard(title: String, description: String?, selected: Boolean, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
            description?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun ExperienceResult(
    state: ExperienceState,
    onRegenerate: () -> Unit,
    onEdit: () -> Unit,
    onSave: () -> Unit,
    alternatives: (Int, Int) -> List<Pair<ActivityLinks.Category, PointOfInterest>>,
    onReplace: (Int, Int, Pair<ActivityLinks.Category, PointOfInterest>?) -> Unit,
    onHotels: () -> Unit,
    onBudget: () -> Unit,
    wiki: suspend (String) -> WikiSummary?,
    isFavorite: (String) -> Boolean,
    onFavorite: (PointOfInterest) -> Unit,
    onReadArticle: (String) -> Unit,
    edits: ExperienceEdits,
) {
    val exp = state.result ?: return
    var adding by remember { mutableStateOf<Int?>(null) }   // jour où l'on ajoute une étape
    adding?.let { d -> AddStepDialog(onDismiss = { adding = null }) { name -> edits.add(d, name); adding = null } }
    // Étape dont la fiche détaillée est ouverte.
    var detail by remember { mutableStateOf<ExperienceStep?>(null) }
    detail?.let { s ->
        PlaceDetailSheet(
            place = s.place, category = s.category, city = exp.city,
            extra = PlaceExtra(why = s.why, time = s.time, durationMin = s.durationMin),
            wiki = wiki, favorite = isFavorite(s.place.name), onFavorite = { onFavorite(s.place) },
            onReadArticle = { tag -> detail = null; onReadArticle(tag) },
            onDismiss = { detail = null },
        )
    }
    // Étape en cours de remplacement (jour, étape) et lieux proposés à la place.
    var editing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    editing?.let { (d, i) ->
        val step = exp.days.getOrNull(d)?.steps?.getOrNull(i)
        if (step == null) editing = null
        else ReplaceDialog(step, alternatives(d, i), exp.city,
            onPick = { choice -> onReplace(d, i, choice); editing = null },
            onDismiss = { editing = null },
            onMove = { delta -> edits.move(d, i, delta); editing = null },
            // Repas, arrivée et départ gardent leur place ; les visites se déplacent autour.
            canUp = i > 0 && !ExperienceBuilder.isAnchored(step) && !ExperienceBuilder.isAnchored(exp.days[d].steps[i - 1]),
            canDown = i < exp.days[d].steps.lastIndex && !ExperienceBuilder.isAnchored(step) && !ExperienceBuilder.isAnchored(exp.days[d].steps[i + 1]))
    }
    // Étape dont on change l'heure (les suivantes sont décalées).
    var timing by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    timing?.let { (d, i) ->
        val step = exp.days.getOrNull(d)?.steps?.getOrNull(i)
        if (step == null) timing = null
        else {
            var minutes by remember(d, i) { mutableStateOf(com.riad.voyage.data.minutesOf(step.time)) }
            AlertDialog(
                onDismissRequest = { timing = null },
                title = { Text(step.place.name) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeField(t("Nouvelle heure (HH:MM)"), minutes) { minutes = it }
                        Text(t("Les étapes suivantes sont décalées d'autant."), style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(enabled = minutes != null, onClick = { minutes?.let { edits.setTime(d, i, it) }; timing = null }) { Text(t("Valider")) }
                },
                dismissButton = { TextButton(onClick = { timing = null }) { Text(t("Annuler")) } },
            )
        }
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.checking) item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(t("Vérification des lieux…"), style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Sparkle, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Votre envolée"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = edits.newEnvolee) { Text("＋ " + t("Nouvelle envolée")) }
                    }
                    Text(exp.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(exp.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = onRegenerate) { Text(t("Autre proposition")) }
                        OutlinedButton(onClick = onEdit) { Text(t("Ajuster mes réponses")) }
                    }
                    // Ce qu'Envolée AI a compris, à corriger d'un geste.
                    state.aiSummary?.let { summary ->
                        Text(t("Envolée AI a compris :") + " " + summary, style = MaterialTheme.typography.bodySmall)
                        if (state.aiIgnored.isNotEmpty()) Text(tf("Non compris : {0}", state.aiIgnored.joinToString(", ")),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        AssumedChips(state.answers, edits.refine)
                        TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) { Text(t("Corriger les réponses ›")) }
                    }
                    if (edits.linked) {
                        TextButton(onClick = edits.openTrip, contentPadding = PaddingValues(0.dp)) {
                            Text(t("✓ Enregistré dans votre voyage, mis à jour à chaque changement ›"))
                        }
                    } else Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(t("💾 Enregistrer dans mon voyage")) }
                    OutlinedButton(onClick = onHotels, modifier = Modifier.fillMaxWidth()) { Text(t("🏨 Où dormir ?")) }
                    OutlinedButton(onClick = onBudget, modifier = Modifier.fillMaxWidth()) { Text(t("💰 Budget du séjour")) }
                }
            }
        }
        // Modifier en parlant (et replanifier le jour même).
        edits.askChange?.let { ask -> item { AskChangeCard(state, ask, edits.undo) } }
        // À savoir, en trois groupes repliables ; une remarque qui nomme un lieu du programme va sur son étape.
        val onStep = notesByPlace(exp)
        val attached = onStep.values.flatten().toSet()
        listOf(
            Triple("⚠️ " + t("Important"), exp.notes, true),
            Triple("📅 " + t("Pendant votre séjour"), exp.events, false),
            Triple("🔄 " + t("Ajustements faits par l'appli"), exp.changes, false),
        ).forEach { (title, list, open) ->
            val shown = list.filterNot { it in attached }
            if (shown.isNotEmpty()) item {
                Foldable("$title (${shown.size})", open, CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    shown.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp)) }
                }
            }
        }
        // Réglages utilisés : à toucher pour changer et recomposer le programme.
        item { SettingsChips(state.answers, edits.refine) }
        // Carte de l'itinéraire (une puce par jour).
        item {
            ProgramMap(exp.days.mapIndexed { i, d ->
                (d.title.ifBlank { tf("Jour {0}", i + 1) }) to d.steps.filter { it.place.hasCoordinates }.map { MapStop(it.place.name, it.place.lat, it.place.lon) }
            })
        }
        exp.days.forEachIndexed { dayIndex, day ->
            item { DayHeader(day, exp.city) }
            itemsIndexed(day.steps) { stepIndex, s ->
                val transfer = s.place.id.startsWith("transfer/")
                StepCard(s, exp.city, onStep[s.place.id].orEmpty(), onOpen = { detail = s }.takeIf { !transfer },
                    onTime = { timing = dayIndex to stepIndex }.takeIf { !transfer }, onChange = { editing = dayIndex to stepIndex }.takeIf { !transfer })
                // Frise : les trous de la journée sont visibles.
                day.steps.getOrNull(stepIndex + 1)?.let { next -> ExperienceBuilder.freeTime(s.time, s.durationMin, next.time) }?.let { gap ->
                    Text("⏳ " + t("Temps libre") + " · " + gap, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 66.dp))
                }
            }
            item {
                TextButton(onClick = { adding = dayIndex }, contentPadding = PaddingValues(0.dp)) { Text(t("+ Ajouter une étape")) }
            }
        }
        item {
            Text(
                t("Lieux : Wikidata, Wikivoyage, OpenStreetMap") +
                    (if (exp.days.any { d -> d.steps.any { it.place.ratings.isNotEmpty() } }) ", notes Google / Tripadvisor / Foursquare" else "") +
                    (if (exp.days.any { it.weather != null }) ", " + t("météo Open-Meteo") else "") + ".",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DayHeader(day: ExperienceDay, city: String) {
    val context = LocalContext.current
    val visits = day.steps.count { it.category !in ExperienceBuilder.venues && it.category != ActivityLinks.Category.TOURS }
    val meals = day.steps.count { it.category == ActivityLinks.Category.RESTAURANTS }
    val end = day.steps.lastOrNull()?.let { s -> com.riad.voyage.data.minutesOf(s.time)?.let { hhmm(it + s.durationMin) } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(day.title + (day.date?.let { " · " + formatDay(it) } ?: ""), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(listOfNotNull(day.weather?.label, t("journée plutôt à l'abri").takeIf { day.weather?.rainy == true },
                tf("{0} visite(s)", visits).takeIf { visits > 0 }, tf("{0} repas", meals).takeIf { meals > 0 }, end?.let { tf("fin vers {0}", it) })
                .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        TextButton(onClick = { openUrl(context, dayRouteUrl(day, city)) }) { Text(t("Parcours ›")) }
    }
}

/** Repères d'une étape en un coup d'œil : note, ouverture vérifiée, abri, envie, conseil. */
private fun badges(s: ExperienceStep, parts: List<String>): List<String> {
    val taste = t("Pour votre envie « {0} » · {1}").substringBefore(" «")
    return listOfNotNull(
        s.place.ratings.maxByOrNull { it.count }?.let { "⭐ " + "%.1f".format(it.rating) },
        ("✓ " + t("ouvert")).takeIf { parts.any { it.startsWith("✓ ") } },
        ("⚠️ " + t("horaires à vérifier")).takeIf { t("horaires à vérifier") in parts },
        ("☔ " + t("à l'abri")).takeIf { parts.any { it.startsWith(t("Idéal par temps de pluie")) } },
        ("❤️ " + t("votre envie")).takeIf { parts.any { it.startsWith(taste) } },
        ("💡 " + t("conseil")).takeIf { parts.any { it.startsWith("💡") } },
        "♿".takeIf { parts.any { it.startsWith("♿") } },
    )
}

/** Remarques qui nomment un lieu du programme (« foire sur la place des Quinconces ») : lieu → remarques. */
private fun notesByPlace(exp: com.riad.voyage.data.Experience): Map<String, List<String>> {
    val places = exp.days.flatMap { it.steps }.map { it.place }
        .filter { it.name.length >= 5 && listOf("transfer/", "free/", "pause/", "custom/").none { p -> it.id.startsWith(p) } }
    return exp.allNotes.mapNotNull { n -> places.firstOrNull { n.contains(it.name, ignoreCase = true) }?.let { it.id to n } }
        .groupBy({ it.first }, { it.second })
}

/** Trajet depuis l'étape précédente, sur une ligne à part : « 🚶 12 min · 850 m ». */
@Composable
private fun TravelLine(s: ExperienceStep) {
    val minutes = s.travelMin ?: return
    val m = s.fromPreviousM
    val walk = m == null || m < 1500 || minutes <= m / 80 + 1
    Text((if (walk) "🚶 " else "🚋 ") + tf("{0} min", minutes) + (m?.let { " · " + formatMeters(it) } ?: ""),
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 66.dp))
}

/**
 * Étape compacte : heure, nom, type, durée et repères ; un toucher déplie le détail (raisons, avis, conseils)
 * et les actions (itinéraire, billets, changer, fiche du lieu).
 */
@Composable
private fun StepCard(s: ExperienceStep, city: String, notes: List<String>, onOpen: (() -> Unit)?, onTime: (() -> Unit)?, onChange: (() -> Unit)?) {
    val context = LocalContext.current
    var open by rememberSaveable(s.place.id, s.time) { mutableStateOf(false) }
    val parts = s.why.split(" · ").filter { it.isNotBlank() }
    TravelLine(s)
    Row {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // L'heure se touche pour la changer (les étapes suivantes suivent).
            Text(s.time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                modifier = if (onTime != null) Modifier.clickable(onClick = onTime) else Modifier)
            Text(s.category.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Card(onClick = { open = !open }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t(s.moment).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    if (s.durationMin > 0) Text("⏱ " + ExperienceBuilder.formatDuration(s.durationMin), style = MaterialTheme.typography.labelSmall)
                }
                Text(s.place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tk(s.place.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                badges(s, parts).takeIf { it.isNotEmpty() }?.let { Text(it.joinToString("   "), style = MaterialTheme.typography.labelMedium) }
                notes.forEach { Text("📌 $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                if (open) {
                    parts.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (onChange != null) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {   // pas pour l'arrivée ni le départ
                        val url = if (s.place.isVenue) ActivityLinks.restaurantUrl(s.place, city) else ActivityLinks.ticketUrls(s.place, city).first().url
                        listOfNotNull(
                            t("Itinéraire") to { openUrl(context, ActivityLinks.directionsUrl(s.place, city)) },
                            (if (s.place.isFree) t("Infos") else if (s.place.isVenue) t("Réserver / voir") else t("Billets")) to { openUrl(context, url) },
                            t("Changer") to onChange,
                            onOpen?.let { t("Fiche") to it },
                        ).forEach { (label, action) ->
                            TextButton(onClick = action, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(label) }
                        }
                    }
                } else if (parts.isNotEmpty()) Text(t("Détails ▾"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatMeters(m: Int) = if (m < 1000) "$m m" else "${"%.1f".format(m / 1000.0)} km"

/** Parcours de la journée dans Google Maps, à pied, étape par étape. */
private fun dayRouteUrl(day: ExperienceDay, city: String): String {
    fun loc(s: ExperienceStep) =
        if (s.place.hasCoordinates) "${s.place.lat},${s.place.lon}"
        else URLEncoder.encode("${s.place.name} $city", "UTF-8").replace("+", "%20")
    val stops = day.steps.map(::loc)
    if (stops.size == 1) return "https://www.google.com/maps/search/?api=1&query=${stops[0]}"
    val waypoints = stops.drop(1).dropLast(1).take(8).joinToString("%7C")
    return "https://www.google.com/maps/dir/?api=1&origin=${stops.first()}&destination=${stops.last()}" +
        (if (waypoints.isNotEmpty()) "&waypoints=$waypoints" else "") + "&travelmode=walking"
}

private fun tasteQuestion(i: Quiz.Interest) = when (i) {
    Quiz.Interest.ART -> t("Quels musées vous attirent ?")
    Quiz.Interest.HISTORY -> t("Quel genre de patrimoine ?")
    Quiz.Interest.NATURE -> t("Quelle nature vous fait envie ?")
    Quiz.Interest.FUN -> t("Quelles activités de loisirs ?")
    Quiz.Interest.FOOD -> t("Quelle cuisine vous tente ?")
    Quiz.Interest.NIGHT -> t("Quelle ambiance pour vos soirées ?")
    Quiz.Interest.SHOPPING -> t("Quel shopping ?")
    Quiz.Interest.BEACH -> t("Quelles plages ?")
}

/** Retirer une étape ou la remplacer par un lieu de la liste. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReplaceDialog(
    step: ExperienceStep,
    options: List<Pair<ActivityLinks.Category, PointOfInterest>>,
    city: String,
    onPick: (Pair<ActivityLinks.Category, PointOfInterest>?) -> Unit,
    onDismiss: () -> Unit,
    onMove: (Int) -> Unit,
    canUp: Boolean,
    canDown: Boolean,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tf("Remplacer « {0} »", step.place.name)) },
        text = {
            LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onMove(-1) }, enabled = canUp, modifier = Modifier.weight(1f)) { Text(t("↑ Monter")) }
                        OutlinedButton(onClick = { onMove(1) }, enabled = canDown, modifier = Modifier.weight(1f)) { Text(t("↓ Descendre")) }
                    }
                }
                item {
                    OutlinedButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(t("Retirer sans remplacer"))
                    }
                }
                if (options.isEmpty()) {
                    item { Text(t("Pas d'autre lieu disponible pour cette étape."), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else {
                    item { Text(t("Ou choisissez un autre lieu :"), style = MaterialTheme.typography.labelLarge) }
                }
                items(options, key = { it.second.id }) { (c, p) ->
                    Card(
                        onClick = { onPick(c to p) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${c.emoji} ${p.name}", fontWeight = FontWeight.Bold)
                            Text(
                                tk(p.kind) + " · ⏱ " + ExperienceBuilder.formatDuration(
                                    ExperienceBuilder.visitMinutes(p, c),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            p.ratings.maxByOrNull { it.count }?.let { r ->
                                Text("★ ${"%.1f".format(r.rating)} ${r.source.label}", style = MaterialTheme.typography.labelMedium,
                                    color = com.riad.voyage.ui.theme.BrandCoral)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } },
    )
}

/** Ajouter une étape libre au programme. */
@Composable
private fun AddStepDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Nouvelle étape")) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(t("Lieu ou activité")) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onAdd(name) }, enabled = name.isNotBlank()) { Text(t("Ajouter")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } },
    )
}

/** Réglage du programme : clé (celle de « assumed »), libellé de la puce, choix possibles (chacun recompose le programme). */
private class Setting(val key: String, val label: String, val options: List<Pair<String, () -> Unit>>)

private fun settings(a: Quiz.Answers, refine: ((Quiz.Answers) -> Quiz.Answers) -> Unit): List<Setting> {
    // Un choix fait ici est un vrai choix, même s'il reprend la valeur par défaut : il n'est plus « non précisé ».
    fun <E> set(key: String, label: String, values: List<E>, name: (E) -> String, apply: Quiz.Answers.(E) -> Quiz.Answers) =
        Setting(key, label, values.map { v -> name(v) to { refine { x -> x.apply(v).let { it.copy(assumed = it.assumed - key) } } } })
    return listOf(
        set("company", "${a.company.emoji} ${t(a.company.label)}", Quiz.Company.entries, { "${it.emoji} ${t(it.label)}" }) { copy(company = it) },
        set("pace", t(a.pace.label), Quiz.Pace.entries, { t(it.label) }) { copy(pace = it) },
        set("mobility", t(a.mobility.label), Quiz.Mobility.entries, { t(it.label) }) { copy(mobility = it) },
        set("diet", "🍽️ " + t(a.diet.label), Quiz.Diet.entries, { t(it.label) }) { copy(diet = it) },
        set("budget", t(a.budget.label), Quiz.Budget.entries, { t(it.label) }) { copy(budget = it) },
        set("style", t(a.style.label), Quiz.Style.entries, { t(it.label) }) { copy(style = it) },
    )
}

/** Puces des réglages du programme ; chacune ouvre un menu et recompose le programme au choix. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsChips(a: Quiz.Answers, refine: ((Quiz.Answers) -> Quiz.Answers) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { settings(a, refine).forEach { SettingChip(it.label, it.options) } }
}

/** Réglages que la demande ne précisait pas : valeur par défaut, à préciser d'un geste juste ici. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AssumedChips(a: Quiz.Answers, refine: ((Quiz.Answers) -> Quiz.Answers) -> Unit) {
    val open = settings(a, refine).filter { it.key in a.assumed }
    if (open.isEmpty()) return
    Text(t("Non précisé (réglage par défaut), touchez pour préciser :"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { open.forEach { SettingChip("❓ " + it.label, it.options) } }
}

@Composable
private fun SettingChip(label: String, options: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (text, action) -> DropdownMenuItem(text = { Text(text) }, onClick = { open = false; action() }) }
        }
    }
}

/** Heure facultative (HH:MM) ; vide = pas de contrainte. */
@Composable
private fun TimeField(label: String, minutes: Int?, onChange: (Int?) -> Unit) {
    var text by remember { mutableStateOf(minutes?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v; if (v.isBlank()) onChange(null) else com.riad.voyage.data.minutesOf(v)?.let(onChange) },
        label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        isError = text.isNotBlank() && com.riad.voyage.data.minutesOf(text) == null,
    )
}

/** Modifier le programme en parlant ; raccourcis pour replanifier le jour même ; annulation. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskChangeCard(state: ExperienceState, onSend: (String) -> Unit, onUndo: () -> Unit) {
    var text by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Modifier avec Envolée AI"), fontWeight = FontWeight.Bold)
            OutlinedTextField(value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), enabled = !state.editing,
                placeholder = { Text(t("Ex. demain plus tranquille, remplace le musée par un parc")) },
                trailingIcon = { MicButton { text = (text + " " + it).trim() } })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(t("Il pleut"), t("On a 1 h de retard"), t("On est fatigués")).forEach { quick ->
                    AssistChip(onClick = { onSend(quick + " (" + t("replanifie la suite de la journée") + ")") }, label = { Text(quick) }, enabled = !state.editing)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.editing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.weight(1f))
                Button(onClick = { onSend(text); text = "" }, enabled = text.isNotBlank() && !state.editing) { Text(t("Appliquer")) }
            }
            state.changeSummary?.let { done ->
                Text("✓ $done", style = MaterialTheme.typography.bodySmall)
                if (state.undo != null) TextButton(onClick = onUndo, contentPadding = PaddingValues(0.dp)) { Text(t("Annuler cette modification")) }
            }
        }
    }
}

/**
 * Demande en langage libre : Envolée AI remplit le questionnaire puis le programme est composé.
 * Si elle pose une question, la réponse se donne explicitement (« Répondre ») : jamais mélangée à une nouvelle demande.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeTextCard(onSend: (String) -> Unit, question: String? = null, onAnswer: (String) -> Unit = {}, onDismiss: () -> Unit = {}) {
    if (question != null) {
        var answer by remember(question) { mutableStateOf("") }
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🤔 $question", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = answer, onValueChange = { answer = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("Votre réponse")) }, trailingIcon = { MicButton { answer = (answer + " " + it).trim() } })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(t("Nouvelle demande")) }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { onAnswer(answer) }, enabled = answer.isNotBlank()) { Text(t("Répondre")) }
                }
            }
        }
        return
    }
    var text by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Décrivez votre séjour"), fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                placeholder = { Text(t("Ex. 3 jours à Barcelone en famille fin mai, Gaudí et tapas, sans trop marcher")) },
                trailingIcon = { MicButton { text = (text + " " + it).trim() } },
            )
            // Exemples : un toucher remplit la demande, à compléter ou envoyer.
            if (text.isBlank()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(t("Week-end à Rome en amoureux, petit budget"), t("3 jours à Lisbonne avec un enfant de 4 ans"),
                    t("Une journée à Paris : musées et bonnes tables")).forEach { ex ->
                    AssistChip(onClick = { text = ex }, label = { Text(ex, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Button(onClick = { onSend(text) }, enabled = text.isNotBlank(), modifier = Modifier.align(Alignment.End)) {
                Text(t("Composer avec Envolée AI"))
            }
            Text(t("Ou répondez aux questions ci-dessous."), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Dicter au lieu d'écrire : reconnaissance vocale d'Android (gratuite, sans clé), dans la langue de l'app. */
@Composable
private fun MicButton(onText: (String) -> Unit) {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
    }
    val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, if (com.riad.voyage.I18n.lang == com.riad.voyage.Lang.EN) "en-GB" else "fr-FR")
        .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, t("Dites votre demande"))
    TextButton(onClick = { runCatching { launcher.launch(intent) } }) { Text("🎤") }
}
