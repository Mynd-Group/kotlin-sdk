package com.myndstream.myndcoresdk.core.utils

import java.util.UUID

class ListeningSessionManager(
        private val inactivityTimeoutMs: Long = DEFAULT_INACTIVITY_TIMEOUT_MS,
        private val timeProvider: () -> Long = { System.currentTimeMillis() },
        private val idProvider: () -> String = { UUID.randomUUID().toString() }
) {
    private var lastActiveAtMs: Long? = null
    private var currentSessionId: String? = null
    private val lock = Any()

    init {
        require(inactivityTimeoutMs > 0) { "inactivityTimeoutMs must be > 0" }
    }

    fun getSessionId(): String {
        val now = timeProvider()
        synchronized(lock) {
            if (shouldStartNewSession(now)) {
                val newId = idProvider().trim()
                require(newId.isNotEmpty()) { "idProvider returned blank id" }
                currentSessionId = newId
                println("ListeningSessionManager: new session id=$newId")
            }
            lastActiveAtMs = now
            return currentSessionId!!
        }
    }

    fun extendSession() {
        val now = timeProvider()
        synchronized(lock) {
            if (shouldStartNewSession(now)) {
                val newId = idProvider().trim()
                require(newId.isNotEmpty()) { "idProvider returned blank id" }
                currentSessionId = newId
                println("ListeningSessionManager: new session id=$newId")
            }
            lastActiveAtMs = now
        }
    }

    private fun shouldStartNewSession(now: Long): Boolean {
        if (lastActiveAtMs == null) return true
        if (currentSessionId.isNullOrBlank()) return true
        return (now - lastActiveAtMs!!) >= inactivityTimeoutMs
    }

    companion object {
        const val DEFAULT_INACTIVITY_TIMEOUT_MS: Long = 15L * 60L * 1000L
    }
}
