package io.github.dzhokhov.quotes.core.json

import io.github.dzhokhov.quotes.TestFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal

class MiniJsonTest {

    private fun assertFails(input: String, label: String = input.take(40)) {
        try {
            MiniJson.parse(input)
            fail("expected JsonParseException for: $label")
        } catch (e: JsonParseException) {
            assertTrue(e.offset >= 0)
        }
    }

    /** Все числовые литералы сырого текста собираются регулярным выражением и сверяются с деревом. */
    private fun collectNumbers(v: JsonValue, out: MutableList<JsonValue.JNumber>) {
        when (v) {
            is JsonValue.JObject -> v.fields.values.forEach { collectNumbers(it, out) }
            is JsonValue.JArray -> v.items.forEach { collectNumbers(it, out) }
            is JsonValue.JNumber -> out.add(v)
            else -> {}
        }
    }

    private val literalRegex = Regex("""(?<=[:,\[\s])-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?(?=[,\]}\s])""")

    private fun checkEveryLiteral(body: String) {
        val parsed = MiniJson.parse(body)
        val numbers = ArrayList<JsonValue.JNumber>()
        collectNumbers(parsed, numbers)
        val rawLiterals = literalRegex.findAll(body).map { it.value }.toList()
        assertTrue("no literals found", rawLiterals.isNotEmpty())
        assertEquals("number of literals", rawLiterals.size, numbers.size)
        rawLiterals.zip(numbers).forEach { (raw, n) ->
            assertEquals(raw, n.literal)
            val expected = BigDecimal(raw)
            val actual = n.toBigDecimal()
            assertEquals("value $raw", expected, actual)
            assertEquals("scale $raw", expected.scale(), actual.scale())
        }
    }

    @Test fun everyLiteralOfFrankfurterRates() = checkEveryLiteral(TestFiles.frankfurterBody)
    @Test fun everyLiteralOfCurrencyApi() = checkEveryLiteral(TestFiles.currencyApiBody)
    @Test fun everyLiteralOfCurrencies() {
        val v = MiniJson.parse(TestFiles.currenciesBody) as JsonValue.JArray
        assertEquals(165, v.items.size)
        // Не-ASCII символы валют проходят как есть
        val xau = v.items.map { it as JsonValue.JObject }.first { (it["iso_code"] as JsonValue.JString).value == "XAU" }
        assertEquals("Gold (Troy Ounce)", (xau["name"] as JsonValue.JString).value)
        val aed = v.items.map { it as JsonValue.JObject }.first { (it["iso_code"] as JsonValue.JString).value == "AED" }
        assertEquals("د.إ", (aed["symbol"] as JsonValue.JString).value)
    }

    @Test fun sixteenSignificantDigitsKeptExactly() {
        val usd = (MiniJson.parse(TestFiles.currencyApiBody) as JsonValue.JObject)["usd"] as JsonValue.JObject
        fun num(k: String) = (usd[k] as JsonValue.JNumber)
        assertEquals("47998502.53177214", num("trl").literal)
        assertEquals(BigDecimal("47998502.53177214"), num("trl").toBigDecimal())
        assertEquals(16, num("trl").toBigDecimal().precision())
        assertEquals(BigDecimal("77788599859.75133"), num("veb").toBigDecimal())
        assertEquals(16, num("veb").toBigDecimal().precision())
        assertEquals(BigDecimal("77788599.85975133"), num("vef").toBigDecimal())
        assertEquals(16, num("vef").toBigDecimal().precision())
        assertEquals(BigDecimal("0.000012799366"), num("btc").toBigDecimal())
        assertEquals(BigDecimal("2"), num("bbd").toBigDecimal())
        assertEquals(BigDecimal("1"), num("bmd").toBigDecimal())
        assertEquals(0, num("bmd").toBigDecimal().scale())
    }

    @Test fun frankfurterUsdRowIsOnePointZero() {
        val arr = MiniJson.parse(TestFiles.frankfurterBody) as JsonValue.JArray
        val usd = arr.items.map { it as JsonValue.JObject }.first { (it["quote"] as JsonValue.JString).value == "USD" }
        val rate = usd["rate"] as JsonValue.JNumber
        assertEquals("1.0", rate.literal)
        assertEquals(BigDecimal("1.0"), rate.toBigDecimal())
        assertEquals(1, rate.toBigDecimal().scale())
    }

    @Test fun exponentsAndSigns() {
        assertEquals(BigDecimal("1e-7"), (MiniJson.parse("1e-7") as JsonValue.JNumber).toBigDecimal())
        assertEquals(BigDecimal("2.5E+3"), (MiniJson.parse("2.5E+3") as JsonValue.JNumber).toBigDecimal())
        assertEquals(BigDecimal("-0.0"), (MiniJson.parse("-0.0") as JsonValue.JNumber).toBigDecimal())
        assertEquals(BigDecimal("0"), (MiniJson.parse("0") as JsonValue.JNumber).toBigDecimal())
        assertEquals("-12.5e3", (MiniJson.parse("  -12.5e3 ") as JsonValue.JNumber).literal)
    }

