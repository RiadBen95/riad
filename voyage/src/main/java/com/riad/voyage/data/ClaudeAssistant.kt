package com.riad.voyage.data

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.Base64PdfSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.WebSearchTool20250305
import com.anthropic.models.messages.WebSearchTool20260209
import com.riad.voyage.t
import com.riad.voyage.tf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.Base64

/** Moteurs d'Envolée AI proposés dans les Réglages : Claude (Anthropic, payant à l'usage) ou Gemini (Google, gratuit). */
enum class ClaudeModel(val id: String, val label: String, val strengths: String, val price: String, val gemini: Boolean = false) {
    OPUS("claude-opus-5-5", "Opus 5.5", "Le plus capable : comprend les demandes nuancées, vérifie avec le plus de soin",
        "4 $ / 20 $ par million de jetons · ≈ 0,01 $ par demande"),
    SONNET("claude-sonnet-5-5", "Sonnet 5.5", "Équilibre entre qualité, rapidité et coût",
        "2 $ / 10 $ par million de jetons · ≈ 0,005 $ par demande"),
    HAIKU("claude-haiku-5-5", "Haiku 5.5", "Le plus rapide et le plus économique",
        "0,10 $ / 0,50 $ par million de jetons · ≈ 0,0003 $ par demande"),
    GEMINI("gemini-flash-latest", "Gemini Flash (Google)", "Gratuit : lit les demandes et les billets, rédige ; recherche web limitée",
        "Gratuit dans la limite d'un quota quotidien · sur l'offre gratuite, Google peut utiliser les demandes pour améliorer ses modèles", gemini = true),
    ;

    companion object {
        fun of(id: String) = entries.firstOrNull { it.id == id } ?: OPUS
    }
}

/** Fonctions d'Envolée AI, chacune activable dans les Réglages. */
enum class AiFeature(val label: String, val description: String) {
    EDIT("Modifier en parlant et replanifier", "« Demain plus tranquille », « il pleut », « on a 1 h de retard »…"),
    IMPORT("Lire les réservations", "Billets et réservations d'hôtel importés ou partagés avec l'appli remplissent le voyage."),
    QUESTIONS("Questions de précision", "Une ou deux questions quand la demande est ambiguë."),
    DATES("Vérifier les dates du séjour", "Jours fériés, fermetures, grèves, festivals (avec les recherches web)."),
    ACCESS("Accessibilité", "Poussette, fauteuil, escaliers : les lieux concernés sont signalés."),
    TIPS("Conseils par étape", "Réserver à l'avance, gratuité, tenue, meilleure heure."),
    FOOD("Arbitrer les restaurants", "Un restaurant qui ne colle pas à vos envies (« sans pizza », « vrai halal ») est remplacé."),
    BUDGET("Commenter le budget", "Pistes d'économies si l'estimation dépasse votre budget."),
    OUTINGS("Balades et excursions", "Quand la ville est épuisée (long séjour) : balades de quartier et excursions à la journée."),
    STORY("Rédiger le carnet", "Une introduction, un texte par jour et des phrases utiles sur mesure dans le PDF."),
    SOUVENIR("Souvenir du voyage", "Un résumé du séjour à partager."),
}

/** Liste de textes, même si Envolée AI renvoie un seul texte au lieu d'une liste. */
private fun JSONObject.texts(k: String): List<String> {
    val a = optJSONArray(k) ?: return listOfNotNull(optString(k).takeIf { it.isNotBlank() && it != "null" })
    return List(a.length()) { a.optString(it) }.filter { it.isNotBlank() && it != "null" }
}

