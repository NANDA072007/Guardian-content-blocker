package com.guardian.app.config

import com.guardian.app.walls.wall2.model.AppSignature
import com.guardian.app.walls.wall2.model.AppCategory

object BlocklistConfig {

    val substringBlockedWords: Set<String> = setOf(
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

    val wordBoundaryBlockedWords: Set<String> = setOf(
        "sex", "porn", "xxx", "nude", "naked", "nsfw",
        "milf", "anal", "jav", "bokep", "fuck", "slut",
        "whore", "dick", "cock", "puss", "cum", "gay",
        "lesbian", "semen", "porno", "pr0n", "p0rn",
        "henti", "phub", "nood", "horny", "thot"
    )

    val settingsPackages: Set<String> = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oneplus.security",
        "com.vivo.permissionmanager",
        "com.huawei.systemmanager"
    )

    val browserPackages: Set<String> = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
        "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
        "org.mozilla.focus", "org.mozilla.klar",
        "com.brave.browser", "com.brave.browser_beta",
        "com.duckduckgo.mobile.android",
        "com.opera.browser", "com.opera.mini.native", "com.opera.gx",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.kiwibrowser.browser",
        "com.vivaldi.browser",
        "com.ecosia.android"
    )

    val installerPackages: Set<String> = setOf(
        "com.android.vending",
        "com.google.android.finsky",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "org.fdroid.fdroid",
        "com.aurora.store",
        "com.sec.android.app.samsungapps"
    )

    val knownAdultApps: List<AppSignature> = listOf(
        AppSignature("com.example.adultapp1", "Adult App 1", null, AppCategory.KNOWN_ADULT),
        AppSignature("com.example.adultapp2", "Adult App 2", null, AppCategory.KNOWN_ADULT,
            aliases = setOf("com.example.adultapp2.pro", "com.example.adultapp2.vip"))
    )
}
