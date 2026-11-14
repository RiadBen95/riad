package com.riad.voyage.data

/** Usage du pourboire, du plus discret au plus attendu. */
enum class Tipping(val label: String) {
    NONE("Pas de pourboire : il peut même vexer"),
    INCLUDED("Service compris ; on laisse la petite monnaie si on est satisfait"),
    ROUND("On arrondit ou on laisse 5 à 10 %"),
    PCT10("Environ 10 % au restaurant"),
    PCT18("15 à 20 % attendus au restaurant"),
}

/**
 * Infos pratiques d'un pays : devise, prises électriques, pourboire, numéros d'urgence, langue.
 * [police], [ambulance], [fire] : numéros nationaux ; [general] : numéro unique depuis un mobile.
 */
data class CountryInfo(
    val currency: String,
    val plugs: String,
    val volts: Int,
    val tipping: Tipping,
    val general: String,
    val police: String,
    val ambulance: String,
    val fire: String,
    val lang: String,
) {
    /** Prises C, E et F (françaises) absentes : adaptateur nécessaire depuis la France. */
    val needsAdapter get() = plugs.split(",").map { it.trim() }.none { it in setOf("C", "E", "F") }
}

/** Phrase utile : sens (en français, traduit dans l'app) et version locale (avec prononciation). */
data class Phrase(val meaning: String, val local: String)

object Practical {
    private fun c(cur: String, plugs: String, v: Int, tip: Tipping, gen: String, pol: String, amb: String, fire: String, lang: String) =
        CountryInfo(cur, plugs, v, tip, gen, pol, amb, fire, lang)

    private val eu112 = "112"

