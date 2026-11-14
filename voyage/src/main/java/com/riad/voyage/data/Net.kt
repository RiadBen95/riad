package com.riad.voyage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Réponse HTTP en erreur : code et corps (pour un message adapté à chaque service). */
class HttpError(val code: Int, val body: String) : IOException("HTTP $code")

/** Client réseau unique de l'app (HTTPS, délais, en-têtes communs). */
object Net {
    const val UA = "Envolee/1.0 (Android app; https://github.com/RiadBen95/riad)"

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    /** GET, ou POST si [body] est fourni. Lève [HttpError] si le serveur répond en erreur. */
    suspend fun fetch(
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        contentType: String = "application/json",
        connectMs: Int = 8_000,
        readMs: Int = 20_000,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = connectMs
            conn.readTimeout = readMs
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", UA)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = conn.responseCode
            if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() }
            else throw HttpError(code, conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            conn.disconnect()
        }
    }
}

private val ACCENTS = Regex("\\p{Mn}+")

/** Texte comparable : minuscules, sans accents ni espaces autour (« Élysée » → « elysee »). */
fun String.plain(): String = java.text.Normalizer.normalize(trim().lowercase(), java.text.Normalizer.Form.NFD).replace(ACCENTS, "")
