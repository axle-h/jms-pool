package com.axh.jms.pool

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isSameInstanceAs
import strikt.assertions.isTrue
import strikt.assertions.single
import kotlin.test.AfterTest
import kotlin.test.Test

class PooledConnectionFactoryTest {

    private val factory = FakeConnectionFactory()
    private val meterRegistry = SimpleMeterRegistry()
    private val pooled = factory.pooled(
        PooledConnectionFactoryOptions(
            maxConnections = 2,
            maxSessionsPerConnection = 3
        ),
    ).apply { registerMetrics(meterRegistry) }
    private val connectionCountMeter = meterRegistry.get("jms.pool.connections").gauge()
    private val sessionCountMeter = meterRegistry.get("jms.pool.sessions").gauge()
    private val activeSessionCountMeter = meterRegistry.get("jms.pool.sessions.active").gauge()

    @AfterTest
    fun close() {
        pooled.close()
    }

    @Test
    fun `creates new connection`() {
        val connection = pooled.createConnection() as WrappedConnection
        expectThat(factory.connections)
            .single()
            .isSameInstanceAs(connection.unwrap())
    }

    @Test
    fun `creates new session`() {
        val connection = pooled.createConnection() as WrappedConnection
        val session = connection.createSession() as WrappedSession

        expectThat(factory.connections)
            .single()
            .and {
                isSameInstanceAs(connection.unwrap())
                get { sessions }
                    .single()
                    .isSameInstanceAs(session.unwrap())
            }
    }

    @Test
    fun `reuses pooled connection`() {
        val connection1 = pooled.createConnection()
        val connection2 = pooled.createConnection()
        expectThat(connection2).isSameInstanceAs(connection1)
    }

    @Test
    fun `closing a pooled session does not close the underlying session`() {
        pooled.createConnection().createSession().apply(AutoCloseable::close)
        expectThat(factory.sessions.single().closed).isFalse()
    }

    @Test
    fun `open sessions are borrowed`() {
        val session1 = pooled.createConnection().createSession()
        val session2 = pooled.createConnection().createSession()
        expectThat(session2).not().isSameInstanceAs(session1)
    }

    @Test
    fun `max connections and sessions are limited`() {
        repeat(7) {
            pooled.createConnection().createSession()
        }
        expectThat(factory.connections).hasSize(2)
        expectThat(factory.sessions).hasSize(6)
    }

    @Test
    fun `sessions round robin the connections`() {
        repeat(4) {
            pooled.createConnection().createSession()
        }
        expectThat(factory.connections).hasSize(2)
        expectThat(factory.sessions).hasSize(4)
        expectThat(factory.connections).all { get { sessions.size }.isEqualTo(2) }
    }

    @Test
    fun `closing a pooled session returns it to the pool`() {
        val session1 = pooled.createConnection().createSession().apply(AutoCloseable::close)
        val session2 = pooled.createConnection().createSession()
        expectThat(session2).isSameInstanceAs(session1)
    }

    @Test
    fun `closing a pooled connection does nothing`() {
        pooled.createConnection().apply(AutoCloseable::close)
        expectThat(factory.connections.single().closed).isFalse()
    }

    @Test
    fun `connections and sessions are closed on exception`() {
        pooled.createConnection().createSession()
        factory.connections.single().sendException()
        expectThat(factory.connections.single().closed).isTrue()
        expectThat(factory.sessions.single().closed).isTrue()
    }

    @Test
    fun `broken connections are recreated`() {
        pooled.createConnection().createSession()
        factory.connections.single().sendException()

        pooled.createConnection().createSession()
        pooled.createConnection().createSession()
        expectThat(factory.connections)
            .hasSize(3) // the broken connection plus 2 new ones
    }

    @Test
    fun `message producers are cached by destination`() {
        val connection = pooled.createConnection() as WrappedConnection
        val session = connection.createSession() as WrappedSession
        val destination = session.createTopic("test1")
        val producer1 = session.createProducer(destination)
        val producer2 = session.createProducer(destination)
        val producer3 = session.createProducer(session.createTopic("test2"))
        expectThat(factory.producers)
            .hasSize(2)
        expectThat(producer2).isSameInstanceAs(producer1)
        expectThat(producer3).not().isSameInstanceAs(producer1)
    }

    @Test
    fun `measures connection count`() {
        expectThat(connectionCountMeter.value()).isEqualTo(0.0)
        repeat (3) {
            pooled.createConnection().createSession()
        }
        expectThat(connectionCountMeter.value()).isEqualTo(2.0)
    }

    @Test
    fun `unhealthy connections are not counted`() {
        pooled.createConnection().createSession()
        expectThat(connectionCountMeter.value()).isEqualTo(1.0)
        factory.connections.first().sendException()
        expectThat(connectionCountMeter.value()).isEqualTo(0.0)
    }

    @Test
    fun `measures session count`() {
        expectThat(sessionCountMeter.value()).isEqualTo(0.0)
        List (3) {
            pooled.createConnection().createSession()
        }.map(AutoCloseable::close)
        expectThat(sessionCountMeter.value()).isEqualTo(3.0)
    }

    @Test
    fun `measures active session count`() {
        expectThat(activeSessionCountMeter.value()).isEqualTo(0.0)
        repeat (3) {
            pooled.createConnection().createSession()
        }
        expectThat(activeSessionCountMeter.value()).isEqualTo(3.0)
    }

    @Test
    fun `inactive sessions are not counted`() {
        val session = pooled.createConnection().createSession()
        expectThat(activeSessionCountMeter.value()).isEqualTo(1.0)
        session.close()
        expectThat(activeSessionCountMeter.value()).isEqualTo(0.0)
    }
}