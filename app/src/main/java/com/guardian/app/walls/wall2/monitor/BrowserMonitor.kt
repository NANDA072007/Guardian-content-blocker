package com.guardian.app.walls.wall2.monitor

import android.view.accessibility.AccessibilityNodeInfo
import com.guardian.app.walls.wall2.adapter.*
import com.guardian.app.walls.wall2.event.*
import com.guardian.app.walls.wall2.util.DomainNormalizer
import com.guardian.app.walls.wall2.util.TraversalLimits
import dagger.hilt.android.scopes.ServiceScoped
import javax.inject.Inject

@ServiceScoped
class BrowserMonitor @Inject constructor() {

    private val profiles: List<BrowserProfile> = listOf(
        ChromeProfile.create(),
        BraveProfile.create(),
        FirefoxProfile.create(),
        SamsungProfile.create(),
        DuckDuckGoProfile.create(),
        GenericProfile.create()
    )

    private val substringBlockedWords = setOf(
        "pornhub", "xvideos", "xnxx", "xhamster", "redtube", "youporn",
        "tube8", "spankbang", "beeg", "eporner", "youjizz", "tnaflix",
        "brazzers", "bangbros", "naughtyamerica", "realitykings",
        "onlyfans", "stripchat", "chaturbate", "livejasmin",
        "hentai", "rule34", "nhentai", "gelbooru", "danbooru",
        "fakku", "javhd", "motherless", "imagefap", "scrolller",
        "coomer", "fappening", "literotica",
        "adultfriendfinder", "ashleymadison",
        "hqporner", "thumbzilla", "pornpics",
        "keezmovies", "extremetube", "sunporno", "pornhd",
        "digitalplayground", "twistys", "teamskeet", "fakehub",
        "faketaxi", "evilangel", "julesjordan", "adulttime",
        "puremature", "fansly", "manyvids", "loyalfans",
        "bongacams", "camsoda", "streamate", "jerkmate",
        "omegle", "chatroulette", "fapello",
        "nudevista", "sxyprn", "daftsex",
        "desiporn", "masalaseen", "indianporn",
        "cam4", "flirt4free", "benaughty", "megapersonal",
        "blowjob", "handjob", "creampie", "threesome", "bondage",
        "fetish", "escort", "erotic", "vibrator", "masturbat",
        "lingerie", "shemale", "tranny",
        "ahegao", "futanari", "lolicon", "shotacon",
        "orgasm", "squirting", "cunnilingus", "fellatio",
        "pornographic", "pornography",
        "pornhup", "xvedios", "xhamstar", "pornhud", "porndude"
    )

    private val wordBoundaryBlockedWords = setOf(
        "sex", "porn", "xxx", "nude", "naked", "nsfw",
        "milf", "anal", "jav", "bokep", "fuck", "slut",
        "whore", "dick", "cock", "puss", "cum", "gay",
        "lesbian", "semen", "porno", "pr0n", "p0rn",
        "henti", "phub", "nood", "horny", "thot"
    )

    fun canHandle(pkg: String): Boolean = profiles.any { pkg in it.packageNames }

    fun process(rootNode: AccessibilityNodeInfo, pkg: String): ProtectionEvent? {
        val profile = profiles.firstOrNull { pkg in it.packageNames } ?: return null

        val url = extractUrl(rootNode, profile)
        if (url != null) {
            val domain = DomainNormalizer.normalize(url)
            return BrowserEvent(
                sessionId = java.util.UUID.randomUUID().toString(),
                metadata = BrowserMetadata(
                    url = url,
                    domain = domain,
                    browserPackage = pkg,
                    extractionMethod = profile.extractionStrategies.firstOrNull()?.name ?: "unknown"
                )
            )
        }

        if (pageContainsBlockedText(rootNode)) {
            return BrowserEvent(
                sessionId = java.util.UUID.randomUUID().toString(),
                metadata = BrowserMetadata(
                    url = null,
                    domain = null,
                    browserPackage = pkg,
                    extractionMethod = "page_text_scan"
                )
            )
        }

        if (isBlanketSuspicious(rootNode)) {
            return BrowserEvent(
                sessionId = java.util.UUID.randomUUID().toString(),
                metadata = BrowserMetadata(
                    url = null,
                    domain = null,
                    browserPackage = pkg,
                    extractionMethod = "browser_blanket"
                )
            )
        }

        return null
    }

    fun processText(text: String, pkg: String): ProtectionEvent? {
        val normalized = normalizeLeetspeak(text)
        val textsToCheck = if (normalized != text) listOf(text, normalized) else listOf(text)

        for (variant in textsToCheck) {
            for (word in substringBlockedWords) {
                if (variant.contains(word)) {
                    return BrowserEvent(
                        sessionId = java.util.UUID.randomUUID().toString(),
                        metadata = BrowserMetadata(
                            url = text,
                            domain = null,
                            browserPackage = pkg,
                            extractionMethod = "text_input_substring"
                        )
                    )
                }
            }
            val tokens = variant.split("\\s+".toRegex())
            for (token in tokens) {
                if (token.length < 2) continue
                if (token in wordBoundaryBlockedWords || token in substringBlockedWords) {
                    return BrowserEvent(
                        sessionId = java.util.UUID.randomUUID().toString(),
                        metadata = BrowserMetadata(
                            url = text,
                            domain = null,
                            browserPackage = pkg,
                            extractionMethod = "text_input_word"
                        )
                    )
                }
            }
        }

        return null
    }

