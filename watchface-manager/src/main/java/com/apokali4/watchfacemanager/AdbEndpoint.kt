package com.apokali4.watchfacemanager

internal object AdbEndpoint {
    fun normalizeHost(value: String): String {
        val host = value.trim()
        require(host.isNotEmpty()) { "Укажи IP-адрес часов." }
        require(host.none { it.isWhitespace() || it == '/' }) { "Некорректный адрес часов." }
        return host
    }

    fun parsePort(value: String): Int {
        val port = value.trim().toIntOrNull()
        require(port != null && port in 1..65535) { "Порт должен быть числом от 1 до 65535." }
        return port
    }

    fun describeFailure(stage: String, error: Throwable): String {
        val details = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString(" → ")
            .ifBlank { error::class.java.simpleName }
        return "$stage: $details"
    }
}
