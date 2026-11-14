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

/** Modèles Claude proposés dans les Réglages (prix de l'API par million de jetons : entrée / sortie). */
enum class ClaudeModel(val id: String, val label: String, val strengths: String, val price: String) {
    OPUS("claude-opus-5-5", "Claude Opus 5.5", "Le plus capable : comprend les demandes nuancées, vérifie avec le plus de soin",
        "4 $ / 20 $ par million de jetons · ≈ 0,01 $ par demande"),
    SONNET("claude-sonnet-5-5", "Claude Sonnet 5.5", "Équilibre entre qualité, rapidité et coût",
        "2 $ / 10 $ par million de jetons · ≈ 0,005 $ par demande"),
    HAIKU("claude-haiku-5-5", "Claude Haiku 5.5", "Le plus rapide et le plus économique",
        "0,10 $ / 0,50 $ par million de jetons · ≈ 0,0003 $ par demande"),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

/** Réponse structurée : la demande en langage libre, traduite en réponses du questionnaire. */
class TripRequest {
    var city: String = ""
    var startDate: String = ""
    var days: Int = 0
    var halfDay: Boolean = false
    var company: String = ""
    var interests: List<String> = emptyList()
    var tastes: List<String> = emptyList()
    var style: String = ""
    var pace: String = ""
    var mobility: String = ""
    var diet: String = ""
    var budget: String = ""
    var arrivalTime: String = ""
    var departureTime: String = ""
    var wanted: List<String> = emptyList()
    var avoid: List<String> = emptyList()
}

class ReviewSummary {
    var id: String = ""
    var summary: String = ""
}

class ReviewSummaries {
    var items: List<ReviewSummary> = emptyList()
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

    suspend fun describe(key: String, model: ClaudeModel, text: String, today: LocalDate): TripRequest? = withContext(Dispatchers.IO) {
        val prompt = """
            Tu aides une application de voyage à préparer un séjour. Lis la demande ci-dessous et remplis la fiche.
            Règles :
            - Laisse un champ texte vide ("") ou une liste vide si la demande ne le précise pas ; n'invente rien.
            - Nous sommes le $today. startDate au format AAAA-MM-JJ.
            - days : nombre de jours du séjour (0 si inconnu) ; halfDay : true pour une demi-journée.
            - arrivalTime / departureTime au format HH:MM (arrivée le premier jour, départ le dernier jour).
            - company parmi : ${choices(Quiz.Company.entries.toTypedArray())}.
            - interests parmi : ${choices(Quiz.Interest.entries.toTypedArray())}.
            - tastes parmi : ${Quiz.Taste.entries.joinToString(", ") { "${it.name} (${it.label})" }}.
            - style parmi : ${choices(Quiz.Style.entries.toTypedArray())} ; pace parmi : ${choices(Quiz.Pace.entries.toTypedArray())}.
            - mobility parmi : ${choices(Quiz.Mobility.entries.toTypedArray())} ; diet parmi : ${choices(Quiz.Diet.entries.toTypedArray())} ;
              budget parmi : ${choices(Quiz.Budget.entries.toTypedArray())}.
            - wanted : lieux ou noms cités explicitement (ex. « Sagrada Família », « Gaudí ») ; avoid : lieux ou types à éviter.

            Demande : $text
        """.trimIndent()
        val params = MessageCreateParams.builder()
            .model(model.id)
            .maxTokens(4000L)
            .outputConfig(TripRequest::class.java)
            .addUserMessage(prompt)
            .build()
        client(key).messages().create(params).content().firstNotNullOfOrNull { it.text().orElse(null)?.text() }
    }

