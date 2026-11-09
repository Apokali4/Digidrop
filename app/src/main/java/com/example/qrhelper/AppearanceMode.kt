package com.example.qrhelper

enum class AppearanceMode {
    SYSTEM, LIGHT, DARK;

    fun resolve(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromPreference(value: String?): AppearanceMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
