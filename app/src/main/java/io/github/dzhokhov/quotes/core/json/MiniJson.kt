package io.github.dzhokhov.quotes.core.json

import java.math.BigDecimal

/** Дерево JSON. Числа хранятся текстом литерала и переводятся в BigDecimal без потерь. */
sealed class JsonValue {
    data class JObject(val fields: Map<String, JsonValue>) : JsonValue() {
        operator fun get(key: String): JsonValue? = fields[key]
    }

    data class JArray(val items: List<JsonValue>) : JsonValue()
    data class JString(val value: String) : JsonValue()
    data class JNumber(val literal: String) : JsonValue() {
        fun toBigDecimal(): BigDecimal = BigDecimal(literal)
    }

    data class JBool(val value: Boolean) : JsonValue()
    object JNull : JsonValue() {
        override fun toString(): String = "JNull"
    }
}

class JsonParseException(val offset: Int, message: String) : Exception("$message at offset $offset")

/**
 * Собственный минимальный разборщик и писатель JSON (RFC 8259) без библиотек и без double.
 * Пределы: вход ≤ 1 МБ символов, вложенность ≤ 32, литерал числа ≤ 64 символов.
 */
object MiniJson {
    const val MAX_INPUT_CHARS = 1_048_576
    const val MAX_DEPTH = 32
    const val MAX_NUMBER_LITERAL = 64
    private val BOM: Char = 0xFEFF.toChar()
    private val FORM_FEED: Char = 0x0C.toChar()

    fun parse(text: String): JsonValue {
        if (text.length > MAX_INPUT_CHARS) throw JsonParseException(0, "input exceeds $MAX_INPUT_CHARS chars")
        val p = Parser(text)
        p.skipBom()
        p.skipWs()
        val value = p.readValue(0)
        p.skipWs()
        if (!p.atEnd()) throw JsonParseException(p.pos, "trailing characters after value")
        return value
    }

    fun write(value: JsonValue): String {
        val sb = StringBuilder()
        writeTo(sb, value)
        return sb.toString()
    }

    private fun writeTo(sb: StringBuilder, v: JsonValue) {
        when (v) {
            is JsonValue.JObject -> {
                sb.append('{')
                var first = true
                for ((k, fv) in v.fields) {
                    if (!first) sb.append(',')
                    first = false
                    writeString(sb, k)
                    sb.append(':')
                    writeTo(sb, fv)
                }
                sb.append('}')
            }
            is JsonValue.JArray -> {
                sb.append('[')
                v.items.forEachIndexed { i, item ->
                    if (i > 0) sb.append(',')
                    writeTo(sb, item)
                }
                sb.append(']')
            }
            is JsonValue.JString -> writeString(sb, v.value)
            is JsonValue.JNumber -> sb.append(v.literal)
            is JsonValue.JBool -> sb.append(if (v.value) "true" else "false")
            JsonValue.JNull -> sb.append("null")
        }
    }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == FORM_FEED -> sb.append("\\f")
                c < ' ' -> sb.append("\\u").append(String.format("%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var pos = 0

        fun atEnd() = pos >= s.length

        fun skipBom() {
            if (pos < s.length && s[pos] == BOM) pos++
        }

        fun skipWs() {
            while (pos < s.length) {
                val c = s[pos]
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') pos++ else break
            }
        }

        private fun fail(msg: String): Nothing = throw JsonParseException(pos, msg)

        private fun peek(): Char {
            if (pos >= s.length) fail("unexpected end of input")
            return s[pos]
        }

        fun readValue(depth: Int): JsonValue {
            if (depth >= MAX_DEPTH) fail("nesting deeper than $MAX_DEPTH")
            return when (val c = peek()) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.JString(readString())
                't' -> { expectWord("true"); JsonValue.JBool(true) }
                'f' -> { expectWord("false"); JsonValue.JBool(false) }
                'n' -> { expectWord("null"); JsonValue.JNull }
                '-', in '0'..'9' -> readNumber()
                else -> fail("unexpected character '$c'")
            }
        }

        private fun expectWord(w: String) {
            if (s.regionMatches(pos, w, 0, w.length)) pos += w.length else fail("invalid literal")
        }

        private fun readObject(depth: Int): JsonValue.JObject {
            pos++ // {
            val map = LinkedHashMap<String, JsonValue>()
            skipWs()
            if (peek() == '}') { pos++; return JsonValue.JObject(map) }
            while (true) {
                skipWs()
                if (peek() != '"') fail("expected string key")
                val key = readString()
                skipWs()
                if (peek() != ':') fail("expected ':'")
                pos++
                skipWs()
                map[key] = readValue(depth + 1) // Повторный ключ — последний побеждает
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return JsonValue.JObject(map) }
                    else -> fail("expected ',' or '}'")
                }
            }
        }

        private fun readArray(depth: Int): JsonValue.JArray {
            pos++ // [
            val list = ArrayList<JsonValue>()
            skipWs()
            if (peek() == ']') { pos++; return JsonValue.JArray(list) }
            while (true) {
                skipWs()
                list.add(readValue(depth + 1))
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return JsonValue.JArray(list) }
                    else -> fail("expected ',' or ']'")
                }
            }
        }

        private fun readString(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) fail("unterminated string")
                val c = s[pos]
                when {
                    c == '"' -> { pos++; return sb.toString() }
                    c == '\\' -> {
                        pos++
                        if (pos >= s.length) fail("unterminated escape")
                        when (val e = s[pos]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append(FORM_FEED)
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 >= s.length) fail("truncated unicode escape")
                                val hex = s.substring(pos + 1, pos + 5)
                                if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) fail("invalid unicode escape")
                                sb.append(hex.toInt(16).toChar())
                                pos += 4
                            }
                            else -> fail("unknown escape '\\$e'")
                        }
                        pos++
                    }
                    c < ' ' -> fail("raw control character in string")
                    else -> { sb.append(c); pos++ }
                }
            }
        }

        private fun readNumber(): JsonValue.JNumber {
            val start = pos
            if (s[pos] == '-') pos++
            if (pos >= s.length) fail("invalid number")
            if (s[pos] == '0') {
                pos++
            } else if (s[pos] in '1'..'9') {
                while (pos < s.length && s[pos].isAsciiDigit()) pos++
            } else fail("invalid number")
            if (pos < s.length && s[pos] == '.') {
                pos++
                if (pos >= s.length || !s[pos].isAsciiDigit()) fail("digit expected after '.'")
                while (pos < s.length && s[pos].isAsciiDigit()) pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                if (pos >= s.length || !s[pos].isAsciiDigit()) fail("digit expected in exponent")
                while (pos < s.length && s[pos].isAsciiDigit()) pos++
            }
            if (pos - start > MAX_NUMBER_LITERAL) throw JsonParseException(start, "number literal longer than $MAX_NUMBER_LITERAL")
            return JsonValue.JNumber(s.substring(start, pos))
        }

        private fun Char.isAsciiDigit() = this in '0'..'9'
    }
}
