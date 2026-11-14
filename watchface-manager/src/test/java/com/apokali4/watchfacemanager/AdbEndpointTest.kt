package com.apokali4.watchfacemanager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AdbEndpointTest {
    @Test fun acceptsValidIpv4AndPort() {
        assertEquals(5555, AdbEndpoint.parsePort("5555"))
        assertEquals("192.168.1.42", AdbEndpoint.normalizeHost(" 192.168.1.42 "))
    }

    @Test fun rejectsInvalidPort() {
        assertThrows(IllegalArgumentException::class.java) { AdbEndpoint.parsePort("0") }
        assertThrows(IllegalArgumentException::class.java) { AdbEndpoint.parsePort("65536") }
        assertThrows(IllegalArgumentException::class.java) { AdbEndpoint.parsePort("abc") }
    }

    @Test fun rejectsEmptyHost() {
        assertThrows(IllegalArgumentException::class.java) { AdbEndpoint.normalizeHost("  ") }
    }

    @Test fun reportsNestedAdbFailureDetails() {
        val failure = IllegalStateException("Pair завершился", java.io.IOException("Connection refused"))
        assertEquals("Сопряжение: Pair завершился → Connection refused", AdbEndpoint.describeFailure("Сопряжение", failure))
    }
}
