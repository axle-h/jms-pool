package com.axh.jms.pool

import javax.jms.Connection
import javax.jms.Session

interface Wrapped<T> {
    fun unwrap(): T
}

interface WrappedConnection : Wrapped<Connection>, Connection
interface WrappedSession : Wrapped<Session>, Session