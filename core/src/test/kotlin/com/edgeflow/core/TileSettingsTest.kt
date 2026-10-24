package com.edgeflow.core

import com.edgeflow.core.model.AppSettings
import com.edgeflow.core.model.EdgeConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TileSettingsTest {

    @Test
    fun `quick settings action selection survives config serialization`() {
        val config = EdgeConfig(settings = AppSettings(quickSettingsTileActionId = 42))
        val encoded = ConfigCodec.json.encodeToString(EdgeConfig.serializer(), config)

        assertEquals(config, ConfigCodec.json.decodeFromString(EdgeConfig.serializer(), encoded))
    }

    @Test
    fun `legacy config preserves all previously exposed system tile components`() {
        assertEquals(12, AppSettings().effectiveTileCount())
        val legacy = ConfigCodec.json.decodeFromString<EdgeConfig>("{}")
        assertEquals(12, legacy.settings.effectiveTileCount())
    }

    @Test
    fun `tile count can be zero or twelve without losing hidden action bindings`() {
        val settings = AppSettings(quickSettingsTileCount = 2)
            .withTileAction(11, 92)
        assertEquals(2, settings.effectiveTileCount())
        assertEquals(0, settings.withTileCount(0).effectiveTileCount())
        assertEquals(92, settings.withTileCount(0).actionForTile(11))
        assertEquals(12, settings.withTileCount(12).effectiveTileCount())
        val restored = ConfigCodec.json.decodeFromString<EdgeConfig>(
            ConfigCodec.json.encodeToString(EdgeConfig.serializer(), EdgeConfig(settings = settings)),
        )
        assertEquals(2, restored.settings.effectiveTileCount())
        assertEquals(92, restored.settings.actionForTile(11))
    }

    @Test
    fun `older configs default to no quick settings action`() {
        val oldConfig = ConfigCodec.json.decodeFromString<EdgeConfig>("{}")

        assertNull(oldConfig.settings.quickSettingsTileActionId)
    }

    @Test
    fun `twelve tile slots store independent actions and survive serialization`() {
        val settings = (1..12).fold(AppSettings()) { state, slot -> state.withTileAction(slot, slot * 10) }
        val config = EdgeConfig(settings = settings)
        val decoded = ConfigCodec.json.decodeFromString<EdgeConfig>(
            ConfigCodec.json.encodeToString(EdgeConfig.serializer(), config),
        )
        for (slot in 1..12) assertEquals(slot * 10, decoded.settings.actionForTile(slot))
        assertNull(decoded.settings.actionForTile(13))
        assertNull(decoded.settings.withTileAction(3, null).actionForTile(3))
    }

    @Test
    fun `tile one legacy choice can be replaced or cleared without reviving it`() {
        val old = AppSettings(quickSettingsTileActionId = 42)
        assertEquals(42, old.actionForTile(1))
        assertEquals(67, old.withTileAction(1, 67).actionForTile(1))
        assertNull(old.withTileAction(1, null).actionForTile(1))
    }
}
