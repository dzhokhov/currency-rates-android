package com.dzhokhov.currencyrates.storage

import android.content.res.AssetManager
import com.dzhokhov.currencyrates.core.Origin
import com.dzhokhov.currencyrates.core.RateSet
import com.dzhokhov.currencyrates.sources.RateSources
import java.io.File

/** Чтение встроенных файлов; интерфейс — чтобы в JVM-тестах подставить папку assets. */
interface AssetReader {
    fun read(path: String): String
}

class AndroidAssetReader(private val assets: AssetManager) : AssetReader {
    override fun read(path: String): String = assets.open(path).use { it.readBytes().toString(Charsets.UTF_8) }
}

class DirectoryAssetReader(private val dir: File) : AssetReader {
    override fun read(path: String): String = File(dir, path).readText()
}

/** Встроенный стартовый набор: assets/rates/<sourceId>.json, fetchedAt = null. */
class EmbeddedAssets(private val reader: AssetReader) {
    fun ratesBody(sourceId: String): String = reader.read("rates/$sourceId.json")
    fun currenciesBody(): String = reader.read("currencies/frankfurter-v2.json")

    fun rateSet(sourceId: String): RateSet {
        val parsed = RateSources.byId(sourceId).parse(ratesBody(sourceId))
        return RateSet(sourceId, parsed.rows, fetchedAt = null, origin = Origin.EMBEDDED)
    }
}