    private val countries: Map<String, CountryInfo> = buildMap {
        fun put(info: CountryInfo, vararg names: String) = names.forEach { put(it.plain(), info) }
        put(c("EUR", "C, E", 230, Tipping.INCLUDED, eu112, "17", "15", "18", "fr"), "France")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "091", "061", "080", "es"), "Espagne", "Spain", "España")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "112", "112", "112", "pt"), "Portugal")
        put(c("EUR", "C, F, L", 230, Tipping.INCLUDED, eu112, "113", "118", "115", "it"), "Italie", "Italy", "Italia")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "110", "112", "112", "de"), "Allemagne", "Germany", "Deutschland")
        put(c("GBP", "G", 230, Tipping.PCT10, "999", "999", "999", "999", "en"),
            "Royaume-Uni", "United Kingdom", "Angleterre", "England", "Écosse", "Scotland")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "112", "112", "112", "nl"), "Pays-Bas", "Netherlands", "Nederland")
        put(c("EUR", "C, E", 230, Tipping.INCLUDED, eu112, "101", "112", "112", "fr"), "Belgique", "Belgium")
        put(c("CHF", "C, J", 230, Tipping.INCLUDED, eu112, "117", "144", "118", "de"), "Suisse", "Switzerland")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "133", "144", "122", "de"), "Autriche", "Austria")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "100", "166", "199", "el"), "Grèce", "Greece")
        put(c("CZK", "C, E", 230, Tipping.PCT10, eu112, "158", "155", "150", "cs"), "Tchéquie", "République tchèque", "Czechia")
        put(c("PLN", "C, E", 230, Tipping.PCT10, eu112, "997", "999", "998", "pl"), "Pologne", "Poland")
        put(c("HUF", "C, F", 230, Tipping.PCT10, eu112, "107", "104", "105", "hu"), "Hongrie", "Hungary")
        put(c("EUR", "G", 230, Tipping.PCT10, eu112, "112", "112", "112", "en"), "Irlande", "Ireland")
        put(c("SEK", "C, F", 230, Tipping.ROUND, eu112, "112", "112", "112", "sv"), "Suède", "Sweden")
        put(c("NOK", "C, F", 230, Tipping.ROUND, eu112, "112", "113", "110", "no"), "Norvège", "Norway")
        put(c("DKK", "C, E, F, K", 230, Tipping.INCLUDED, eu112, "112", "112", "112", "da"), "Danemark", "Denmark")
        put(c("EUR", "C, F", 230, Tipping.ROUND, eu112, "192", "194", "193", "hr"), "Croatie", "Croatia")
        put(c("TRY", "C, F", 230, Tipping.PCT10, eu112, "112", "112", "112", "tr"), "Turquie", "Turkey", "Türkiye")
        put(c("MAD", "C, E", 220, Tipping.PCT10, eu112, "19", "15", "15", "ar"), "Maroc", "Morocco")
        put(c("DZD", "C, F", 230, Tipping.ROUND, "17", "17", "14", "14", "ar"), "Algérie", "Algeria")
        put(c("TND", "C, E", 230, Tipping.ROUND, "197", "197", "190", "198", "ar"), "Tunisie", "Tunisia")
        put(c("EGP", "C, F", 220, Tipping.PCT10, "122", "122", "123", "180", "ar"), "Égypte", "Egypt")
        put(c("AED", "G", 230, Tipping.PCT10, "999", "999", "998", "997", "ar"), "Émirats arabes unis", "United Arab Emirates", "Dubaï")
        put(c("SAR", "G", 230, Tipping.PCT10, "911", "999", "997", "998", "ar"), "Arabie saoudite", "Saudi Arabia")
        put(c("QAR", "G", 240, Tipping.PCT10, "999", "999", "999", "999", "ar"), "Qatar")
        put(c("JPY", "A, B", 100, Tipping.NONE, "110", "110", "119", "119", "ja"), "Japon", "Japan")
        put(c("CNY", "A, C, I", 220, Tipping.NONE, "110", "110", "120", "119", "zh"), "Chine", "China")
        put(c("THB", "A, B, C, O", 220, Tipping.ROUND, "191", "191", "1669", "199", "th"), "Thaïlande", "Thailand")
        put(c("INR", "C, D, M", 230, Tipping.PCT10, "112", "100", "108", "101", "hi"), "Inde", "India")
        put(c("IDR", "C, F", 230, Tipping.ROUND, "112", "110", "118", "113", "id"), "Indonésie", "Indonesia")
        put(c("VND", "A, C", 220, Tipping.ROUND, "113", "113", "115", "114", "vi"), "Vietnam", "Viêt Nam")
        put(c("KRW", "C, F", 220, Tipping.NONE, "112", "112", "119", "119", "ko"), "Corée du Sud", "South Korea")
        put(c("USD", "A, B", 120, Tipping.PCT18, "911", "911", "911", "911", "en"), "États-Unis", "United States", "USA")
        put(c("CAD", "A, B", 120, Tipping.PCT18, "911", "911", "911", "911", "en"), "Canada")
        put(c("MXN", "A, B", 127, Tipping.PCT10, "911", "911", "911", "911", "es"), "Mexique", "Mexico", "México")
        put(c("BRL", "C, N", 220, Tipping.INCLUDED, "190", "190", "192", "193", "pt"), "Brésil", "Brazil", "Brasil")
        put(c("ARS", "C, I", 220, Tipping.PCT10, "911", "911", "107", "100", "es"), "Argentine", "Argentina")
        put(c("AUD", "I", 230, Tipping.ROUND, "000", "000", "000", "000", "en"), "Australie", "Australia")
        put(c("XOF", "C, D, E, K", 230, Tipping.ROUND, "17", "17", "1515", "18", "fr"), "Sénégal", "Senegal")
        put(c("ZAR", "C, D, M, N", 230, Tipping.PCT10, "112", "10111", "10177", "10177", "en"), "Afrique du Sud", "South Africa")
    }

    fun of(country: String): CountryInfo? = countries[country.plain()]

    /** Les 8 phrases, dans l'ordre : bonjour, merci, s'il vous plaît, pardon, combien, où est, l'addition, au secours. */
    val meanings = listOf(
        "Bonjour", "Merci", "S'il vous plaît", "Excusez-moi", "Combien ça coûte ?", "Où est… ?", "L'addition, s'il vous plaît", "Au secours !",
    )

    private val phrasebook: Map<String, List<String>> = mapOf(
        "fr" to listOf("Bonjour", "Merci", "S'il vous plaît", "Excusez-moi", "Combien ça coûte ?", "Où est… ?", "L'addition, s'il vous plaît", "Au secours !"),
        "en" to listOf("Hello", "Thank you", "Please", "Excuse me", "How much is it?", "Where is…?", "The bill, please", "Help!"),
        "es" to listOf("Hola", "Gracias", "Por favor", "Perdón", "¿Cuánto cuesta?", "¿Dónde está…?", "La cuenta, por favor", "¡Socorro!"),
        "pt" to listOf("Olá", "Obrigado / Obrigada", "Por favor", "Desculpe", "Quanto custa?", "Onde fica…?", "A conta, por favor", "Socorro!"),
        "it" to listOf("Buongiorno", "Grazie", "Per favore", "Scusi", "Quanto costa?", "Dov'è…?", "Il conto, per favore", "Aiuto!"),
        "de" to listOf("Guten Tag", "Danke", "Bitte", "Entschuldigung", "Wie viel kostet das?", "Wo ist…?", "Die Rechnung, bitte", "Hilfe!"),
        "nl" to listOf("Hallo", "Dank u wel", "Alstublieft", "Pardon", "Hoeveel kost het?", "Waar is…?", "De rekening, alstublieft", "Help!"),
        "el" to listOf("Γειά σας (Yiá sas)", "Ευχαριστώ (Efcharistó)", "Παρακαλώ (Parakaló)", "Συγγνώμη (Signómi)",
            "Πόσο κάνει; (Póso káni ?)", "Πού είναι…; (Poú íne… ?)", "Τον λογαριασμό, παρακαλώ (Ton logariasmó, parakaló)", "Βοήθεια! (Voíthia !)"),
        "cs" to listOf("Dobrý den", "Děkuji", "Prosím", "Promiňte", "Kolik to stojí?", "Kde je…?", "Zaplatím, prosím", "Pomoc!"),
        "pl" to listOf("Dzień dobry", "Dziękuję", "Proszę", "Przepraszam", "Ile to kosztuje?", "Gdzie jest…?", "Poproszę rachunek", "Pomocy!"),
        "hu" to listOf("Jó napot", "Köszönöm", "Kérem", "Elnézést", "Mennyibe kerül?", "Hol van…?", "A számlát, kérem", "Segítség!"),
        "sv" to listOf("Hej", "Tack", "Snälla", "Ursäkta", "Hur mycket kostar det?", "Var ligger…?", "Notan, tack", "Hjälp!"),
        "no" to listOf("Hei", "Takk", "Vær så snill", "Unnskyld", "Hvor mye koster det?", "Hvor er…?", "Regningen, takk", "Hjelp!"),
        "da" to listOf("Hej", "Tak", "Vær så venlig", "Undskyld", "Hvad koster det?", "Hvor er…?", "Regningen, tak", "Hjælp!"),
        "hr" to listOf("Dobar dan", "Hvala", "Molim", "Oprostite", "Koliko košta?", "Gdje je…?", "Račun, molim", "Upomoć!"),
        "tr" to listOf("Merhaba", "Teşekkür ederim", "Lütfen", "Affedersiniz", "Ne kadar?", "… nerede?", "Hesap, lütfen", "İmdat!"),
        "ar" to listOf("مرحبا (Marhaba)", "شكرا (Choukran)", "من فضلك (Min fadlak)", "عذرا (Oudhran)",
            "بكم هذا؟ (Bikam hadha ?)", "أين…؟ (Ayna… ?)", "الحساب من فضلك (Al-hisab min fadlak)", "النجدة! (An-najda !)"),
        "ja" to listOf("こんにちは (Konnichiwa)", "ありがとうございます (Arigatō gozaimasu)", "お願いします (Onegaishimasu)", "すみません (Sumimasen)",
            "いくらですか？ (Ikura desu ka ?)", "…はどこですか？ (… wa doko desu ka ?)", "お会計をお願いします (Okaikei o onegaishimasu)", "助けて！ (Tasukete !)"),
        "zh" to listOf("你好 (Nǐ hǎo)", "谢谢 (Xièxie)", "请 (Qǐng)", "对不起 (Duìbuqǐ)",
            "多少钱？ (Duōshao qián ?)", "…在哪里？ (… zài nǎlǐ ?)", "买单 (Mǎidān)", "救命！ (Jiùmìng !)"),
        "th" to listOf("สวัสดี (Sawatdee)", "ขอบคุณ (Khop khun)", "กรุณา (Karuna)", "ขอโทษ (Kho thot)",
            "เท่าไหร่ (Thao rai ?)", "…อยู่ที่ไหน (… yu thi nai ?)", "เช็คบิล (Chek bin)", "ช่วยด้วย (Chuay duay !)"),
        "hi" to listOf("नमस्ते (Namaste)", "धन्यवाद (Dhanyavaad)", "कृपया (Kripya)", "माफ़ कीजिए (Maaf kijiye)",
            "कितने का है? (Kitne ka hai ?)", "… कहाँ है? (… kahaan hai ?)", "बिल दीजिए (Bill dijiye)", "बचाओ! (Bachao !)"),
        "id" to listOf("Halo", "Terima kasih", "Tolong", "Maaf", "Berapa harganya?", "Di mana…?", "Minta bonnya", "Tolong!"),
        "vi" to listOf("Xin chào", "Cảm ơn", "Làm ơn", "Xin lỗi", "Bao nhiêu tiền?", "… ở đâu?", "Tính tiền", "Cứu tôi với!"),
        "ko" to listOf("안녕하세요 (Annyeonghaseyo)", "감사합니다 (Gamsahamnida)", "주세요 (… juseyo)", "죄송합니다 (Joesonghamnida)",
            "얼마예요? (Eolmayeyo ?)", "… 어디예요? (… eodiyeyo ?)", "계산해 주세요 (Gyesanhae juseyo)", "도와주세요! (Dowajuseyo !)"),
    )

    fun phrases(lang: String): List<Phrase> = phrasebook[lang]?.let { local -> meanings.zip(local) { m, l -> Phrase(m, l) } }.orEmpty()
}
