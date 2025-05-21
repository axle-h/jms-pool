package com.axh.jms.pool

import io.micrometer.core.instrument.Tags
import javax.jms.Session

data class PooledConnectionFactoryOptions(
    val transacted: Boolean = false,
    val sessionMode: Int = Session.AUTO_ACKNOWLEDGE,
    val maxConnections: Int = 2,
    val maxSessionsPerConnection: Int = 100,
    val maxSessionProducerCache: Int = 1000,
    val tags: Tags = Tags.empty(),
)