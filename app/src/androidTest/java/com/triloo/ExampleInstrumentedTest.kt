package com.triloo

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Пример инструментального теста, который выполняется на Android-устройстве.
 *
 * Подробнее: [документация по тестированию](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        // Получаем context тестируемого приложения.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.triloo", appContext.packageName)
    }
}
