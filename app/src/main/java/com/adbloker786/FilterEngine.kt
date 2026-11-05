package com.adbloker786

import java.util.Locale

internal object FilterEngine {
    private val normal = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "adnxs.com", "adsrvr.org"
    )
    private val advanced = normal + setOf(
        "criteo.com", "taboola.com", "outbrain.com", "scorecardresearch.com",
        "quantserve.com", "pubmatic.com", "rubiconproject.com", "openx.net",
        "adform.net", "smartadserver.com", "casalemedia.com", "moatads.com",
        "serving-sys.com", "amazon-adsystem.com", "ads-twitter.com", "ads.linkedin.com"
    )
    private val maximum = advanced + setOf(
        "unityads.unity3d.com", "applovin.com", "ironsrc.com", "inmobi.com",
        "advertising.com", "media.net", "lijit.com", "triplelift.com",
        "bidswitch.net", "sharethrough.com", "ads.facebook.com",
        "pixel.facebook.com", "an.facebook.com", "tr.facebook.com", "analytics.tiktok.com"
    )

    fun isBlocked(host: String, mode: String, customBlockRules: String = "", allowRules: String = ""): Boolean {
        val normalizedHost = normalize(host) ?: return false
        if (matchesAny(normalizedHost, parseRules(allowRules))) return false
        if (matchesAny(normalizedHost, parseRules(customBlockRules))) return true
        val rules = when (mode.lowercase(Locale.ROOT)) {
            "normal" -> normal
            "maximum" -> maximum
            else -> advanced
        }
        return matchesAny(normalizedHost, rules)
    }

    fun normalize(value: String): String? {
        val candidate = value.trim().lowercase(Locale.ROOT).removePrefix("*.").trimEnd('.')
        if (candidate.isBlank() || candidate.length > 253 || candidate.contains("..")) return null
        if (!candidate.matches(Regex("[a-z0-9.-]+"))) return null
        if (candidate.startsWith('.') || candidate.endsWith('.')) return null
        return candidate
    }

    private fun parseRules(value: String): Set<String> =
        value.split(Regex("[\\r\\n,;\\s]+")).mapNotNull(::normalize).toSet()

    private fun matchesAny(host: String, rules: Set<String>): Boolean =
        rules.any { host == it || host.endsWith(".$it") }
}
