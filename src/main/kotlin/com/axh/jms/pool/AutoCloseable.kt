package com.axh.jms.pool

fun AutoCloseable.tryClose() =
    try {
        close()
    } catch (_: Exception) {
        // do nothing
    }