package com.guardian.app.walls.wall2.adapter

interface RuleProvider<T> {
    fun matches(target: T): Boolean
}