/** La demande en langage libre, traduite en réponses du questionnaire (lue depuis le JSON de la réponse). */
class TripRequest(o: JSONObject) {
    val city: String = o.optString("city")
    val startDate: String = o.optString("startDate")
    val days: Int = o.optInt("days")
    val halfDay: Boolean = o.optBoolean("halfDay")
    val company: String = o.optString("company")
    val interests: List<String> = o.texts("interests")
    val tastes: List<String> = o.texts("tastes")
    val style: String = o.optString("style")
    val pace: String = o.optString("pace")
    val mobility: String = o.optString("mobility")
    val diet: String = o.optString("diet")
    val budget: String = o.optString("budget")
    val arrivalTime: String = o.optString("arrivalTime")
    val departureTime: String = o.optString("departureTime")
    val wanted: List<String> = o.texts("wanted")
    val avoid: List<String> = o.texts("avoid")
    val travellers: Int = o.optInt("travellers")
    val restAfterArrival: Boolean = o.optBoolean("restAfterArrival")
    val childAges: List<Int> = o.optJSONArray("childAges")?.let { a -> List(a.length()) { a.optInt(it, -1) } }.orEmpty().filter { it >= 0 }
    val stroller: Boolean = o.optBoolean("stroller")
    val budgetEur: Int = o.optInt("budgetEur")
    val maxWalkMin: Int = o.optInt("maxWalkMin")
    val warnings: List<String> = o.texts("warnings")
    val question: String = o.optString("question").takeIf { it != "null" }.orEmpty()
    val country: String = o.optString("country").takeIf { it != "null" }.orEmpty()
}

/** Réservations lues par Envolée AI : les trajets (vols, trains) dans l'ordre, et l'hôtel s'il y en a un. */
data class Booking(val segments: List<Segment>, val passengers: Int, val price: Double?, val currency: String, val reference: String,
                   val hotel: Hotel? = null) {
    data class Hotel(val name: String, val city: String, val checkIn: LocalDate?, val checkOut: LocalDate?,
                     val price: Double?, val currency: String, val reference: String)

    /** Ville du séjour : arrivée du trajet aller, sinon ville de l'hôtel. */
    val destination get() = outbound?.toCity ?: hotel?.city.orEmpty()

    data class Segment(
        val fromCity: String, val toCity: String, val from: String, val to: String,
        val depDate: LocalDate?, val depTime: Int?, val arrDate: LocalDate?, val arrTime: Int?, val number: String,
    )

    /** Ville du séjour : là où l'on reste le plus longtemps entre deux trajets (ou la dernière arrivée pour un aller simple). */
    val stayIndex: Int get() {
        if (segments.size < 2 || !segments.last().toCity.equals(segments.first().fromCity, true)) return segments.lastIndex
        return (0 until segments.lastIndex).maxByOrNull { i ->
            val a = segments[i].arrDate ?: return@maxByOrNull 0L
            val d = segments[i + 1].depDate ?: return@maxByOrNull 0L
            java.time.temporal.ChronoUnit.DAYS.between(a, d)
        } ?: segments.lastIndex
    }
    val outbound get() = segments.getOrNull(stayIndex)
    val inbound get() = segments.getOrNull(stayIndex + 1)
}

/** Conseils pour une étape : astuce, accessibilité, et raison de remplacer un restaurant qui ne colle pas. */
data class StepAdvice(val tip: String?, val access: String?, val unsuitable: String?, val passage: Boolean = false,
                      val notThen: String? = null, val minutes: Int = 0)

/** Textes du carnet : introduction, un texte par jour, phrases utiles sur mesure (sens, phrase locale). */
data class Story(val intro: String, val days: List<String>, val phrases: List<Pair<String, String>>)

/** Modification demandée en parlant : réponses à changer et opérations sur le programme. */
data class EditPlan(val summary: String, val answers: JSONObject?, val ops: List<JSONObject>)

/**
 * Envolée AI : comprend une demande libre, lit un billet, modifie le programme en parlant, vérifie et conseille.
 * Elle ne choisit jamais les lieux elle-même : ils viennent des sources de l'appli.
 */
object ClaudeAssistant {
    /** Moteur et clé à utiliser (Claude ou Gemini). */
    class Config(val model: ClaudeModel, val key: String)

    /** Pièce jointe (PDF ou image JPEG / PNG) envoyée telle quelle au moteur. */
    class Attachment(val bytes: ByteArray, val mime: String)

    private var cached: Pair<String, AnthropicClient>? = null

    private fun client(key: String): AnthropicClient =
        cached?.takeIf { it.first == key }?.second ?: AnthropicOkHttpClient.builder().apiKey(key).build().also { cached = key to it }

    private fun <E : Enum<E>> choices(values: Array<E>) = values.joinToString(", ") { it.name }

    private val language get() = com.riad.voyage.I18n.lang.nativeName

