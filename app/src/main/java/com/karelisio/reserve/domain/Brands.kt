package com.karelisio.reserve.domain

import java.text.Normalizer

/** Brands for which a loyalty card can be declared, with the spellings found in OSM. */
enum class Brand(val label: String, private val aliases: List<String>, private val exact: List<String> = emptyList()) {
    LECLERC("E.Leclerc", listOf("leclerc")),
    TOTAL("TotalEnergies", listOf("total")),
    INTERMARCHE("Intermarché", listOf("intermarche")),
    CARREFOUR("Carrefour", listOf("carrefour")),
    AUCHAN("Auchan", listOf("auchan")),
    SYSTEME_U("Système U", listOf("systeme u", "super u", "hyper u", "u express", "station u", "magasins u"), exact = listOf("u")),
    CASINO("Casino / Géant", listOf("casino", "geant")),
    ESSO("Esso", listOf("esso")),
    BP("BP", emptyList(), exact = listOf("bp")),
    SHELL("Shell", listOf("shell")),
    AVIA("Avia", listOf("avia")),
    NETTO("Netto", listOf("netto")),
    DYNEFF("Dyneff", listOf("dyneff")),
    ENI("Eni / Agip", listOf("eni", "agip"));

    fun matches(stationBrand: String?): Boolean {
        val key = normalize(stationBrand ?: return false)
        return key in exact || aliases.any { alias -> key == alias || key.split(' ', '.', '-').contains(alias) || key.startsWith("$alias ") || key.contains(alias) && alias.length >= 5 }
    }

    companion object {
        fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFD)
                .replace("\\p{Mn}+".toRegex(), "")
                .lowercase()
                .trim()

        fun fromKey(key: String): Brand? = entries.firstOrNull { it.name == key }
    }
}
