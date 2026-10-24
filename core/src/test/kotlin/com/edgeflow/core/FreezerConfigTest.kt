package com.edgeflow.core

import com.edgeflow.core.model.FreezerConfig
import com.edgeflow.core.model.afterUnfreeze
import kotlin.test.Test
import kotlin.test.assertEquals

class FreezerConfigTest {

    @Test
    fun `failed unfreeze leaves package in freezer config`() {
        val config = FreezerConfig(frozen = setOf("com.example.frozen", "com.example.other"))

        assertEquals(config, config.afterUnfreeze("com.example.frozen", succeeded = false))
    }

    @Test
    fun `successful unfreeze removes only the selected package`() {
        val config = FreezerConfig(frozen = setOf("com.example.frozen", "com.example.other"))

        assertEquals(
            setOf("com.example.other"),
            config.afterUnfreeze("com.example.frozen", succeeded = true).frozen,
        )
    }
}