    /**
     * Une question, une réponse texte (avec recherche web et pièces jointes si besoin). Le JSON demandé est lu
     * avec org.json : les réponses « structurées » des bibliothèques passent par une réflexion absente d'Android.
     */
    private suspend fun ask(c: Config, prompt: String, web: Boolean = false, files: List<Attachment> = emptyList()): String =
        withContext(Dispatchers.IO) {
            (if (c.model.gemini) askGemini(c, prompt, web, files) else askClaude(c, prompt, web, files))
                .also { Diagnostics.lastAiResponse = it }
        }

    private fun askClaude(c: Config, prompt: String, web: Boolean, files: List<Attachment>): String {
        val builder = MessageCreateParams.builder().model(c.model.id).maxTokens(4000L)
        if (files.isEmpty()) builder.addUserMessage(prompt)
        else builder.addUserMessageOfContentBlockParams(listOf(ContentBlockParam.ofText(prompt)) + files.map { f ->
            val data = Base64.getEncoder().encodeToString(f.bytes)
            if (f.mime == "application/pdf") ContentBlockParam.ofDocument(Base64PdfSource.builder().data(data).build())
            else ContentBlockParam.ofImage(Base64ImageSource.builder().data(data).mediaType(
                if (f.mime == "image/png") Base64ImageSource.MediaType.IMAGE_PNG else Base64ImageSource.MediaType.IMAGE_JPEG).build())
        })
        if (web) {
            if (c.model == ClaudeModel.HAIKU) builder.addTool(WebSearchTool20250305.builder().maxUses(3L).build())
            else builder.addTool(WebSearchTool20260209.builder().maxUses(3L).build())
        }
        return client(c.key).messages().create(builder.build()).content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("")
    }

