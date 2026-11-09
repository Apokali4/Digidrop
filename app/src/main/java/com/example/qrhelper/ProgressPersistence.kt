package com.example.qrhelper

/**
 * Core delivery progress is durable route data and must always be written to Room.
 * The legacy auto-save preference only controls compatibility data such as the
 * session set of unique package codes; it must never disable route progress writes.
 */
internal suspend fun persistRouteProgress(
    persist: suspend () -> Unit
) {
    persist()
}
