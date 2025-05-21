package com.axh.jms.pool

import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.time.Duration.Companion.milliseconds

class IdleTest {
    private var now = 0L
    private val item = StubItem { now }
    private val monitor = IdleMonitor("test", maxIdle = 10.milliseconds, period = 10.milliseconds) {
        listOf(item)
    }

    @AfterTest
    fun close() {
        monitor.close()
    }

    @Test
    fun `unactivated item is not closed`() {
        monitor.update()
        expectThat(item.closed).isFalse()
    }

    @Test
    fun `currently active item is not closed`() {
        item.activate()
        monitor.update()
        expectThat(item.closed).isFalse()
    }

    @Test
    fun `inactive item within threshold is not closed`() {
        item.activate()
        item.deactivate()

        now += 10

        monitor.update()
        expectThat(item.closed).isFalse()
    }

    @Test
    fun `inactive item outside threshold is closed`() {
        item.activate()
        item.deactivate()

        now += 11

        monitor.update()
        expectThat(item.closed).isTrue()
    }

    @Test
    fun `tracks closed session count`() {
        expectThat(monitor.closedIdleCounter.get()).isEqualTo(0)

        item.activate()
        item.deactivate()
        now += 11
        monitor.update()

        expectThat(monitor.closedIdleCounter.get()).isEqualTo(1)
    }

    @Test
    fun `inactive items outside threshold are closed in the background`() {
        item.activate()
        item.deactivate()

        now += 11

        expectThat(item.closedFuture.get(5, SECONDS)).isTrue()
    }

    @Test
    fun `inactive items within threshold are not closed in the background`() {
        item.activate()
        item.deactivate()

        now += 10

        expectThrows<TimeoutException> {
            item.closedFuture.get(20, MILLISECONDS)
        }
    }

    private class StubItem(nowMillis: () -> Long) : IdleBase(nowMillis), InternalAutoCloseable {
        val closedFuture = CompletableFuture<Boolean>()
        var closed = false

        override fun closeInternal() {
            closed = true
            closedFuture.complete(true)
        }
    }
}