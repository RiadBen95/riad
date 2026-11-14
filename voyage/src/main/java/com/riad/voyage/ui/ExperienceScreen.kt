package com.riad.voyage.ui

import ActivityLinks
import ExperienceDay
import ExperienceStep
import Place
import Quiz
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riad.voyage.ExperienceState
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.ExperienceBuilder
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
) {
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text(t("Création de votre expérience…"), style = MaterialTheme.typography.titleMedium)
                Text(state.stage, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
        state.result != null -> ExperienceResult(state, onRegenerate, onEdit, onSave, alternatives, onReplace, onHotels, onBudget,
            wiki, isFavorite, onFavorite, onReadArticle)
        else -> Questionnaire(state, onAnswers, onStep, onGenerate, suggestCities)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Questionnaire(
    state: ExperienceState,
    onAnswers: ((Quiz.Answers) -> Quiz.Answers) -> Unit,
    onStep: (Int) -> Unit,
    onGenerate: () -> Unit,
    suggestCities: suspend (String) -> List<Place>,
) {
    val a = state.answers
    val steps = Quiz.steps(a)
    val step = state.step.coerceIn(0, steps.size - 1)
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
            Text(t("Votre expérience sur mesure"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                t("Quelques questions sur vos envies et vos goûts : nous composons un programme qui mêle visites, balades, restaurants et sorties."),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(progress = { (step + 1f) / steps.size }, modifier = Modifier.fillMaxWidth())
            Text(tf("Question {0} sur {1}", step + 1, steps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                id == "city" -> {
                    QuestionTitle(t("Où voulez-vous vivre l'expérience ?"))
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
            Button(
                onClick = { if (last) onGenerate() else onStep(1) },
                enabled = canContinue,
                modifier = Modifier.weight(if (step > 0) 1.6f else 1f),
            ) {
                if (last) {
                    Icon(AppIcons.Sparkle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Créer mon expérience"))
                } else Text(t("Suivant"))
            }
        }
    }
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
) {
    val exp = state.result ?: return
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
            onDismiss = { editing = null })
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Text(t("Votre expérience"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(exp.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(exp.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        Button(onClick = onRegenerate) { Text(t("Autre proposition")) }
                        OutlinedButton(onClick = onEdit) { Text(t("Modifier")) }
                    }
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text(t("💾 Enregistrer dans mon voyage")) }
                    OutlinedButton(onClick = onHotels, modifier = Modifier.fillMaxWidth()) { Text(t("🏨 Où dormir ?")) }
                    OutlinedButton(onClick = onBudget, modifier = Modifier.fillMaxWidth()) { Text(t("💰 Budget du séjour")) }
                }
            }
        }
        exp.days.forEachIndexed { dayIndex, day ->
            item { DayHeader(day, exp.city) }
            itemsIndexed(day.steps) { stepIndex, s -> StepCard(s, exp.city, onOpen = { detail = s }) { editing = dayIndex to stepIndex } }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(
                day.title + (day.date?.let { " · " + formatDay(it) } ?: ""),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            )
            day.weather?.let { w ->
                Text(
                    w.label + if (w.rainy) " · " + t("journée plutôt à l'abri") else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        TextButton(onClick = { openUrl(context, dayRouteUrl(day, city)) }) { Text(t("Parcours sur la carte ›")) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepCard(s: ExperienceStep, city: String, onOpen: () -> Unit = {}, onChange: () -> Unit = {}) {
    val context = LocalContext.current
    Row {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(s.category.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Card(onClick = onOpen, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t(s.moment).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tk(s.place.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(s.why, style = MaterialTheme.typography.bodySmall)
                if (s.durationMin > 0) {
                    Text(
                        "⏱ " + tf("{0} sur place", ExperienceBuilder.formatDuration(s.durationMin)),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                s.travelMin?.let { t ->
                    val m = s.fromPreviousM
                    Text(
                        when {
                            m == null -> tf("Trajet d'environ {0} min depuis l'étape précédente", t)
                            m < 1500 || t <= m / 80 + 1 -> tf("🚶 {0} · ~{1} min à pied depuis l'étape précédente", formatMeters(m), t)
                            else -> tf("🚇 {0} · ~{1} min en transports depuis l'étape précédente", formatMeters(m), t)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { openUrl(context, ActivityLinks.directionsUrl(s.place, city)) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(t("Itinéraire"))
                    }
                    val url = if (s.place.isVenue) ActivityLinks.restaurantUrl(s.place, city)
                    else ActivityLinks.ticketUrls(s.place, city).first().url
                    TextButton(onClick = { openUrl(context, url) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                        Text(
                            when {
                                s.place.isFree -> t("Infos")
                                s.place.isVenue -> t("Réserver / voir")
                                else -> t("Billets")
                            },
                        )
                    }
                    TextButton(onClick = onChange, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(t("Changer")) }
                }
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
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tf("Remplacer « {0} »", step.place.name)) },
        text = {
            LazyColumn(Modifier.height(420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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
