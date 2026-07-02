package com.guardian.app.walls.wall2.util

object DomainNormalizer {
    fun normalize(url: String): String? {
        var cleaned = url.trim().lowercase()

        if (!cleaned.contains(".")) return null

        cleaned = cleaned.removePrefix("https://")
        cleaned = cleaned.removePrefix("http://")
        cleaned = cleaned.removePrefix("www.")
        cleaned = cleaned.removePrefix("m.")

        val slashIndex = cleaned.indexOf('/')
        if (slashIndex > 0) cleaned = cleaned.substring(0, slashIndex)

        val queryIndex = cleaned.indexOf('?')
        if (queryIndex > 0) cleaned = cleaned.substring(0, queryIndex)

        if (!cleaned.contains(".")) return null

        return cleaned
    }
}
