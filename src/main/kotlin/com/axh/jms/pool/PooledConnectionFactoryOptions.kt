package com.axh.jms.pool

import javax.jms.Session
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


data class PooledConnectionFactoryOptions @JvmOverloads constructor(
    val transacted: Boolean = false,
    val sessionMode: Int = Session.AUTO_ACKNOWLEDGE,
    val maxConnections: Int = 2,
    val maxSessionsPerConnection: Int = 100,
    val maxSessionProducerCache: Int = 1000,
    val maxIdleSessionTime: Duration = 30.seconds,
)