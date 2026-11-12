package com.riad.voyage.data

object Airlines {
    private val names = mapOf(
        "AF" to "Air France", "AH" to "Air Algérie", "TO" to "Transavia France", "HV" to "Transavia",
        "U2" to "easyJet", "EC" to "easyJet Europe", "DS" to "easyJet Suisse", "FR" to "Ryanair",
        "VY" to "Vueling", "IB" to "Iberia", "LH" to "Lufthansa", "TK" to "Turkish Airlines",
        "PC" to "Pegasus", "EK" to "Emirates", "QR" to "Qatar Airways", "EY" to "Etihad",
        "AT" to "Royal Air Maroc", "TU" to "Tunisair", "BJ" to "Nouvelair", "KL" to "KLM",
        "BA" to "British Airways", "LX" to "Swiss", "AZ" to "ITA Airways", "SN" to "Brussels Airlines",
        "OS" to "Austrian", "TP" to "TAP Portugal", "DL" to "Delta", "AA" to "American Airlines",
        "UA" to "United", "AC" to "Air Canada", "TS" to "Air Transat", "V7" to "Volotea",
        "W6" to "Wizz Air", "W4" to "Wizz Air Malta", "SV" to "Saudia", "MS" to "EgyptAir",
        "XK" to "Air Corsica", "SS" to "Corsair", "BF" to "French Bee", "TX" to "Air Caraïbes",
        "UX" to "Air Europa", "A3" to "Aegean", "LS" to "Jet2", "SK" to "SAS", "AY" to "Finnair",
        "ET" to "Ethiopian", "RJ" to "Royal Jordanian", "ME" to "Middle East Airlines",
        "GF" to "Gulf Air", "WY" to "Oman Air", "SQ" to "Singapore Airlines", "CX" to "Cathay Pacific",
        "JL" to "Japan Airlines", "NH" to "ANA", "5O" to "ASL Airlines France", "IJ" to "Spring Japan",
    )

    fun name(code: String): String = names[code] ?: code.ifBlank { "Compagnie" }
}
