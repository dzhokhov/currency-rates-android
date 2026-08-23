package com.dzhokhov.currencyrates.storage

import com.dzhokhov.currencyrates.core.json.JsonParseException
import com.dzhokhov.currencyrates.core.json.JsonValue
import com.dzhokhov.currencyrates.core.json.MiniJson
import com.dzhokhov.currencyrates.log.RatesLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * JSON-файлы в filesDir: атомарная запись «уникальный временный файл → fsync → переименование»,
 * Чтение с проверкой schemaVersion; любая ошибка чтения → null и storage_fallback в журнале.
 */
class JsonFiles(private val dir: File, private val log: RatesLog) {
    init {
        dir.mkdirs()
        cleanupOrphans()
    }

    /** Осиротевшие *.tmp-* удаляются при старте. */
    fun cleanupOrphans(): Int {
        var removed = 0
        dir.walkTopDown().filter { it.isFile && it.name.contains(".tmp-") }.forEach { if (it.delete()) removed++ }
        return removed
    }

    fun exists(name: String): Boolean = File(dir, name).exists()

    fun read(name: String, expectedSchema: Int = SCHEMA_VERSION): JsonValue.JObject? {
        val file = File(dir, name)
        if (!file.exists()) return null
        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            fallback(name, "io", e.message)
            return null
        }
        val value = try {
            MiniJson.parse(text)
        } catch (e: JsonParseException) {
            fallback(name, "json_parse offset=${e.offset}", e.message)
            return null
        }
        val obj = value as? JsonValue.JObject
        if (obj == null) {
            fallback(name, "json_parse", "root is not an object")
            return null
        }
        val schema = (obj["schemaVersion"] as? JsonValue.JNumber)?.literal?.toIntOrNull()
        if (schema == null || schema > expectedSchema) {
            fallback(name, "schema", "schemaVersion=$schema expected<=$expectedSchema")
            return null
        }
        return obj
    }

    /** Атомарная запись; возвращает число записанных байт. */
    fun write(name: String, value: JsonValue, reason: String): Int {
        val target = File(dir, name)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp-${UUID.randomUUID()}")
        val bytes = MiniJson.write(value).toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(tmp).use { out ->
                out.write(bytes)
                out.fd.sync()
            }
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        log.event("store", "JsonFiles", "write", "-", "file=$name bytes=${bytes.size} reason=$reason", "continue")
        return bytes.size
    }

    fun delete(name: String): Boolean = File(dir, name).delete()

    private fun fallback(name: String, reason: String, message: String?) {
        log.event("storage_fallback", "JsonFiles", "read", "-", "file=$name reason=$reason message=${message ?: ""}", "use_default")
    }

    companion object {
        const val SCHEMA_VERSION = 1
    }
}
