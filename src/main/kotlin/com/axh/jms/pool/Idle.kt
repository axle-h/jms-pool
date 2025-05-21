package com.axh.jms.pool

import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.fixedRateTimer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface Idle {
    fun activeState(): IdleState
}

sealed interface IdleState

data object Active : IdleState

data class Inactive(val duration: Duration) : IdleState

interface Activated {
    val active: Boolean
    fun activate()
    fun deactivate()
}

abstract class IdleBase(private val nowMillis: () -> Long = System::currentTimeMillis) : Idle, Activated {
    private var lastActive: Long = nowMillis()

    override var active: Boolean = false

    override fun activate() {
        active = true
    }

    override fun deactivate() {
        active = false
        lastActive = nowMillis()
    }

    override fun activeState(): IdleState =
        if (active) Active
        else Inactive((nowMillis() - lastActive).milliseconds.absoluteValue)
}

class IdleMonitor<T>(
    private val name: String,
    private val maxIdle: Duration,
    private val period: Duration = maxIdle,
    private val items: () -> Iterable<T>,
) : AutoCloseable where T : Idle, T : InternalAutoCloseable {
    private val logger = LoggerFactory.getLogger(IdleMonitor::class.java)
    val closedIdleCounter = AtomicLong(0)

    private val timer =
        fixedRateTimer("idle.$name", daemon = true, initialDelay = period.inWholeMilliseconds, period = period.inWholeMilliseconds) {
            try {
                update()
            } catch (e: Exception) {
                logger.error("failed to monitor idle $name", e)
            }
        }

    fun update() {
        for (item in items()) {
            val state = item.activeState()
            if (state is Inactive && state.duration > maxIdle) {
                item.closeInternal()
                closedIdleCounter.incrementAndGet()
            }
        }
    }

    override fun close() {
        timer.cancel()
    }


}