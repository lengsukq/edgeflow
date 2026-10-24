package com.edgeflow.core

import kotlinx.serialization.json.Json

/**
 * The single JSON codec used for every persisted structure.
 *
 * Polymorphic action and condition trees rely on the `type` discriminator, so
 * class discriminators must stay enabled and the field names must not be
 * mangled.
 */
object ConfigCodec {
    val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        classDiscriminator = "type"
        prettyPrint = false
        explicitNulls = false
    }
}
