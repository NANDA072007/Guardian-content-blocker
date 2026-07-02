package com.guardian.app.config

import org.junit.Assert.*
import org.junit.Test

class BlocklistConfigTest {

    @Test
    fun `substringBlockedWords is not empty`() {
        assertTrue(BlocklistConfig.substringBlockedWords.isNotEmpty())
    }

    @Test
    fun `wordBoundaryBlockedWords is not empty`() {
        assertTrue(BlocklistConfig.wordBoundaryBlockedWords.isNotEmpty())
    }

    @Test
    fun `settingsPackages is not empty`() {
        assertTrue(BlocklistConfig.settingsPackages.isNotEmpty())
    }

    @Test
    fun `browserPackages is not empty`() {
        assertTrue(BlocklistConfig.browserPackages.isNotEmpty())
    }

    @Test
    fun `settingsPackages contains standard Android settings`() {
        assertTrue("com.android.settings" in BlocklistConfig.settingsPackages)
    }

    @Test
    fun `browserPackages contains Chrome`() {
        assertTrue(BlocklistConfig.browserPackages.any { it.contains("chrome") })
    }

    @Test
    fun `browserPackages contains Firefox`() {
        assertTrue(BlocklistConfig.browserPackages.any { it.contains("firefox") })
    }

    @Test
    fun `no empty strings in blocked word lists`() {
        assertFalse(BlocklistConfig.substringBlockedWords.any { it.isEmpty() })
        assertFalse(BlocklistConfig.wordBoundaryBlockedWords.any { it.isEmpty() })
        assertFalse(BlocklistConfig.settingsPackages.any { it.isEmpty() })
        assertFalse(BlocklistConfig.browserPackages.any { it.isEmpty() })
    }

    @Test
    fun `no duplicates in blocked word lists`() {
        assertEquals(
            BlocklistConfig.substringBlockedWords.size,
            BlocklistConfig.substringBlockedWords.toSet().size
        )
        assertEquals(
            BlocklistConfig.wordBoundaryBlockedWords.size,
            BlocklistConfig.wordBoundaryBlockedWords.toSet().size
        )
    }
}
