package com.bugsnag.android

import org.junit.Assert
import java.io.StringWriter

/**
 * Serializes a [JsonStream.Streamable] object into JSON and compares its equality against a JSON
 * test fixture loaded from resources
 */
internal fun verifyJsonMatches(streamable: JsonStream.Streamable, resourceName: String) {
    validateJson(resourceName, JsonParser().toJsonString(streamable))
}

internal fun verifyJsonMatches(map: Map<String, Any>, resourceName: String) {
    val stringWriter = StringWriter()
    val jsonStream = JsonStream(stringWriter)
    ObjectJsonStreamer().objectToStream(map, jsonStream)
    val json = stringWriter.toString()
    validateJson(resourceName, json)
}

/**
 * To help comparing JSON we remove any whitespace that hasn't been quoted. So:
 * ```
 * {
 *      "some key": "Some Value"
 * }
 * ```
 *
 * Becomes:
 * ```
 * {"some key":"Some Value"}
 * ```
 */
private fun removeUnquotedWhitespace(json: String): String {
    val builder = StringBuilder(json.length)
    var quoted = false
    var index = 0

    while (index < json.length) {
        val ch = json[index++]

        if (quoted) {
            when (ch) {
                '\"' -> quoted = false
                '\\' -> {
                    builder.append('\\')
                    builder.append(json[index++])
                }
            }

            builder.append(ch)
        } else if (!ch.isWhitespace()) {
            builder.append(ch)

            if (ch == '\"') {
                quoted = true
            }
        }
    }

    return builder.toString()
}

internal fun validateJson(resourceName: String, json: String) {
    val rawJson = JsonParser().read(resourceName)
    val expectedJson = removeUnquotedWhitespace(rawJson)
    val generatedJson = removeUnquotedWhitespace(json)
    Assert.assertEquals(expectedJson, generatedJson)
}

/**
 * Generates parameterised test cases from a variable number of [JsonStream.Streamable] elements.
 * The expected JSON file for each element should match the naming format
 * '$filename_serialization_$index.json'
 */
internal fun <T> generateSerializationTestCases(
    filename: String,
    vararg elements: T
): Collection<Pair<T, String>> {
    return elements.mapIndexed { index, obj ->
        Pair(obj, "${filename}_serialization_$index.json")
    }
}
