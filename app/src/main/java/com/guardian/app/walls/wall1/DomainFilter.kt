package com.guardian.app.walls.wall1

/**
 * Interface contract for matching domains against the blocker rules.
 */
interface DomainFilter {
    /**
     * Returns true if the domain matches any blocked rules.
     */
    fun shouldBlock(domain: String): Boolean
}
