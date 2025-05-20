package com.axh.jms.pool

import java.io.Serializable
import javax.jms.BytesMessage
import javax.jms.CompletionListener
import javax.jms.Connection
import javax.jms.ConnectionConsumer
import javax.jms.ConnectionFactory
import javax.jms.ConnectionMetaData
import javax.jms.Destination
import javax.jms.ExceptionListener
import javax.jms.JMSContext
import javax.jms.JMSException
import javax.jms.MapMessage
import javax.jms.Message
import javax.jms.MessageConsumer
import javax.jms.MessageListener
import javax.jms.MessageProducer
import javax.jms.ObjectMessage
import javax.jms.Queue
import javax.jms.QueueBrowser
import javax.jms.ServerSessionPool
import javax.jms.Session
import javax.jms.StreamMessage
import javax.jms.TemporaryQueue
import javax.jms.TemporaryTopic
import javax.jms.TextMessage
import javax.jms.Topic
import javax.jms.TopicSubscriber

fun notSupported(): Nothing = error("not supported")

class FakeConnectionFactory : ConnectionFactory {
    val connections = mutableListOf<FakeConnection>()
    val sessions get() = connections.flatMap { it.sessions }
    val producers get() = sessions.flatMap { it.producers }

    override fun createConnection(): Connection = FakeConnection().also(connections::add)
    override fun createConnection(userName: String?, password: String?): Connection =
        createConnection()

    override fun createContext(): JMSContext? = notSupported()

    override fun createContext(userName: String?, password: String?): JMSContext? = notSupported()

    override fun createContext(
        userName: String?,
        password: String?,
        sessionMode: Int
    ): JMSContext? = notSupported()

    override fun createContext(sessionMode: Int): JMSContext? = notSupported()
}

class FakeConnection : Connection {
    val sessions = mutableListOf<FakeSession>()
    private var exceptionListener: ExceptionListener? = null

    var closed = false
    override fun close() {
        closed = true
    }

    fun sendException() {
        exceptionListener?.onException(JMSException("a bad thing")) ?: error("No exception listener")
    }

    override fun createConnectionConsumer(
        destination: Destination?,
        messageSelector: String?,
        sessionPool: ServerSessionPool?,
        maxMessages: Int
    ): ConnectionConsumer? = notSupported()

    override fun createSharedConnectionConsumer(
        topic: Topic?,
        subscriptionName: String?,
        messageSelector: String?,
        sessionPool: ServerSessionPool?,
        maxMessages: Int
    ): ConnectionConsumer? = notSupported()

    override fun createDurableConnectionConsumer(
        topic: Topic?,
        subscriptionName: String?,
        messageSelector: String?,
        sessionPool: ServerSessionPool?,
        maxMessages: Int
    ): ConnectionConsumer? = notSupported()

    override fun createSharedDurableConnectionConsumer(
        topic: Topic?,
        subscriptionName: String?,
        messageSelector: String?,
        sessionPool: ServerSessionPool?,
        maxMessages: Int
    ): ConnectionConsumer? = notSupported()

    override fun createSession(transacted: Boolean, acknowledgeMode: Int): Session? =
        FakeSession().also(sessions::add)

    override fun createSession(sessionMode: Int): Session? = notSupported()

    override fun createSession(): Session? = notSupported()

    override fun getClientID(): String? = notSupported()

    override fun setClientID(clientID: String?) = notSupported()

    override fun getMetaData(): ConnectionMetaData? = notSupported()

    override fun getExceptionListener(): ExceptionListener? = exceptionListener

    override fun setExceptionListener(listener: ExceptionListener?) {
        exceptionListener = listener
    }

    override fun start() = notSupported()

    override fun stop() = notSupported()
}

class FakeSession : Session {
    val producers = mutableListOf<FakeMessageProducer>()
    var closed = false
    
    override fun createBytesMessage(): BytesMessage? = notSupported()

    override fun createMapMessage(): MapMessage? = notSupported()

    override fun createMessage(): Message? = notSupported()

    override fun createObjectMessage(): ObjectMessage? = notSupported()

    override fun createObjectMessage(`object`: Serializable?): ObjectMessage? = notSupported()

    override fun createStreamMessage(): StreamMessage? = notSupported()

    override fun createTextMessage(): TextMessage? = notSupported()

    override fun createTextMessage(text: String?): TextMessage? = notSupported()

    override fun getTransacted(): Boolean = notSupported()

    override fun getAcknowledgeMode(): Int = notSupported()

    override fun commit() = notSupported()

    override fun rollback() = notSupported()

