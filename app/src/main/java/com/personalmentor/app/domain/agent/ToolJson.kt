package com.personalmentor.app.domain.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Small helpers shared by the browser and GitHub tool executors. */

internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

internal fun JsonObject.flag(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

/** Copies only the listed keys (skipping nulls) so tool results stay small. */
internal fun JsonObject.pick(vararg keys: String): JsonObject =
    JsonObject(keys.mapNotNull { k -> this[k]?.takeIf { it !is JsonNull }?.let { k to it } }.toMap())

internal fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement?.asArray(): JsonArray? = this as? JsonArray

internal fun toolOk(block: JsonObjectBuilder.() -> Unit = {}): String =
    buildJsonObject { put("ok", true); block() }.toString()

internal fun toolFail(message: String): String =
    buildJsonObject { put("ok", false); put("error", message) }.toString()

internal const val DECLINED_MESSAGE =
    "The user declined this action. Do not retry it; ask the user what they would like instead."
