package io.github.dzhokhov.quotes.sources

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.URL
import java.net.UnknownHostException

sealed class HttpResult {
    data class Response(val code: Int, val body: String, val bytes: Int) : HttpResult()
    data class Error(val exception: Exception) : HttpResult()
}

/** GET с исключениями как результатами — подменяется в тестах. */
interface HttpClient {
    fun get(url: String): HttpResult
}

/** Класс «нет связи»: DNS, отказ соединения, нет маршрута, сокет без ответа. Таймаут чтения — «не удалось обновить». */
fun isNoNetwork(e: Exception): Boolean = e is UnknownHostException || e is SocketException

/** HttpURLConnection: таймауты 10/15 с, Accept JSON, User-Agent, UTF-8, предел 1 МБ, только HTTPS. */
class HttpUrlConnectionClient(private val userAgent: String) : HttpClient {
    override fun get(url: String): HttpResult {
        return try {
            if (!url.startsWith("https://")) throw IOException("only https is allowed: $url")
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", userAgent)
                val code = conn.responseCode
                val stream = if (code >= 400) conn.errorStream else conn.inputStream
                val bytes = stream?.use { readLimited(it) } ?: ByteArray(0)
                HttpResult.Response(code, String(bytes, Charsets.UTF_8), bytes.size)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            HttpResult.Error(e)
        }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BODY_BYTES) throw IOException("body exceeds $MAX_BODY_BYTES bytes")
        }
        return out.toByteArray()
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 15_000
        const val MAX_BODY_BYTES = 1_048_576
    }
}
