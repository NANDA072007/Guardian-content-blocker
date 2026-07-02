package com.guardian.app.walls.wall1

import android.util.Log
import com.guardian.app.data.repository.GuardianRepository
import com.guardian.app.util.CryptoUtils
import com.guardian.app.walls.wall1.pipeline.RuleEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RuleEngineTest {

    private lateinit var repository: GuardianRepository
    private lateinit var ruleEngine: RuleEngine

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any<String>(), any<String>()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any<String>(), any<String>(), any<Throwable>()) } returns 0

        repository = mockk(relaxed = true)
        coEvery { repository.getAllDomainHashes() } returns emptyList()
        ruleEngine = RuleEngine(repository)
    }

    @Test
    fun `shouldBlock returns false if domain is empty or not loaded`() {
        assertFalse(ruleEngine.shouldBlock(""))
        assertFalse(ruleEngine.shouldBlock("google.com"))
    }

    @Test
    fun `shouldBlock returns true for blocked domains and their subdomains`() = runTest {
        val blockedDomain = "adult.com"
        val hash = CryptoUtils.sha256(blockedDomain.toByteArray())
        ruleEngine.loadBlocklist(listOf(hash))

        // Exact match
        assertTrue(ruleEngine.shouldBlock("adult.com"))
        // Subdomain match
        assertTrue(ruleEngine.shouldBlock("sub.adult.com"))
        assertTrue(ruleEngine.shouldBlock("www.adult.com"))
        // Allowed domain
        assertFalse(ruleEngine.shouldBlock("google.com"))
    }

    @Test
    fun `shouldBlock ignores TLDs`() = runTest {
        val hash = CryptoUtils.sha256("com".toByteArray())
        ruleEngine.loadBlocklist(listOf(hash))

        // "com" is a TLD, so it should not cause general blocking of everything under dot-com
        assertFalse(ruleEngine.shouldBlock("google.com"))
    }
}
