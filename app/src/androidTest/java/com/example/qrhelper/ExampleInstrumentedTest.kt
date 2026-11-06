package com.example.qrhelper

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertEquals

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test fun appContext_isCorrect() { assertEquals("com.example.qrhelper", androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName) }
}
