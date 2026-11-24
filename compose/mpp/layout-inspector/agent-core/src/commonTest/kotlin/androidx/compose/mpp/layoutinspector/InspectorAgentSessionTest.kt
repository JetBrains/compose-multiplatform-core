/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.compose.mpp.layoutinspector

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class InspectorAgentSessionTest {
    @Test
    fun greetingAndPingNeedOnlyAnEstablishedSession() = runBlocking<Unit> {
        val session = TestSession()
        session.input.send(InspectorMessage.Greeting)
        session.input.send(InspectorMessage.Ping(6))
        session.input.close()
        DefaultInspectorAgent().run(session)
        assertEquals(InspectorMessage.GreetingAccepted, session.output.receive())
        assertEquals(InspectorMessage.Pong(6), session.output.receive())
        assertTrue(session.output.receiveCatching().isClosed)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun greetingIsRequiredIndependentlyOfConnectionDirection() = runBlocking<Unit> {
        val session = TestSession()
        session.input.send(InspectorMessage.Ping(1))
        DefaultInspectorAgent().run(session)
        assertIs<InspectorMessage.Error>(session.output.receive())
        assertTrue(session.output.receiveCatching().isClosed)
        assertIs<InspectorProtocolException>(session.closeCause)
    }

    @Test
    fun invalidPostGreetingMessagesAreRejected() = runBlocking<Unit> {
        val session = TestSession()
        session.input.send(InspectorMessage.Greeting)
        session.input.send(InspectorMessage.GreetingAccepted)
        DefaultInspectorAgent().run(session)
        assertEquals(InspectorMessage.GreetingAccepted, session.output.receive())
        assertIs<InspectorMessage.Error>(session.output.receive())
        assertIs<InspectorProtocolException>(session.closeCause)
    }

    @Test
    fun cancellationClosesTheSession() = runBlocking<Unit> {
        val session = TestSession()
        val running = launch { DefaultInspectorAgent().run(session) }
        withTimeout(2_000) { session.receiving.await() }
        running.cancelAndJoin()
        assertEquals(1, session.closeCount)
        assertIs<CancellationException>(session.closeCause)
    }

    @Test
    fun greetingTimeoutClosesTheSession() = runBlocking<Unit> {
        val session = TestSession()
        assertFailsWith<TimeoutCancellationException> { DefaultInspectorAgent(50).run(session) }
        assertEquals(1, session.closeCount)
        assertIs<TimeoutCancellationException>(session.closeCause)
    }

    @Test
    fun endOfStreamBeforeGreetingClosesWithoutSendingMessages() = runBlocking<Unit> {
        val session = TestSession()
        session.input.close()
        DefaultInspectorAgent().run(session)
        assertTrue(session.output.receiveCatching().isClosed)
        assertEquals(1, session.closeCount)
    }

    @Test
    fun framingErrorsAreRejectedAndTransportErrorsArePropagated() = runBlocking<Unit> {
        val invalid = TestSession()
        invalid.input.close(InspectorProtocolException("Invalid JSON"))
        DefaultInspectorAgent().run(invalid)
        assertEquals(InspectorMessage.Error("Invalid JSON"), invalid.output.receive())
        assertIs<InspectorProtocolException>(invalid.closeCause)

        val broken = TestSession()
        val failure = IllegalStateException("Transport lost")
        broken.input.close(failure)
        val propagated = assertFailsWith<IllegalStateException> { DefaultInspectorAgent().run(broken) }
        assertEquals(failure.message, propagated.message)
        assertEquals(failure.message, assertIs<IllegalStateException>(broken.closeCause).message)
    }

    @Test
    fun oneAgentCanHandleIndependentSessionsConcurrently() = runBlocking<Unit> {
        val agent = DefaultInspectorAgent()
        (1L..5L).map { id ->
            async {
                val session = TestSession()
                session.input.send(InspectorMessage.Greeting)
                session.input.send(InspectorMessage.Ping(id))
                session.input.close()
                agent.run(session)
                assertEquals(InspectorMessage.GreetingAccepted, session.output.receive())
                assertEquals(InspectorMessage.Pong(id), session.output.receive())
            }
        }.awaitAll()
    }

    @Test
    fun nonPositiveGreetingTimeoutIsRejected() {
        assertFailsWith<IllegalArgumentException> { DefaultInspectorAgent(0) }
    }

    private class TestSession : InspectorSession {
        val input = Channel<InspectorMessage>(Channel.UNLIMITED)
        val output = Channel<InspectorMessage>(Channel.UNLIMITED)
        val receiving = CompletableDeferred<Unit>()
        var closeCount = 0
        var closeCause: Throwable? = null

        override suspend fun receive(): InspectorMessage? {
            receiving.complete(Unit)
            val result = input.receiveCatching()
            result.exceptionOrNull()?.let { throw it }
            return result.getOrNull()
        }

        override suspend fun send(message: InspectorMessage) { output.send(message) }
        override suspend fun close(cause: Throwable?) {
            closeCount++
            closeCause = cause
            input.cancel()
            output.close()
        }
    }
}
