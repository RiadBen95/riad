package com.riad.voyage.data

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.models.messages.WebSearchTool20260209
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate

/** Modèles (Anthropic) d'Envolée AI proposés dans les Réglages (prix de l'API par million de jetons : entrée / sortie). */
enum class ClaudeModel(val id: String, val label: String, val strengths: String, val price: String) {
    OPUS("claude-opus-5-5", "Opus 5.5", "Le plus capable : comprend les demandes nuancées, vérifie avec le plus de soin",
        "4 $ / 20 $ par million de jetons · ≈ 0,01 $ par demande"),
    SONNET("claude-sonnet-5-5", "Sonnet 5.5", "Équilibre entre qualité, rapidité et coût",
        "2 $ / 10 $ par million de jetons · ≈ 0,005 $ par demande"),
    HAIKU("claude-haiku-5-5", "Haiku 5.5", "Le plus rapide et le plus économique",
        "0,10 $ / 0,50 $ par million de jetons · ≈ 0,0003 $ par demande"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

/** La demande en langage libre, traduite en réponses du questionnaire (lue depuis le JSON de la réponse). */
class TripRequest(o: JSONObject) {
    /** Liste de textes, même si Envolée AI renvoie un seul texte au lieu d'une liste. */
    private fun list(o: JSONObject, k: String): List<String> {
        val a = o.optJSONArray(k) ?: return listOfNotNull(o.optString(k).takeIf { it.isNotBlank() && it != "null" })
        return List(a.length()) { a.optString(it) }.filter { it.isNotBlank() && it != "null" }
    }
    val city: String = o.optString("city")
    val startDate: String = o.optString("startDate")
    val days: Int = o.optInt("days")
    val halfDay: Boolean = o.optBoolean("halfDay")
    val company: String = o.optString("company")
    val interests: List<String> = list(o, "interests")
    val tastes: List<String> = list(o, "tastes")
    val style: String = o.optString("style")
    val pace: String = o.optString("pace")
    val mobility: String = o.optString("mobility")
    val diet: String = o.optString("diet")
    val budget: String = o.optString("budget")
    val arrivalTime: String = o.optString("arrivalTime")
    val departureTime: String = o.optString("departureTime")
    val wanted: List<String> = list(o, "wanted")
    val avoid: List<String> = list(o, "avoid")
    val travellers: Int = o.optInt("travellers")
    val restAfterArrival: Boolean = o.optBoolean("restAfterArrival")
}

/**
 * Claude dans Envolée : lit une demande libre (→ réponses du questionnaire), résume des avis,
 * vérifie un lieu par recherche web. Claude ne choisit jamais les lieux : ils viennent des sources de l'app.
 */
object ClaudeAssistant {
    private var cached: Pair<String, AnthropicClient>? = null

    private fun client(key: String): AnthropicClient =
        cached?.takeIf { it.first == key }?.second ?: AnthropicOkHttpClient.builder().apiKey(key).build().also { cached = key to it }

    private fun <E : Enum<E>> choices(values: Array<E>) = values.joinToString(", ") { it.name }

    /**
     * Une question, une réponse texte. Le JSON demandé est lu avec org.json (intégré à Android) :
     * les réponses « structurées » de la bibliothèque passent par une réflexion Java absente d'Android.
     */
    private fun ask(key: String, model: ClaudeModel, prompt: String, web: Boolean = false): String {
        val builder = MessageCreateParams.builder().model(model.id).maxTokens(4000L).addUserMessage(prompt)
        if (web) {
            if (model == ClaudeModel.HAIKU) builder.addTool(WebSearchTool20250305.builder().maxUses(3L).build())
            else builder.addTool(WebSearchTool20260209.builder().maxUses(3L).build())
        }
        return client(key).messages().create(builder.build()).content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
    }

    /** Dernier objet JSON de la réponse (Claude peut écrire du texte autour). */
    private fun json(text: String): JSONObject? {
        val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
        val end = text.lastIndexOf('}').takeIf { it > start } ?: return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    suspend fun describe(key: String, model: ClaudeModel, text: String, today: LocalDate): TripRequest? = withContext(Dispatchers.IO) {
        val prompt = """
            Tu aides une application de voyage à préparer un séjour. Lis la demande ci-dessous et remplis la fiche.
            Règles :
            - Laisse un champ texte vide ("") ou une liste vide si la demande ne le précise pas ; n'invente rien.
            - Nous sommes le $today. startDate au format AAAA-MM-JJ.
            - days : nombre de jours du séjour (0 si inconnu) ; halfDay : true pour une demi-journée.
            - arrivalTime / departureTime au format HH:MM (arrivée le premier jour, départ le dernier jour) ; une heure
              approximative se convertit : matin 09:00, midi 12:00, après-midi 15:00, soir 19:00, nuit 22:00.
            - restAfterArrival : true si la personne veut rester à l'hôtel (se reposer) après son arrivée.
            - travellers : nombre de voyageurs s'il est dit ou évident (« seul » = 1, « à deux » ou « en couple » = 2), sinon 0.
            - company parmi : ${choices(Quiz.Company.entries.toTypedArray())}.
            - interests parmi : ${choices(Quiz.Interest.entries.toTypedArray())}.
            - tastes parmi : ${Quiz.Taste.entries.joinToString(", ") { "${it.name} (${it.label})" }}.
            - style parmi : ${choices(Quiz.Style.entries.toTypedArray())} ; pace parmi : ${choices(Quiz.Pace.entries.toTypedArray())}.
            - mobility parmi : ${choices(Quiz.Mobility.entries.toTypedArray())} ; diet parmi : ${choices(Quiz.Diet.entries.toTypedArray())} ;
              budget parmi : ${choices(Quiz.Budget.entries.toTypedArray())}.
            - wanted : lieux ou noms cités explicitement (ex. « Sagrada Família », « Gaudí ») ; avoid : lieux ou types à éviter.
            Réponds uniquement par un objet JSON avec les clés : city, startDate, days, halfDay, company, interests, tastes,
            style, pace, mobility, diet, budget, arrivalTime, departureTime, restAfterArrival, travellers, wanted, avoid.

            Demande : $text
        """.trimIndent()
        json(ask(key, model, prompt))?.let(::TripRequest)
    }

    /** Résumé en une phrase des avis de chaque lieu ([id], nom, avis) → id → résumé. */
    suspend fun summarize(key: String, model: ClaudeModel, places: List<Triple<String, String, List<String>>>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val input = places.joinToString("\n\n") { (id, name, reviews) -> "id=$id · $name\n" + reviews.joinToString("\n") { "- $it" } }
            val prompt = "Pour chaque lieu, résume ses avis de visiteurs en une phrase courte et utile (moins de 20 mots, " +
                "en ${com.riad.voyage.I18n.lang.nativeName}), sans inventer. Réponds uniquement par un objet JSON " +
                "{\"id du lieu\": \"résumé\", …} avec les mêmes id.\n\n$input"
            val o = json(ask(key, model, prompt)) ?: return@withContext emptyMap()
            o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
        }

    /** Vérifie par recherche web un lieu que Google ne connaît pas : fermé ? horaires ce jour-là ? */
    suspend fun webCheck(key: String, model: ClaudeModel, name: String, city: String, date: LocalDate?): PlaceCheck? =
        withContext(Dispatchers.IO) {
            val text = ask(key, model,
                "Le lieu « $name » à $city est-il encore ouvert au public" + (date?.let { ", et quels sont ses horaires le $it" } ?: "") +
                    " ? Cherche sur le web, puis réponds uniquement par une ligne JSON : " +
                    "{\"closedForGood\": true ou false, \"hours\": \"horaires ce jour-là ou vide\"}", web = true)
            val json = Regex("\\{[^{}]*\\}").findAll(text).lastOrNull()?.value ?: return@withContext null
            runCatching {
                val o = JSONObject(json)
                val hours = o.optString("hours").ifBlank { null }
                PlaceCheck(o.optBoolean("closedForGood"), hours, false, null, emptyList())
            }.getOrNull()
        }

    /**
     * Fiche d'Envolée AI → réponses du questionnaire, en partant de [base] (réponses par défaut, complétées par le
     * voyage relié) : rien d'une demande précédente ne se mélange à la nouvelle. Renvoie aussi ce qui n'a pas été compris.
     */
    fun toAnswers(r: TripRequest, base: Quiz.Answers): Pair<Quiz.Answers, List<String>> {
        val ignored = mutableListOf<String>()
        fun <E : Enum<E>> pick(values: Array<E>, name: String, default: E, what: String): E {
            if (name.isBlank()) return default
            return values.firstOrNull { it.name == name.trim().uppercase() } ?: default.also { ignored += "$what « $name »" }
        }
        fun <E : Enum<E>> pickAll(values: Array<E>, names: List<String>, what: String) = names.mapNotNull { n ->
            values.firstOrNull { it.name == n.trim().uppercase() }.also { if (it == null && n.isNotBlank()) ignored += "$what « $n »" }
        }.toSet()
        if (r.days > Quiz.Duration.MAX_DAYS) ignored += com.riad.voyage.tf("{0} jours (au plus {1})", r.days, Quiz.Duration.MAX_DAYS)
        val days = r.days.coerceIn(0, Quiz.Duration.MAX_DAYS)
        val start = r.startDate.takeIf { it.isNotBlank() }?.let { d ->
            runCatching { LocalDate.parse(d.trim()) }.getOrNull()?.takeIf { !it.isBefore(LocalDate.now()) }
                ?: null.also { ignored += com.riad.voyage.tf("date « {0} »", d) }
        }
        fun time(v: String, what: String) = v.takeIf { it.isNotBlank() }?.let { minutesOf(it) ?: null.also { ignored += "$what « $v »" } }
        val company = when {
            r.company.isNotBlank() -> pick(Quiz.Company.entries.toTypedArray(), r.company, base.company, com.riad.voyage.t("compagnie"))
            r.travellers == 1 -> Quiz.Company.SOLO
            r.travellers == 2 -> Quiz.Company.COUPLE
            r.travellers >= 3 -> Quiz.Company.FRIENDS
            else -> base.company
        }
        val answers = base.copy(
            city = r.city.trim().ifBlank { base.city },
            place = if (r.city.isBlank()) base.place else null,
            startDate = start ?: base.startDate,
            duration = when {
                r.halfDay -> Quiz.Duration.HALF
                days > 0 -> Quiz.Duration(days)
                else -> base.duration
            },
            company = company,
            interests = pickAll(Quiz.Interest.entries.toTypedArray(), r.interests, com.riad.voyage.t("envie")).ifEmpty { base.interests },
            tastes = pickAll(Quiz.Taste.entries.toTypedArray(), r.tastes, com.riad.voyage.t("goût")),
            style = pick(Quiz.Style.entries.toTypedArray(), r.style, base.style, com.riad.voyage.t("style")),
            pace = pick(Quiz.Pace.entries.toTypedArray(), r.pace, base.pace, com.riad.voyage.t("rythme")),
            mobility = pick(Quiz.Mobility.entries.toTypedArray(), r.mobility, base.mobility, com.riad.voyage.t("déplacements")),
            diet = pick(Quiz.Diet.entries.toTypedArray(), r.diet, base.diet, com.riad.voyage.t("régime")),
            budget = pick(Quiz.Budget.entries.toTypedArray(), r.budget, base.budget, com.riad.voyage.t("budget")),
            arrivalMin = time(r.arrivalTime, com.riad.voyage.t("arrivée")) ?: base.arrivalMin,
            departureMin = time(r.departureTime, com.riad.voyage.t("départ")) ?: base.departureMin,
            wanted = r.wanted.filter { it.isNotBlank() },
            avoidNames = r.avoid.filter { it.isNotBlank() },
            restAfterArrival = r.restAfterArrival,
        )
        return answers to ignored
    }
}
