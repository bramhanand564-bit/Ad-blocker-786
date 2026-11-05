package com.adbloker786

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterEngineTest {
    @Test fun blocksKnownAdvertisingDomainAndSubdomains() {
        assertTrue(FilterEngine.isBlocked("doubleclick.net", "normal"))
        assertTrue(FilterEngine.isBlocked("pagead.doubleclick.net", "normal"))
    }

    @Test fun allowListOverridesBuiltInAndCustomRules() {
        assertFalse(FilterEngine.isBlocked("ads.example.com", "maximum", "example.com", "ads.example.com"))
    }

    @Test fun normalModeIsLessAggressiveThanMaximum() {
        assertFalse(FilterEngine.isBlocked("applovin.com", "normal"))
        assertTrue(FilterEngine.isBlocked("applovin.com", "maximum"))
    }

    @Test fun rejectsMalformedCustomDomains() {
        assertFalse(FilterEngine.isBlocked("not a domain", "maximum", "not a domain"))
    }
}
