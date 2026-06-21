package me.jfenn.bingo.common.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.InputStream
import kotlin.text.Charsets.UTF_8

val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = true
}

val jsonUnpretty = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    prettyPrint = false
}

val jsonStrict = Json(json) {
    ignoreUnknownKeys = false
}

fun InputStream.readUtf8TextWithoutBom(): String {
    return readBytes().toString(UTF_8).removePrefix("\uFEFF")
}

fun <T> Json.decodeFromUtf8Stream(
    serializer: KSerializer<T>,
    stream: InputStream,
): T {
    return decodeFromString(serializer, stream.readUtf8TextWithoutBom())
}

inline fun <reified T> Json.decodeFromUtf8Stream(stream: InputStream): T {
    return decodeFromString(serializer<T>(), stream.readUtf8TextWithoutBom())
}