    /** Gemini (API REST de Google) : texte, pièces jointes, recherche Google si demandée. */
    private fun askGemini(c: Config, prompt: String, web: Boolean, files: List<Attachment>): String {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        files.forEach { f ->
            parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", f.mime).put("data", Base64.getEncoder().encodeToString(f.bytes))))
        }
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
        if (web) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        val conn = URL("https://generativelanguage.googleapis.com/v1beta/models/${c.model.id}:generateContent").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 90_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("x-goog-api-key", c.key)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            when (code) {
                in 200..299 -> {}
                429 -> throw IOException(t("Quota gratuit de Gemini atteint pour aujourd'hui."))
                400, 401, 403 -> throw IOException(t("Clé Gemini refusée : vérifiez-la dans les Réglages."))
                else -> throw IOException("Gemini : HTTP $code")
            }
            val out = JSONObject(text).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            return (0 until (out?.length() ?: 0)).joinToString("") { out!!.optJSONObject(it)?.optString("text").orEmpty() }
        } finally {
            conn.disconnect()
        }
    }

    /** Objet JSON de la réponse (le moteur peut écrire du texte autour). */
    private fun json(text: String): JSONObject? {
        val start = text.indexOf('{').takeIf { it >= 0 } ?: return null
        val end = text.lastIndexOf('}').takeIf { it > start } ?: return null
        return runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull()
    }

    suspend fun describe(c: Config, text: String, today: LocalDate, askQuestions: Boolean): TripRequest? {
        val prompt = """
            Tu aides une application de voyage à préparer un séjour. Lis la demande ci-dessous et remplis la fiche.
            Règles :
            - Laisse un champ texte vide ("") ou une liste vide si la demande ne le précise pas ; n'invente rien.
            - Nous sommes le $today (${today.dayOfWeek}). startDate au format AAAA-MM-JJ, toujours dans le futur.
              Traduis les dates relatives : « week-end » = samedi et dimanche prochains (days 2), sauf si la demande dit
              autre chose ; « dimanche » = le prochain dimanche ; « du 4 au 6 décembre » = startDate du 4, days 3.
              Si l'heure de retour tombe un jour précis (« retour dimanche 8h10 »), le séjour se termine ce jour-là.
            - days : nombre de jours du séjour (0 si inconnu) ; halfDay : true pour une demi-journée.
            - arrivalTime / departureTime au format HH:MM (arrivée le premier jour, départ le dernier jour) ; une heure
              approximative se convertit : matin 09:00, midi 12:00, après-midi 15:00, soir 19:00, nuit 22:00.
            - restAfterArrival : true si la personne veut rester à l'hôtel (se reposer) après son arrivée.
            - travellers : nombre de voyageurs s'il est dit ou évident (« seul » = 1, « à deux » = 2), sinon 0.
            - childAges : âges des enfants cités ; stroller : true si une poussette est mentionnée.
            - budgetEur : budget total annoncé en euros (0 sinon) ; maxWalkMin : marche maximale entre deux étapes (0 sinon).
            - company, style, pace, mobility, diet, budget : seulement si la demande le dit clairement, sinon "".
              Ne les déduis jamais du thème ou du ton (« week-end autour du vin » ne dit ni « en couple » ni « incontournables »).
              mobility WALK seulement si la personne veut tout faire à pied ou rester près du centre.
            - company parmi : ${choices(Quiz.Company.entries.toTypedArray())}.
            - interests parmi : ${choices(Quiz.Interest.entries.toTypedArray())}.
            - tastes parmi : ${Quiz.Taste.entries.joinToString(", ") { "${it.name} (${it.label})" }}.
            - style parmi : ${choices(Quiz.Style.entries.toTypedArray())} ; pace parmi : ${choices(Quiz.Pace.entries.toTypedArray())}.
            - mobility parmi : ${choices(Quiz.Mobility.entries.toTypedArray())} ; diet parmi : ${choices(Quiz.Diet.entries.toTypedArray())} ;
              budget parmi : ${choices(Quiz.Budget.entries.toTypedArray())}.
            - wanted : lieux cités explicitement (ex. « Louvre ») ; avoid : mots des lieux ou cuisines à éviter (ex. « pizza »).
            - country : pays de la ville en français (ex. « Italie » pour Florence ; précise si la ville est ambiguë).
            - warnings : courtes remarques en $language, fondées uniquement sur cette demande, sur ce qui risque de ne pas
              coller (saison : marchés de Noël en général ouverts de fin novembre au 24 décembre ; fermetures connues).
              N'invente aucun chiffre ni élément que la demande ne contient pas ; sinon liste vide.
            - question : ${if (askQuestions) "si la ville ou les dates manquent ou sont ambiguës, UNE question courte en $language pour les préciser ; sinon \"\"" else "\"\""}.
            Réponds uniquement par un objet JSON avec les clés : city, startDate, days, halfDay, company, interests, tastes,
            style, pace, mobility, diet, budget, arrivalTime, departureTime, restAfterArrival, travellers, childAges, stroller,
            budgetEur, maxWalkMin, wanted, avoid, warnings, question, country.

            Demande : $text
        """.trimIndent()
        return json(ask(c, prompt).also { Diagnostics.requestResponse = it })?.let(::TripRequest)
    }

    /** Résumé en une phrase des avis de chaque lieu ([id], nom, avis) → id → résumé. */
    suspend fun summarize(c: Config, places: List<Triple<String, String, List<String>>>): Map<String, String> {
        val input = places.joinToString("\n\n") { (id, name, reviews) -> "id=$id · $name\n" + reviews.joinToString("\n") { "- $it" } }
        val o = json(ask(c, "Pour chaque lieu, résume ses avis de visiteurs en une phrase courte et utile (moins de 20 mots, " +
            "en $language), sans inventer et sans aucun nom de personne (serveur, employé, client). " +
            "Réponds uniquement par un objet JSON {\"id du lieu\": \"résumé\", …} avec les mêmes id.\n\n$input"))
            ?: return emptyMap()
        return o.keys().asSequence().associateWith { o.optString(it) }.filterValues { it.isNotBlank() }
    }

    /** Vérifie par recherche web un lieu que Google ne connaît pas : fermé ? horaires ce jour-là ? */
    suspend fun webCheck(c: Config, name: String, city: String, date: LocalDate?): PlaceCheck? {
        val text = ask(c, "Le lieu « $name » à $city est-il encore ouvert au public" + (date?.let { ", et quels sont ses horaires le $it" } ?: "") +
            " ? Cherche sur le web, puis réponds uniquement par une ligne JSON : " +
            "{\"closedForGood\": true ou false, \"closedThatDay\": true ou false, \"hours\": \"horaires ce jour-là ou vide\"}", web = true)
        val o = Regex("\\{[^{}]*\\}").findAll(text).lastOrNull()?.value?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val hours = o.optString("hours").ifBlank { null }
        return PlaceCheck(o.optBoolean("closedForGood"), hours, o.optBoolean("closedThatDay"), null, emptyList())
    }

    /** Jours fériés, fermetures exceptionnelles, grèves, événements et marchés sur les dates du séjour. */
    suspend fun checkDates(c: Config, city: String, start: LocalDate, end: LocalDate, known: List<String>, places: List<String>): List<String> =
        json(ask(c, "Pour un séjour à $city du $start au $end : cherche les jours fériés, fermetures exceptionnelles de grands lieux, " +
            "grèves annoncées, festivals et marchés saisonniers qui tombent pendant le séjour, dans la ville même. Ne garde que des informations " +
            "précises et confirmées (nom de l'événement et dates exactes) ; rien de vague ni d'incertain, rien qui ne touche pas le séjour. " +
            "Si un événement occupe un de ces lieux du programme, nomme le lieu : ${places.joinToString(", ")}. " +
            "Une seule remarque par événement. Déjà signalé, à ne pas répéter : " +
            known.joinToString(" | ").ifBlank { "rien" } + ". Réponds uniquement par un JSON " +
            "{\"notes\": [\"remarque courte et utile en $language\", …]} (3 au plus, liste vide si rien de notable).", web = true))
            ?.texts("notes").orEmpty()

    /** Balades de quartier et excursions à la journée, pour les jours où la ville est épuisée. */
    suspend fun outings(c: Config, city: String, count: Int, seen: List<String>, wine: Boolean = false): List<String> =
        json(ask(c, "Séjour à $city : les grands sites suivants sont déjà au programme : ${seen.joinToString(", ")}. " +
            (if (wine) "Le séjour est consacré au vin : la première idée est une excursion à la journée dans un vignoble réel et proche " +
                "(visite de domaine ou de château avec dégustation). " else "") + "Propose $count idées " +
            "pour les jours restants, en $language : des balades dans des quartiers réels (« Balade : nom du quartier — ce qu'on y voit ») et " +
            "des excursions à la journée vers des lieux réels et connus (« Excursion : lieu (distance en km, transport) — intérêt »). " +
            "N'invente rien. Réponds uniquement par un JSON {\"ideas\": [\"…\"]}."))?.texts("ideas").orEmpty()

    /**
     * Conseils par étape ([id] → description) : astuce, accessibilité, restaurant qui ne colle pas aux [constraints].
     * Renvoie id → conseils (seulement les étapes concernées).
     */
    suspend fun advise(c: Config, city: String, steps: List<Pair<String, String>>, constraints: String,
                       tips: Boolean, access: Boolean, food: Boolean, web: Boolean): Map<String, StepAdvice> {
        val asks = listOfNotNull(
            "\"tip\": un conseil pratique court, cohérent avec le jour et l'heure prévus, sans parler des horaires d'ouverture (l'appli les vérifie), ou vide".takeIf { tips },
            "\"passage\": true si le lieu se voit seulement de l'extérieur (pas de visite intérieure)".takeIf { tips },
            "\"notThen\": raison courte si le lieu ne se visite pas à ce jour et cette heure (jours de visite, réservation, office religieux), sinon vide".takeIf { tips },
            "\"minutes\": durée de visite habituelle en minutes si elle diffère nettement de celle prévue, sinon 0".takeIf { tips },
            "\"access\": problème d'accès poussette / fauteuil (escaliers, pavés) ou vide".takeIf { access },
            "\"unsuitable\": pour un restaurant seulement, raison courte s'il ne respecte pas les contraintes, sinon vide".takeIf { food },
        )
        val o = json(ask(c, "Séjour à $city. Contraintes de la personne : ${constraints.ifBlank { "aucune" }}.\n" +
            "Pour chaque étape ci-dessous, donne en $language : ${asks.joinToString(" ; ")}. Ne mets que des informations fiables.\n" +
            "Réponds uniquement par un JSON {\"id\": {\"tip\": \"\", \"passage\": false, \"notThen\": \"\", \"minutes\": 0, \"access\": \"\", \"unsuitable\": \"\"}, …} avec les mêmes id.\n\n" +
            steps.joinToString("\n") { (id, d) -> "id=$id · $d" }, web = web)) ?: return emptyMap()
        return o.keys().asSequence().mapNotNull { id ->
            val v = o.optJSONObject(id) ?: return@mapNotNull null
            fun f(k: String) = v.optString(k).takeIf { it.isNotBlank() && it != "null" }
            val adv = StepAdvice(f("tip").takeIf { tips }, f("access").takeIf { access }, f("unsuitable").takeIf { food }, tips && v.optBoolean("passage"),
                f("notThen").takeIf { tips }, if (tips) v.optInt("minutes").coerceIn(0, 300) else 0)
            if (adv.tip == null && adv.access == null && adv.unsuitable == null && !adv.passage && adv.notThen == null && adv.minutes == 0) null else id to adv
        }.toMap()
    }

    /** Modification demandée en parlant ([program] : les étapes numérotées), à appliquer par l'appli. */
    suspend fun edit(c: Config, request: String, program: String, answers: String, now: String): EditPlan? {
        val o = json(ask(c, """
            Tu modifies un programme de voyage existant à la demande de la personne. Il est $now.
            Réglages actuels : $answers
            Programme (jour/étape : heure, moment, lieu) :
            $program

            Demande : $request

            Réponds uniquement par un JSON :
            {"summary": "ce que tu as changé, en une phrase en $language",
             "answers": {clés à changer seulement : "pace" (${choices(Quiz.Pace.entries.toTypedArray())}), "style", "mobility",
                         "diet", "budget", "start" (${choices(Quiz.Start.entries.toTypedArray())}), "interests", "tastes",
                         "avoid" (mots à éviter en plus), "wanted" (lieux à voir en plus), "maxWalkMin"} ou {},
             "ops": [ {"op": "time", "day": 1, "step": 2, "time": "14:00"},
                      {"op": "shift", "day": 1, "step": 2, "minutes": 60},
                      {"op": "remove", "day": 1, "step": 3},
                      {"op": "replace", "day": 1, "step": 3, "prefer": "indoor | outdoor | nearby | any"},
                      {"op": "add", "day": 1, "name": "nom de l'étape"} ] }
            Les jours et étapes sont numérotés à partir de 1, comme dans le programme. Classe les opérations d'un même jour
            de la dernière étape à la première. Ne change que ce qui est demandé ; « il pleut » : remplace les étapes de plein
            air restantes par "indoor" ; « retard » : décale les étapes suivantes (shift).
        """.trimIndent())) ?: return null
        val ops = o.optJSONArray("ops")?.let { a -> List(a.length()) { a.optJSONObject(it) }.filterNotNull() }.orEmpty()
        return EditPlan(o.optString("summary"), o.optJSONObject("answers")?.takeIf { it.length() > 0 }, ops)
    }

    /** Lit des billets ou confirmations (texte, PDF, images) : trajets et hôtel. */
    suspend fun readBooking(c: Config, text: String?, files: List<Attachment>): Booking? {
        val o = json(ask(c, """
            Lis ces confirmations de réservation (avion, train, hôtel) et réponds uniquement par un JSON :
            {"segments": [{"fromCity": "ville de départ en français", "toCity": "ville d'arrivée en français", "from": "code ou gare",
              "to": "code ou gare", "depDate": "AAAA-MM-JJ", "depTime": "HH:MM", "arrDate": "AAAA-MM-JJ", "arrTime": "HH:MM",
              "number": "compagnie et numéro"}],
             "passengers": nombre, "price": montant total des trajets ou 0, "currency": "EUR", "reference": "référence de réservation",
             "hotel": {"name": "", "city": "ville en français", "checkIn": "AAAA-MM-JJ", "checkOut": "AAAA-MM-JJ", "price": 0,
                       "currency": "EUR", "reference": ""} ou null}
            Heures locales, trajets dans l'ordre chronologique (escales comprises), liste vide s'il n'y a pas de trajet.
            N'invente rien : champ vide si absent.
        """.trimIndent() + (text?.let { "\n\nTexte :\n${it.take(20_000)}" } ?: ""), files = files)) ?: return null
        val segs = o.optJSONArray("segments")?.let { a ->
            List(a.length()) { i ->
                val s = a.optJSONObject(i) ?: return@List null
                fun date(k: String) = runCatching { LocalDate.parse(s.optString(k)) }.getOrNull()
                Booking.Segment(s.optString("fromCity"), s.optString("toCity"), s.optString("from"), s.optString("to"),
                    date("depDate"), minutesOf(s.optString("depTime")), date("arrDate"), minutesOf(s.optString("arrTime")), s.optString("number"))
            }.filterNotNull().filter { it.toCity.isNotBlank() }
        }.orEmpty()
        val hotel = o.optJSONObject("hotel")?.takeIf { it.optString("name").isNotBlank() }?.let { h ->
            fun date(k: String) = runCatching { LocalDate.parse(h.optString(k)) }.getOrNull()
            Booking.Hotel(h.optString("name"), h.optString("city"), date("checkIn"), date("checkOut"),
                h.optDouble("price").takeIf { !it.isNaN() && it > 0 }, h.optString("currency").ifBlank { "EUR" }, h.optString("reference"))
        }
        if (segs.isEmpty() && hotel == null) return null
        return Booking(segs, o.optInt("passengers", 1).coerceAtLeast(1), o.optDouble("price").takeIf { !it.isNaN() && it > 0 },
            o.optString("currency").ifBlank { "EUR" }, o.optString("reference"), hotel)
    }

    /** Commentaire du budget, avec pistes d'économies. */
    suspend fun budgetAdvice(c: Config, summary: String): String =
        ask(c, "Voici le budget estimé d'un séjour :\n$summary\nEn $language, en 4 phrases au plus : dis si c'est cohérent avec le budget " +
            "annoncé (s'il y en a un), quel poste pèse le plus, et deux pistes d'économies concrètes. Pas de titre, pas de liste.").trim()

    /** Carnet : introduction, un texte par jour, phrases utiles sur mesure. */
    suspend fun story(c: Config, summary: String, days: Int, localLanguage: String, needs: String, who: String?): Story? {
        val o = json(ask(c, "Voici un programme de voyage :\n$summary\n\nVoyageurs : ${who ?: "non précisés : n'écris ni « nous » ni « je », " +
            "tourne les phrases sans dire qui voyage"}.\nEn $language, écris pour le carnet de voyage : une introduction " +
            "chaleureuse de 2 phrases, puis pour chacun des $days jours une phrase qui donne envie. Ajoute 3 phrases utiles en $localLanguage " +
            "adaptées à la personne (${needs.ifBlank { "voyageur sans besoin particulier" }}). Réponds uniquement par un JSON " +
            "{\"intro\": \"\", \"days\": [\"\"], \"phrases\": [{\"meaning\": \"sens en $language\", \"local\": \"phrase en $localLanguage\"}]}")) ?: return null
        val phrases = o.optJSONArray("phrases")?.let { a ->
            List(a.length()) { i -> a.optJSONObject(i)?.let { it.optString("meaning") to it.optString("local") } }.filterNotNull()
        }.orEmpty().filter { it.first.isNotBlank() && it.second.isNotBlank() }
        return Story(o.optString("intro"), o.texts("days"), phrases)
    }

    /** Souvenir à partager après le voyage. */
    suspend fun souvenir(c: Config, summary: String): String =
        ask(c, "Voici le programme et les dépenses d'un voyage terminé :\n$summary\nEn $language, écris un petit souvenir de " +
            "5 à 7 phrases à partager avec ses proches, vivant et personnel, sans inventer d'événement. Pas de titre.").trim()

    /**
     * Fiche d'Envolée AI → réponses du questionnaire, en partant de [base] (page vierge, ou préférences, ou voyage
     * relié) : rien d'une demande précédente ne s'y mélange. Renvoie aussi ce qui n'a pas été compris.
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
        if (r.days > Quiz.Duration.MAX_DAYS) ignored += tf("{0} jours (au plus {1})", r.days, Quiz.Duration.MAX_DAYS)
        val days = r.days.coerceIn(0, Quiz.Duration.MAX_DAYS)
        val start = r.startDate.takeIf { it.isNotBlank() }?.let { d ->
            runCatching { LocalDate.parse(d.trim()) }.getOrNull()?.takeIf { !it.isBefore(LocalDate.now()) }
                ?: null.also { ignored += tf("date « {0} »", d) }
        }
        fun time(v: String, what: String) = v.takeIf { it.isNotBlank() }?.let { minutesOf(it) ?: null.also { ignored += "$what « $v »" } }
        val travellers = r.travellers.takeIf { it > 0 }
        val company = when {
            r.childAges.isNotEmpty() -> Quiz.Company.FAMILY
            r.company.isNotBlank() -> pick(Quiz.Company.entries.toTypedArray(), r.company, base.company, t("compagnie"))
            travellers == 1 -> Quiz.Company.SOLO
            travellers == 2 -> Quiz.Company.COUPLE
            travellers != null -> Quiz.Company.FRIENDS
            else -> base.company
        }
        // Réglage ni dit ni repris (préférences, voyage relié) : valeur par défaut, signalée comme supposée.
        val blank = Quiz.Answers()
        val assumed = buildSet {
            if (company == blank.company && base.travellers == null && travellers == null && r.company.isBlank()) add("company")
            if (r.style.isBlank() && base.style == blank.style) add("style")
            if (r.pace.isBlank() && base.pace == blank.pace) add("pace")
            if (r.mobility.isBlank() && base.mobility == blank.mobility && r.maxWalkMin <= 0) add("mobility")
            if (r.budget.isBlank() && base.budget == blank.budget && r.budgetEur <= 0) add("budget")
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
            travellers = travellers ?: base.travellers,
            interests = (pickAll(Quiz.Interest.entries.toTypedArray(), r.interests, t("envie")) +
                pickAll(Quiz.Taste.entries.toTypedArray(), r.tastes, t("goût")).map { it.interest }).ifEmpty { base.interests },
            tastes = pickAll(Quiz.Taste.entries.toTypedArray(), r.tastes, t("goût")),
            style = pick(Quiz.Style.entries.toTypedArray(), r.style, base.style, t("style")),
            pace = pick(Quiz.Pace.entries.toTypedArray(), r.pace, base.pace, t("rythme")),
            mobility = pick(Quiz.Mobility.entries.toTypedArray(), r.mobility, base.mobility, t("déplacements")),
            diet = pick(Quiz.Diet.entries.toTypedArray(), r.diet, base.diet, t("régime")),
            budget = pick(Quiz.Budget.entries.toTypedArray(), r.budget, base.budget, t("budget")),
            arrivalMin = time(r.arrivalTime, t("arrivée")) ?: base.arrivalMin,
            departureMin = time(r.departureTime, t("départ")) ?: base.departureMin,
            wanted = r.wanted,
            avoidNames = r.avoid,
            restAfterArrival = r.restAfterArrival,
            youngKids = r.childAges.any { it < 6 } || base.youngKids,
            stroller = r.stroller || base.stroller,
            budgetEur = r.budgetEur.takeIf { it > 0 } ?: base.budgetEur,
            maxWalkMin = r.maxWalkMin.takeIf { it > 0 } ?: base.maxWalkMin,
            extraNotes = r.warnings,
            country = r.country.trim().ifBlank { base.country },
            assumed = assumed,
        )
        return answers to ignored
    }

    /** Changement de réglages demandé en parlant : seules les clés présentes changent. */
    fun patch(o: JSONObject, a: Quiz.Answers): Quiz.Answers {
        fun <E : Enum<E>> pick(values: Array<E>, k: String, d: E) = o.optString(k).takeIf { it.isNotBlank() }
            ?.let { n -> values.firstOrNull { it.name == n.trim().uppercase() } } ?: d
        fun <E : Enum<E>> pickAll(values: Array<E>, k: String, d: Set<E>) = if (!o.has(k)) d
            else o.texts(k).mapNotNull { n -> values.firstOrNull { it.name == n.trim().uppercase() } }.toSet().ifEmpty { d }
        return a.copy(
            pace = pick(Quiz.Pace.entries.toTypedArray(), "pace", a.pace),
            style = pick(Quiz.Style.entries.toTypedArray(), "style", a.style),
            mobility = pick(Quiz.Mobility.entries.toTypedArray(), "mobility", a.mobility),
            diet = pick(Quiz.Diet.entries.toTypedArray(), "diet", a.diet),
            budget = pick(Quiz.Budget.entries.toTypedArray(), "budget", a.budget),
            start = pick(Quiz.Start.entries.toTypedArray(), "start", a.start),
            interests = pickAll(Quiz.Interest.entries.toTypedArray(), "interests", a.interests),
            tastes = if (o.has("tastes")) pickAll(Quiz.Taste.entries.toTypedArray(), "tastes", emptySet()) else a.tastes,
            avoidNames = a.avoidNames + o.texts("avoid"),
            wanted = a.wanted + o.texts("wanted"),
            maxWalkMin = o.optInt("maxWalkMin").takeIf { it > 0 } ?: a.maxWalkMin,
        )
    }
}
