package com.dzhokhov.currencyrates.storage

import com.dzhokhov.currencyrates.core.Origin
import com.dzhokhov.currencyrates.core.RateSet
import com.dzhokhov.currencyrates.core.json.JsonValue
import com.dzhokhov.currencyrates.log.RatesLog
import com.dzhokhov.currencyrates.sources.RateSources
import java.time.Instant

/** Кэш наборов rates/<sourceId>.json (сырой ответ в конверте); при отсутствии или негодности — встроенный набор. */
class RateSetStore(private val files: JsonFiles, private val embedded: EmbeddedAssets, private val log: RatesLog) {

    fun fileName(sourceId: String) = "rates/$sourceId.json"

    /** Действующий набор: кэш → встроенный; null только если негоден и встроенный (не ожидается). */
    fun load(sourceId: String): RateSet? {
        val name = fileName(sourceId)
        val obj = files.read(name)
        if (obj != null) {
            try {
                val id = (obj["sourceId"] as? JsonValue.JString)?.value
                val fetchedAt = (obj["fetchedAt"] as? JsonValue.JNumber)?.toBigDecimal()?.toLong()
                val body = (obj["body"] as? JsonValue.JString)?.value
                if (id != sourceId || fetchedAt == null || body == null) throw IllegalStateException("envelope fields")
                val parsed = RateSources.byId(sourceId).parse(body)
                return RateSet(sourceId, parsed.rows, Instant.ofEpochMilli(fetchedAt), Origin.CACHED)
            } catch (e: Exception) {
                log.event("storage_fallback", "RateSetStore", "load", "$sourceId;USD;daily", "file=$name reason=${e.javaClass.simpleName} message=${e.message ?: ""}", "use_embedded")
            }
        }
        return try {
            embedded.rateSet(sourceId)
        } catch (e: Exception) {
            log.event("storage_fallback", "RateSetStore", "load", "$sourceId;USD;daily", "file=assets/$name reason=${e.javaClass.simpleName} message=${e.message ?: ""}", "no_set")
            null
        }
    }

    fun save(sourceId: String, fetchedAt: Instant, url: String, body: String): Int =
        files.write(
            fileName(sourceId),
            JsonValue.JObject(
                linkedMapOf(
                    "schemaVersion" to JsonValue.JNumber(JsonFiles.SCHEMA_VERSION.toString()),
                    "sourceId" to JsonValue.JString(sourceId),
                    "fetchedAt" to JsonValue.JNumber(fetchedAt.toEpochMilli().toString()),
                    "url" to JsonValue.JString(url),
                    "body" to JsonValue.JString(body),
                ),
            ),
            "rates",
        )
}
