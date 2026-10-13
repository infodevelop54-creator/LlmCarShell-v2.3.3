package com.example.llmcar.voice

import java.text.Normalizer
import java.util.Locale

object WakeWordMatcher {

    fun matches(recognized: String, phrase: String): Boolean {
        if (phrase.isBlank()) return false
        val a = normalize(recognized)
        val b = normalize(phrase)
        return b.isNotEmpty() && a.contains(b)
    }

    fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale("ru", "RU"))
            .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}