    /** Résumé en une phrase des avis de chaque lieu ([id], nom, avis) → id → résumé. */
    suspend fun summarize(key: String, model: ClaudeModel, places: List<Triple<String, String, List<String>>>): Map<String, String> =
        withContext(Dispatchers.IO) {
            val input = places.joinToString("\n\n") { (id, name, reviews) -> "id=$id · $name\n" + reviews.joinToString("\n") { "- $it" } }
            val params = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(4000L)
                .outputConfig(ReviewSummaries::class.java)
                .addUserMessage(
                    "Pour chaque lieu, résume ses avis de visiteurs en une phrase courte et utile (moins de 20 mots, " +
                        "en ${com.riad.voyage.I18n.lang.nativeName}), sans inventer. Garde le même id.\n\n$input",
                )
                .build()
            client(key).messages().create(params).content().firstNotNullOfOrNull { it.text().orElse(null)?.text() }
                ?.items?.associate { it.id to it.summary }.orEmpty()
        }

    /** Vérifie par recherche web un lieu que Google ne connaît pas : fermé ? horaires ce jour-là ? */
    suspend fun webCheck(key: String, model: ClaudeModel, name: String, city: String, date: LocalDate?): PlaceCheck? =
        withContext(Dispatchers.IO) {
            val builder = MessageCreateParams.builder()
                .model(model.id)
                .maxTokens(4000L)
                .addUserMessage(
                    "Le lieu « $name » à $city est-il encore ouvert au public" + (date?.let { ", et quels sont ses horaires le $it" } ?: "") +
                        " ? Cherche sur le web, puis réponds uniquement par une ligne JSON : " +
                        "{\"closedForGood\": true ou false, \"hours\": \"horaires ce jour-là ou vide\"}",
                )
            if (model == ClaudeModel.HAIKU) builder.addTool(WebSearchTool20250305.builder().maxUses(3L).build())
            else builder.addTool(WebSearchTool20260209.builder().maxUses(3L).build())
            val text = client(key).messages().create(builder.build()).content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
            val json = Regex("\\{[^{}]*\\}").findAll(text).lastOrNull()?.value ?: return@withContext null
            runCatching {
                val o = JSONObject(json)
                val hours = o.optString("hours").ifBlank { null }
                PlaceCheck(o.optBoolean("closedForGood"), hours, false, null, emptyList())
            }.getOrNull()
        }

    /** Fiche de Claude → réponses du questionnaire (les valeurs inconnues gardent les réponses actuelles). */
    fun toAnswers(r: TripRequest, a: Quiz.Answers): Quiz.Answers {
        fun <E : Enum<E>> pick(values: Array<E>, name: String, default: E) = values.firstOrNull { it.name == name.trim().uppercase() } ?: default
        val days = r.days.coerceIn(0, Quiz.Duration.MAX_DAYS)
        return a.copy(
            city = r.city.trim().ifBlank { a.city },
            place = if (r.city.isBlank()) a.place else null,
            startDate = runCatching { LocalDate.parse(r.startDate.trim()) }.getOrNull() ?: a.startDate,
            duration = when {
                r.halfDay -> Quiz.Duration.HALF
                days > 0 -> Quiz.Duration(days)
                else -> a.duration
            },
            company = pick(Quiz.Company.entries.toTypedArray(), r.company, a.company),
            interests = r.interests.mapNotNull { n -> Quiz.Interest.entries.firstOrNull { it.name == n.trim().uppercase() } }.toSet().ifEmpty { a.interests },
            tastes = r.tastes.mapNotNull { n -> Quiz.Taste.entries.firstOrNull { it.name == n.trim().uppercase() } }.toSet(),
            style = pick(Quiz.Style.entries.toTypedArray(), r.style, a.style),
            pace = pick(Quiz.Pace.entries.toTypedArray(), r.pace, a.pace),
            mobility = pick(Quiz.Mobility.entries.toTypedArray(), r.mobility, a.mobility),
            diet = pick(Quiz.Diet.entries.toTypedArray(), r.diet, a.diet),
            budget = pick(Quiz.Budget.entries.toTypedArray(), r.budget, a.budget),
            arrivalMin = minutesOf(r.arrivalTime) ?: a.arrivalMin,
            departureMin = minutesOf(r.departureTime) ?: a.departureMin,
            wanted = r.wanted.filter { it.isNotBlank() },
            avoidNames = r.avoid.filter { it.isNotBlank() },
        )
    }
}
