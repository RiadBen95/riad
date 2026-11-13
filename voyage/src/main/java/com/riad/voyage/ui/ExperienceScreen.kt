package com.riad.voyage.ui

import com.riad.voyage.tk
import com.riad.voyage.tf
import com.riad.voyage.t
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.riad.voyage.ExperienceState
import com.riad.voyage.data.ActivityLinks
import com.riad.voyage.data.ExperienceDay
import com.riad.voyage.data.ExperienceStep
import com.riad.voyage.data.Place
import com.riad.voyage.data.Quiz
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
        state.result != null -> ExperienceResult(state, onRegenerate, onEdit, onSave)
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
                    Quiz.Duration.entries.forEach { d ->
                        ChoiceCard(t(d.label), null, a.duration == d) { onAnswers { it.copy(duration = d) } }
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
private fun ExperienceResult(state: ExperienceState, onRegenerate: () -> Unit, onEdit: () -> Unit, onSave: () -> Unit) {
    val exp = state.result ?: return
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
                }
            }
        }
        exp.days.forEach { day ->
            item { DayHeader(day, exp.city) }
            items(day.steps) { s -> StepCard(s, exp.city) }
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

@Composable
private fun StepCard(s: ExperienceStep, city: String) {
    val context = LocalContext.current
    Row {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(s.time, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(s.category.emoji, style = MaterialTheme.typography.titleLarge)
        }
        Card(Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t(s.moment).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.place.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(tk(s.place.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                Text(s.why, style = MaterialTheme.typography.bodySmall)
                if (s.durationMin > 0) {
                    Text(
                        "⏱ " + tf("{0} sur place", com.riad.voyage.data.ExperienceBuilder.formatDuration(s.durationMin)),
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
