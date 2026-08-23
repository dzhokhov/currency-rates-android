package io.github.dzhokhov.quotes.storage

import io.github.dzhokhov.quotes.TestFiles
import io.github.dzhokhov.quotes.core.CurrencyRegistry
import io.github.dzhokhov.quotes.core.Origin
import io.github.dzhokhov.quotes.log.ListRatesLog
import io.github.dzhokhov.quotes.sources.CurrencyApiSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.time.Instant

class RateSetStoreTest {
    private lateinit var dir: File
    private lateinit var log: ListRatesLog
    private lateinit var store: RateSetStore

    @Before fun setUp() {
        dir = Files.createTempDirectory("rates-sets").toFile()
        log = ListRatesLog()
        val assetsDir = listOf(File("src/main/assets"), File("app/src/main/assets")).first { it.isDirectory }
        store = RateSetStore(JsonFiles(dir, log), EmbeddedAssets(DirectoryAssetReader(assetsDir)), log)
    }

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun emptyCacheGivesEmbeddedSets() {
        val f = store.load(CurrencyRegistry.FRANKFURTER)!!
        assertEquals(Origin.EMBEDDED, f.origin)
        assertNull(f.fetchedAt)
        assertEquals(165, f.rows.size)
        val c = store.load(CurrencyRegistry.CURRENCY_API)!!
        assertEquals(Origin.EMBEDDED, c.origin)
        assertNotNull(c.rows["BTC"])
        assertTrue(log.lines.none { it.contains("storage_fallback") })
    }

    @Test fun savedResponseIsLoadedAsCached() {
        val at = Instant.ofEpochMilli(1756000000000)
        val body = TestFiles.currencyApiBody
        store.save(CurrencyRegistry.CURRENCY_API, at, "https://latest.currency-api.pages.dev/v1/currencies/usd.json", body)
        assertTrue(File(dir, "rates/currency-api.json").exists())
        val c = store.load(CurrencyRegistry.CURRENCY_API)!!
        assertEquals(Origin.CACHED, c.origin)
        assertEquals(at, c.fetchedAt)
        assertEquals(CurrencyApiSource.parse(body).rows, c.rows)
        assertTrue(log.lines.any { it.contains("step_id=store") && it.contains("file=rates/currency-api.json") })
    }

    @Test fun corruptedCacheFallsBackToEmbedded() {
        File(dir, "rates").mkdirs()
        File(dir, "rates/frankfurter-v2.json").writeText("{\"schemaVersion\":1,\"sourceId\":\"frankfurter-v2\",\"fetchedAt\":1,\"body\":\"[{\\\"date\\\":\\\"2026-08-22\\\",\\\"base\\\":\\\"USD\\\",\\\"quote\\\":\\\"EUR\\\",\\\"rate\\\":0}]\"}")
        val f = store.load(CurrencyRegistry.FRANKFURTER)!!
        assertEquals(Origin.EMBEDDED, f.origin)
        assertTrue(log.lines.any { it.contains("storage_fallback") && it.contains("SourceFormatException") })
        log.lines.clear()
        File(dir, "rates/frankfurter-v2.json").writeText("not json at all")
        assertEquals(Origin.EMBEDDED, store.load(CurrencyRegistry.FRANKFURTER)!!.origin)
        assertTrue(log.lines.any { it.contains("storage_fallback") && it.contains("json_parse") })
        log.lines.clear()
        File(dir, "rates/frankfurter-v2.json").writeText("{\"schemaVersion\":1,\"sourceId\":\"currency-api\",\"fetchedAt\":1,\"body\":\"[]\"}")
        assertEquals(Origin.EMBEDDED, store.load(CurrencyRegistry.FRANKFURTER)!!.origin)
        assertTrue(log.lines.any { it.contains("storage_fallback") })
    }
}
