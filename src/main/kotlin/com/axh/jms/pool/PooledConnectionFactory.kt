package com.axh.jms.pool

import com.github.benmanes.caffeine.cache.Caffeine
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import org.slf4j.LoggerFactory
import java.util.concurrent.locks.ReentrantLock
import javax.jms.Connection
import javax.jms.ConnectionFactory
import javax.jms.Destination
import javax.jms.ExceptionListener
import javax.jms.JMSContext
import javax.jms.JMSException
import javax.jms.MessageProducer
import javax.jms.Session
import kotlin.concurrent.withLock

class PooledConnectionFactory @JvmOverloads constructor(
    private val delegate: ConnectionFactory,
    private val options: PooledConnectionFactoryOptions = PooledConnectionFactoryOptions(),
) : ConnectionFactory, AutoCloseable {
    private val logger = LoggerFactory.getLogger(PooledConnectionFactory::class.java)
    private val pooled = List(options.maxConnections) { PooledConnectionItem() }
        .toMinimumInFlightIterable()
    private val idleMonitor = IdleMonitor("session", options.maxIdleSessionTime) {
        pooled.items.flatMap { it.sessions }
    }

    @JvmOverloads
    fun registerMetrics(meterRegistry: MeterRegistry, tags: Tags = Tags.empty()) {
        meterRegistry.gauge("jms.pool.connections", tags, pooled.items) {
            it.count(Health::healthy).toDouble()
        }
        meterRegistry.gauge("jms.pool.sessions", tags, pooled.items) {
            it.sumOf(PooledConnectionItem::sessionCount).toDouble()
        }
        meterRegistry.gauge("jms.pool.sessions.active", tags, pooled) {
            it.inFlight().toDouble()
        }
        meterRegistry.gauge("jms.pool.sessions.idle.closed", tags, idleMonitor.closedIdleCounter)
    }

    private val poolIterator = pooled.iterator()

    override fun createConnection(): Connection =
        createConnection(null, null)

    override fun createConnection(userName: String?, password: String?): Connection =
        poolIterator.next().getOrCreate(userName, password)

    override fun createContext(): JMSContext =
        createContext(null, null)

    override fun createContext(userName: String?, password: String?): JMSContext =
        createContext(null, null, JMSContext.AUTO_ACKNOWLEDGE)

    override fun createContext(
        userName: String?,
        password: String?,
        sessionMode: Int
    ): JMSContext {
        error("JMSContext is not supported")
    }

    override fun createContext(sessionMode: Int): JMSContext =
        createContext(null, null, sessionMode)

    private abstract inner class PooledItem<T> : IdleBase(), AutoCloseable, InFlight, Health
        where T : InternalAutoCloseable, T : InFlight {
        protected val lock = ReentrantLock()
        protected var value: T? = null

        override fun close() {
            logger.debug("Closing connection")
            lock.withLock {
                value?.closeInternal()
                value = null
            }
        }

        override fun inFlight(): Int =
            value?.inFlight() ?: 0

        override fun healthy() = value != null
    }

    private inner class PooledConnectionItem : AutoCloseable, ExceptionListener, PooledItem<PooledConnection>() {
        fun getOrCreate(userName: String?, password: String?): PooledConnection =
            value ?: lock.withLock {
                logger.debug("Creating connection")
                val connection =
                    if (userName != null || password != null)
                        delegate.createConnection(userName, password)
                    else delegate.createConnection()
                connection.exceptionListener = this
                val sessions = List(options.maxSessionsPerConnection) { PooledSessionItem(connection) }.toMinimumInFlightIterable()
                val pooledConnection = PooledConnection(connection, sessions)
                value = pooledConnection
                return pooledConnection
            }

        override fun onException(exception: JMSException?) {
            if (exception != null) {
                logger.error("Connection exception", exception)
            } else {
                logger.error("Unknown connection exception")
            }
            close()
        }

        val sessions get() = value?.sessions?.items ?: emptyList()

        val sessionCount: Int get() = sessions.count(Health::healthy)
    }

    private inner class PooledConnection(
        private val delegate: Connection,
        val sessions: MinimumInFlightIterable<PooledSessionItem>
    ) : Connection by delegate, InFlight by sessions, WrappedConnection, InternalAutoCloseable, AutoCloseable {
        private val sessionsIterator = sessions.iterator()

        override fun createSession(): Session =
            createSession(options.transacted, options.sessionMode)

        override fun createSession(sessionMode: Int): Session =
            createSession(false, sessionMode)

        override fun createSession(transacted: Boolean, acknowledgeMode: Int): Session {
            require(acknowledgeMode == options.sessionMode) {
                "Session mode $acknowledgeMode not supported, expected ${options.sessionMode}"
            }
            require(transacted == options.transacted) {
                "Transacted mode $transacted not supported, expected ${options.transacted}"
            }

            return sessionsIterator.next().getOrCreate(transacted, acknowledgeMode).borrow()
        }

        override fun unwrap(): Connection = delegate

        override fun close() {
            // public api, do nothing
        }

        override fun closeInternal() {
            sessions.tryClose()
            delegate.tryClose()
        }
    }

    private inner class PooledSessionItem(private val connection: Connection) : PooledItem<PooledSession>() {
        fun getOrCreate(transacted: Boolean, acknowledgeMode: Int): PooledSession =
            value ?: lock.withLock {
                logger.debug("Creating session")
                val session = connection.createSession(transacted, acknowledgeMode)
                val pooledSession = PooledSession(session, this)
                value = pooledSession
                return pooledSession
            }
    }

    private inner class PooledSession(private val delegate: Session, private val activated: Activated) : Session by delegate, InFlight, WrappedSession, InternalAutoCloseable, AutoCloseable {
        private val producers = Caffeine.newBuilder()
            .maximumSize(options.maxSessionProducerCache.toLong())
            .evictionListener<Destination, MessageProducer> { _, value, _ -> value?.tryClose() }
            .build(delegate::createProducer)

        fun borrow(): PooledSession = apply { activated.activate() }

        override fun createProducer(destination: Destination): MessageProducer =
            producers[destination]

        override fun unwrap(): Session = delegate

        override fun close() {
            // public api, return to the pool
            activated.deactivate()
        }

        override fun inFlight(): Int = if (activated.active) 1 else 0

        override fun closeInternal() {
            delegate.tryClose()
        }
    }

    override fun close() {
        idleMonitor.tryClose()
        pooled.tryClose()
    }
}

@JvmOverloads
fun ConnectionFactory.pooled(options: PooledConnectionFactoryOptions = PooledConnectionFactoryOptions()) =
    PooledConnectionFactory(this, options)

fun <R> ConnectionFactory.useSession(block: Session.() -> R): R =
    createConnection().createSession().use(block)