    override fun close() {
        closed = true
    }

    override fun recover() = notSupported()

    override fun getMessageListener(): MessageListener? = notSupported()

    override fun setMessageListener(listener: MessageListener?) = notSupported()

    override fun run() = notSupported()

    override fun createProducer(destination: Destination): MessageProducer =
        FakeMessageProducer(destination).also(producers::add)

    override fun createConsumer(destination: Destination?): MessageConsumer? = notSupported()

    override fun createConsumer(
        destination: Destination?,
        messageSelector: String?
    ): MessageConsumer? = notSupported()

    override fun createConsumer(
        destination: Destination?,
        messageSelector: String?,
        noLocal: Boolean
    ): MessageConsumer? = notSupported()

    override fun createSharedConsumer(
        topic: Topic?,
        sharedSubscriptionName: String?
    ): MessageConsumer? = notSupported()

    override fun createSharedConsumer(
        topic: Topic?,
        sharedSubscriptionName: String?,
        messageSelector: String?
    ): MessageConsumer? = notSupported()

    override fun createQueue(queueName: String?): Queue? = notSupported()

    override fun createTopic(topicName: String): Topic = FakeTopic(topicName)

    override fun createDurableSubscriber(topic: Topic?, name: String?): TopicSubscriber? = notSupported()

    override fun createDurableSubscriber(
        topic: Topic?,
        name: String?,
        messageSelector: String?,
        noLocal: Boolean
    ): TopicSubscriber? = notSupported()

    override fun createDurableConsumer(topic: Topic?, name: String?): MessageConsumer? = notSupported()

    override fun createDurableConsumer(
        topic: Topic?,
        name: String?,
        messageSelector: String?,
        noLocal: Boolean
    ): MessageConsumer? = notSupported()

    override fun createSharedDurableConsumer(
        topic: Topic?,
        name: String?
    ): MessageConsumer? = notSupported()

    override fun createSharedDurableConsumer(
        topic: Topic?,
        name: String?,
        messageSelector: String?
    ): MessageConsumer? = notSupported()

    override fun createBrowser(queue: Queue?): QueueBrowser? = notSupported()

    override fun createBrowser(queue: Queue?, messageSelector: String?): QueueBrowser? = notSupported()

    override fun createTemporaryQueue(): TemporaryQueue? = notSupported()

    override fun createTemporaryTopic(): TemporaryTopic? = notSupported()

    override fun unsubscribe(name: String?) = notSupported()

}

data class FakeTopic(val name: String) : Topic {
    override fun getTopicName(): String = name
    override fun toString(): String = name
}

class FakeMessageProducer(val producerDestination: Destination) : MessageProducer {
    var closed = false
    
    override fun setDisableMessageID(value: Boolean) = notSupported()

    override fun getDisableMessageID(): Boolean = notSupported()

    override fun setDisableMessageTimestamp(value: Boolean) = notSupported()

    override fun getDisableMessageTimestamp(): Boolean = notSupported()

    override fun setDeliveryMode(deliveryMode: Int) = notSupported()

    override fun getDeliveryMode(): Int = notSupported()

    override fun setPriority(defaultPriority: Int) = notSupported()

    override fun getPriority(): Int = notSupported()

    override fun setTimeToLive(timeToLive: Long) = notSupported()

    override fun getTimeToLive(): Long = notSupported()

    override fun setDeliveryDelay(deliveryDelay: Long) = notSupported()

    override fun getDeliveryDelay(): Long = notSupported()

    override fun getDestination(): Destination? = producerDestination

    override fun close() {
        closed = true
    }

    override fun send(message: Message?) = notSupported()

    override fun send(
        message: Message?,
        deliveryMode: Int,
        priority: Int,
        timeToLive: Long
    ) = notSupported()

    override fun send(destination: Destination?, message: Message?) = notSupported()

    override fun send(
        destination: Destination?,
        message: Message?,
        deliveryMode: Int,
        priority: Int,
        timeToLive: Long
    ) = notSupported()

    override fun send(message: Message?, completionListener: CompletionListener?) = notSupported()

    override fun send(
        message: Message?,
        deliveryMode: Int,
        priority: Int,
        timeToLive: Long,
        completionListener: CompletionListener?
    ) = notSupported()

    override fun send(
        destination: Destination?,
        message: Message?,
        completionListener: CompletionListener?
    ) = notSupported()

    override fun send(
        destination: Destination?,
        message: Message?,
        deliveryMode: Int,
        priority: Int,
        timeToLive: Long,
        completionListener: CompletionListener?
    ) = notSupported()
}