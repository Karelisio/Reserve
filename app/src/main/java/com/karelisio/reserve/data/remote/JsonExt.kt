package com.karelisio.reserve.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonElement?.obj(): JsonObject? = this as? JsonObject

internal fun JsonElement?.arr(): JsonArray? = this as? JsonArray

internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

internal fun JsonObject.dbl(key: String): Double? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.doubleOrNull ?: p.content.replace(',', '.').toDoubleOrNull()
}

internal fun JsonObject.lng(key: String): Long? {
    val p = this[key] as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.longOrNull ?: p.content.toDoubleOrNull()?.toLong()
}

/**
 * Some fields of the official feed are JSON documents serialized as strings, others are
 * already decoded by the API: accept both.
 */
internal fun JsonElement?.decodeEmbedded(): JsonElement? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> if (isString) runCatching { Http.json.parseToJsonElement(content) }.getOrNull() else null
    else -> this
}

/** A field that may be an object, an array of objects, or absent. */
internal fun JsonElement?.asObjectList(): List<JsonObject> = when (this) {
    is JsonArray -> mapNotNull { it as? JsonObject }
    is JsonObject -> listOf(this)
    else -> emptyList()
}

/** A field that may be an array of strings, a single string, or a delimited string. */
internal fun JsonElement?.asStringList(): List<String> = when (this) {
    is JsonArray -> mapNotNull { (it as? JsonPrimitive)?.content?.takeIf { s -> s.isNotBlank() } }
    is JsonPrimitive -> if (this is JsonNull) emptyList()
    else content.split(';', ',').map { it.trim() }.filter { it.isNotEmpty() }
    else -> emptyList()
}
