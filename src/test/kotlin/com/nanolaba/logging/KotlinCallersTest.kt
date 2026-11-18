package com.nanolaba.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Runs the calls of KotlinCallers once; compiling them is the check.
 */
class KotlinCallersTest {

    @Test
    fun theCallsOfKotlinRun() {
        MemoryLogger.install().use { memory ->
            KotlinCallers.levels()
            KotlinCallers.views()
            assertEquals(3 + 4 + 5 + 6, KotlinCallers.blocks())
            assertTrue(memory.messages.contains("lazy 42"), memory.messages.toString())
            assertTrue(memory.messages.contains("in a unit"), memory.messages.toString())
        }
    }
}
