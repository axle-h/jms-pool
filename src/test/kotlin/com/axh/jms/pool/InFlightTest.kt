package com.axh.jms.pool

import strikt.api.expectThat
import strikt.assertions.isSameInstanceAs
import kotlin.test.Test

class InFlightTest {
    @Test
    fun `picks the least in flight`() {
        val inFlight = listOf(ONE, ZERO, TWO).toMinimumInFlightIterable()
        val iterator = inFlight.iterator()
        repeat(3) { expectThat(iterator.next()).isSameInstanceAs(ZERO) }
    }

    @Test
    fun `picks the first when all have same in flight`() {
        val first = InFlight { 0 }
        val inFlight = listOf(first, ZERO, ZERO).toMinimumInFlightIterable()
        val iterator = inFlight.iterator()
        repeat(3) { expectThat(iterator.next()).isSameInstanceAs(first) }
    }

    companion object {
        val ZERO = InFlight { 0 }
        val ONE = InFlight { 1 }
        val TWO = InFlight { 2 }
    }
}