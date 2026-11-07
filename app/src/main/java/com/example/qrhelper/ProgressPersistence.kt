package com.example.qrhelper

/** Persists delivery progress only when automatic saving is enabled. */
internal suspend fun persistProgressIfEnabled(
    autoSaveEnabled: Boolean,
    persist: suspend () -> Unit
) {
    if (autoSaveEnabled) persist()
}