    @Test fun stringsWithEscapes() {
        val s = MiniJson.parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u0431\\ud83c\\uddfa\"") as JsonValue.JString
        assertEquals("a\"b\\c/d\b" + 0x0C.toChar() + "\n\r\tб🇺", s.value)
        val raw = MiniJson.parse("\"дин. ₿ د.إ\"") as JsonValue.JString
        assertEquals("дин. ₿ د.إ", raw.value)
    }

    @Test fun literalsBooleansNullObjectsArrays() {
        val v = MiniJson.parse("{\"a\":[true,false,null,{}],\"b\":{\"c\":[]},\"a\":1}") as JsonValue.JObject
        assertEquals(2, v.fields.size)
        assertEquals(JsonValue.JNumber("1"), v["a"]) // Повторный ключ — последний
        val b = v["b"] as JsonValue.JObject
        assertEquals(JsonValue.JArray(emptyList()), b["c"])
    }

    @Test fun everyPrefixOfSubsetFails() {
        val subset = TestFiles.sample("frankfurter-v2-rates-usd-subset.json").trimEnd()
        MiniJson.parse(subset) // Полный текст разбирается
        for (n in 0 until subset.length) {
            assertFails(subset.substring(0, n), "prefix $n")
        }
    }

    @Test fun brokenInputs() {
        assertFails("")
        assertFails("   ")
        assertFails("[1] x")
        assertFails("{} {}")
        assertFails("01")
        assertFails(".5")
        assertFails("1.")
        assertFails("+1")
        assertFails("NaN")
        assertFails("Infinity")
        assertFails("-")
        assertFails("1e")
        assertFails("// c\n1")
        assertFails("[1,2] // c")
        assertFails("{'a':1}")
        assertFails("[1,2,]")
        assertFails("{\"a\":1,}")
        assertFails("\"abc")
        assertFails("\"a\\qb\"")
        assertFails("\"a\\u12\"")
        assertFails("\"a\\uzzzz\"")
        assertFails("\"a\nb\"")
        assertFails("[tru]")
        assertFails("nul")
        assertFails("{\"a\" 1}")
        assertFails("{1:2}")
    }

    @Test fun depthLimit() {
        val ok = "[".repeat(32) + "]".repeat(32)
        MiniJson.parse(ok)
        assertFails("[".repeat(33) + "]".repeat(33), "depth 33")
    }

    @Test fun sizeLimit() {
        val big = "\"" + "a".repeat(MiniJson.MAX_INPUT_CHARS - 2) + "\""
        assertEquals(MiniJson.MAX_INPUT_CHARS, big.length)
        MiniJson.parse(big)
        assertFails(big + " ", "1 MiB + 1")
    }

    @Test fun numberLiteralLimit() {
        val ok = "1" + "0".repeat(63)
        assertEquals(64, ok.length)
        assertEquals(BigDecimal(ok), (MiniJson.parse(ok) as JsonValue.JNumber).toBigDecimal())
        assertFails("1" + "0".repeat(64), "65-char literal")
    }

    @Test fun bomIsSkippedAndWhitespaceAccepted() {
        val v = MiniJson.parse(0xFEFF.toChar() + " \t\r\n[ 1 ,\t2\n]\r\n")
        assertEquals(JsonValue.JArray(listOf(JsonValue.JNumber("1"), JsonValue.JNumber("2"))), v)
    }

    @Test fun writeThenParseRoundTrip() {
        val body = "{\"q\":\"a\\\"b\",\"s\":\"back\\\\slash\",\"t\":\"tab\\there\",\"u\":\"дин. ₿\"}"
        val state = JsonValue.JObject(
            linkedMapOf(
                "schemaVersion" to JsonValue.JNumber("1"),
                "rows" to JsonValue.JArray(listOf(JsonValue.JString("USD"), JsonValue.JString("EUR"))),
                "base" to JsonValue.JString("RSD"),
                "amount" to JsonValue.JString("1000"),
                "amountTyped" to JsonValue.JBool(true),
                "amountText" to JsonValue.JNull,
            ),
        )
        val refresh = JsonValue.JObject(
            linkedMapOf(
                "schemaVersion" to JsonValue.JNumber("1"),
                "lastAttemptAt" to JsonValue.JNumber("1756000000000"),
                "lastFullSuccessAt" to JsonValue.JNull,
                "perSource" to JsonValue.JObject(mapOf("frankfurter-v2" to JsonValue.JString("ok"))),
            ),
        )
        val envelope = JsonValue.JObject(
            linkedMapOf(
                "schemaVersion" to JsonValue.JNumber("1"),
                "sourceId" to JsonValue.JString("currency-api"),
                "fetchedAt" to JsonValue.JNumber("1756000000000"),
                "url" to JsonValue.JString("https://x/y?base=USD"),
                "body" to JsonValue.JString(body + "\n" + 0x0C.toChar()),
            ),
        )
        for (v in listOf(state, refresh, envelope)) {
            val text = MiniJson.write(v)
            assertEquals(v, MiniJson.parse(text))
        }
        // Сырое тело ответа переживает конверт побайтно
        val wrapped = MiniJson.write(JsonValue.JObject(mapOf("body" to JsonValue.JString(TestFiles.currencyApiBody))))
        assertEquals(TestFiles.currencyApiBody, ((MiniJson.parse(wrapped) as JsonValue.JObject)["body"] as JsonValue.JString).value)
    }
}