    private fun extractUrl(rootNode: AccessibilityNodeInfo, profile: BrowserProfile): String? {
        for (strategy in profile.extractionStrategies) {
            val url = when (strategy) {
                ExtractionStrategy.RESOURCE_ID -> extractByResourceId(rootNode, profile)
                ExtractionStrategy.HINT_ATTRIBUTE -> extractByHintAttribute(rootNode)
                ExtractionStrategy.TEXT_PATTERN -> extractByTextPattern(rootNode)
                ExtractionStrategy.HEURISTIC -> extractByHeuristic(rootNode)
            }
            if (url != null) return url
        }
        return null
    }

    private fun extractByResourceId(rootNode: AccessibilityNodeInfo, profile: BrowserProfile): String? {
        for ((_, resId) in profile.urlBarResourceIds) {
            try {
                val urlNode = rootNode.findAccessibilityNodeInfosByViewId(resId)
                if (urlNode != null && urlNode.isNotEmpty()) {
                    val text = urlNode[0].text?.toString()
                    if (text != null && text.isNotEmpty()) return text
                    val desc = urlNode[0].contentDescription?.toString()
                    if (desc != null && desc.isNotEmpty()) return desc
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private fun extractByHintAttribute(rootNode: AccessibilityNodeInfo): String? {
        return findUrlNodeText(rootNode, 0) { text ->
            text.contains("http") || text.contains("www.") || text.contains(".com") || text.contains(".org")
        }
    }

    private fun extractByTextPattern(rootNode: AccessibilityNodeInfo): String? {
        return findUrlNodeText(rootNode, 0) { text ->
            Regex("https?://[\\w.-]+\\.[a-zA-Z]{2,}").containsMatchIn(text) ||
                    Regex("[\\w.-]+\\.(com|org|net|io|co\\.\\w{2})").containsMatchIn(text)
        }
    }

    private fun extractByHeuristic(rootNode: AccessibilityNodeInfo): String? {
        return findUrlNodeText(rootNode, 0) { text ->
            text.length > 5 && text.length < 200 &&
                    text.contains(".") && !text.contains(" ")
        }
    }

    private fun findUrlNodeText(node: AccessibilityNodeInfo?, depth: Int, predicate: (String) -> Boolean): String? {
        if (node == null || depth > TraversalLimits.MAX_DEPTH) return null
        try {
            val text = node.text?.toString()
            if (text != null && predicate(text)) return text
            val desc = node.contentDescription?.toString()
            if (desc != null && predicate(desc)) return desc
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findUrlNodeText(child, depth + 1, predicate)
                    if (result != null) return result
                    child.recycle()
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private fun pageContainsBlockedText(rootNode: AccessibilityNodeInfo): Boolean {
        return nodeContainsBlockedWord(rootNode, 0)
    }

    private fun isBlanketSuspicious(rootNode: AccessibilityNodeInfo): Boolean {
        var childCount = 0
        var textNodeCount = 0
        try {
            countTextChildren(rootNode, 0, TraversalLimits.MAX_DEPTH, { childCount++ }, { textNodeCount++ })
        } catch (_: Exception) {}
        return childCount >= 10 && textNodeCount <= 2
    }

    private fun countTextChildren(
        node: AccessibilityNodeInfo?, depth: Int, maxDepth: Int,
        onNode: () -> Unit, onTextNode: () -> Unit
    ) {
        if (node == null || depth > maxDepth) return
        try {
            onNode()
            val text = node.text?.toString()
            if (!text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()) {
                onTextNode()
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    countTextChildren(child, depth + 1, maxDepth, onNode, onTextNode)
                    child.recycle()
                }
            }
        } catch (_: Exception) {}
    }

    private fun nodeContainsBlockedWord(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > TraversalLimits.MAX_DEPTH) return false
        try {
            val text = node.text?.toString()?.lowercase()
            if (text != null) {
                for (word in substringBlockedWords) {
                    if (text.contains(word)) return true
                }
            }
            val desc = node.contentDescription?.toString()?.lowercase()
            if (desc != null) {
                for (word in substringBlockedWords) {
                    if (desc.contains(word)) return true
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null && nodeContainsBlockedWord(child, depth + 1)) return true
            }
        } catch (_: Exception) {}
        return false
    }

    private fun normalizeLeetspeak(text: String): String {
        return text.lowercase()
            .replace("0", "o")
            .replace("1", "i")
            .replace("3", "e")
            .replace("4", "a")
            .replace("5", "s")
            .replace("7", "t")
            .replace("8", "b")
            .replace("9", "g")
            .replace('$', 's')
            .replace('!', 'i')
            .replace('@', 'a')
            .replace('|', 'i')
            .replace(Regex("[.\\-_:,;+*~'\"#]"), "")
    }
}
