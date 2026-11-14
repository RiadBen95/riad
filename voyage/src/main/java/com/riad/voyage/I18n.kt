package com.riad.voyage

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import java.util.Locale

/** Langues de l'application. Le français sert de texte source (et de clé de traduction). */
enum class Lang(val code: String, val nativeName: String) {
    FR("fr", "Français"),
    EN("en", "English"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(code)

    companion object {
        fun of(code: String?) = entries.firstOrNull { it.code == code }
    }
}

/**
 * Traductions : chaque texte français de l'app est la clé ; les traductions viennent de
 * res/raw/translations.tsv (fr, en). Un texte non traduit reste en français.
 */
object I18n {
    /** Langue affichée ; c'est un état Compose : changer de langue redessine l'app. */
    var lang by mutableStateOf(Lang.FR)
        private set

    /** Choix enregistré : null = langue du téléphone. */
    var chosen: Lang? = null
        private set

    private var table: Map<String, Array<String>> = emptyMap()

    fun init(context: Context) {
        if (table.isEmpty()) {
            table = runCatching {
                context.resources.openRawResource(R.raw.translations).bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() && !it.startsWith("#") }
                        .map { it.split('\t') }
                        .filter { it.size >= 2 }
                        .associate { cols -> cols[0] to Array(Lang.entries.size) { i -> cols.getOrNull(i)?.ifBlank { null } ?: cols[0] } }
                }
            }.getOrDefault(emptyMap())
        }
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        chosen = Lang.of(prefs.getString("lang", null))
        lang = chosen ?: systemLang()
    }

    fun systemLang(): Lang {
        val code = Locale.getDefault().language
        return Lang.of(code) ?: Lang.EN
    }

    fun choose(context: Context, l: Lang?) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("lang", l?.code).apply()
        chosen = l
        lang = l ?: systemLang()
    }

    /** Pour les tests : charge une table sans contexte Android. */
    fun loadForTest(tsv: String, l: Lang) {
        table = tsv.lines().filter { it.isNotBlank() }.map { it.split('\t') }
            .associate { cols -> cols[0] to Array(Lang.entries.size) { i -> cols.getOrNull(i)?.ifBlank { null } ?: cols[0] } }
        lang = l
    }

    fun translate(fr: String): String {
        if (lang == Lang.FR) return fr
        return table[fr]?.get(lang.ordinal) ?: fr
    }

    /** Langue utilisable pour Wikipédia, Wikivoyage et les recherches en ligne. */
    val wikiLang get() = lang.code
}

/** Texte traduit dans la langue de l'app. */
fun t(fr: String): String = I18n.translate(fr)

/** Texte traduit avec des valeurs : tf("{0} nuits", 3). */
fun tf(fr: String, vararg args: Any): String {
    var s = I18n.translate(fr)
    args.forEachIndexed { i, a ->
        val v = a.toString()
        // Élision française : « de Alger » → « d'Alger ».
        if (I18n.lang == Lang.FR && v.firstOrNull()?.let { it.lowercaseChar() in "aeiouyhâàéèêîôû" } == true) s = s.replace("de {$i}", "d'{$i}")
        s = s.replace("{$i}", v)
    }
    return s
}

/** Type de lieu composé (« Restaurant · €€ ») : chaque partie est traduite. */
fun tk(kind: String): String = kind.split(" · ").joinToString(" · ") { I18n.translate(it) }

/** Liste « a, b et c » dans la langue de l'app. */
fun tlist(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + t(" et ") + items.last()
}
