package com.axh.jms.pool

fun interface InFlight {
    fun inFlight(): Int
}

fun Iterable<InFlight>.sumInFlight() = InFlight { sumOf(InFlight::inFlight) }

class MinimumInFlightIterable<T : InFlight>(val items: List<T>) : Iterable<T>, InFlight, AutoCloseable {
    init {
        require(items.isNotEmpty()) { "At least one item is required" }
    }

    private val totalInFlight = items.sumInFlight()

    override fun iterator(): Iterator<T> = object : Iterator<T> {
        override fun next(): T = items.minBy(InFlight::inFlight)
        override fun hasNext(): Boolean = true
    }

    override fun inFlight(): Int = totalInFlight.inFlight()

    override fun close() {
        items.filterIsInstance<AutoCloseable>()
            .forEach(AutoCloseable::tryClose)
    }
}

fun <T : InFlight> List<T>.toMinimumInFlightIterable() = MinimumInFlightIterable(this)
