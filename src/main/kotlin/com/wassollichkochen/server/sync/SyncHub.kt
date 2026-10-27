package com.wassollichkochen.server.sync

import com.wassollichkochen.server.models.SyncEvent
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

object SyncHub {
    private val json = Json { encodeDefaults = true }
    private val connections = mutableMapOf<String, MutableList<WebSocketServerSession>>()
    private val mutex = Mutex()

    suspend fun register(householdId: String, session: WebSocketServerSession) {
        mutex.withLock {
            connections.getOrPut(householdId) { mutableListOf() }.add(session)
        }
    }

    suspend fun unregister(householdId: String, session: WebSocketServerSession) {
        mutex.withLock {
            connections[householdId]?.remove(session)
        }
    }

    suspend fun broadcast(householdId: String, event: SyncEvent, exclude: WebSocketServerSession? = null) {
        val text = json.encodeToString(SyncEvent.serializer(), event)
        val sessions = mutex.withLock { connections[householdId]?.toList() } ?: return
        val deadSessions = mutableListOf<WebSocketServerSession>()

        sessions.forEach { session ->
            if (session !== exclude) {
                try {
                    session.send(Frame.Text(text))
                } catch (e: Exception) {
                    deadSessions.add(session)
                }
            }
        }

        if (deadSessions.isNotEmpty()) {
            mutex.withLock {
                deadSessions.forEach { connections[householdId]?.remove(it) }
            }
        }
    }

    suspend fun clientCount(householdId: String): Int =
        mutex.withLock { connections[householdId]?.size ?: 0 }

}