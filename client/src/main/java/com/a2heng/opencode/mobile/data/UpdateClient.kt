package com.a2heng.opencode.mobile.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 从 GitHub Releases 检查/下载更新（公开仓库，不需要 token）。 */
object UpdateClient {

    private const val API = "https://api.github.com/repos/a2heng/android-dev-in-android/releases/latest"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): UpdateInfo? {
        val request = Request.Builder()
            .url(API)
            .header("Accept", "application/vnd.github+json")
            .build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val body = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
            val tag = (body["tag_name"] as? JsonPrimitive)?.content ?: return null
            val page = (body["html_url"] as? JsonPrimitive)?.content ?: return null
            val assets = body["assets"]?.jsonArray ?: return UpdateInfo(tag, page, null)
            val apk = assets.mapNotNull { it as? JsonObject }.firstOrNull { asset ->
                ((asset["name"] as? JsonPrimitive)?.content ?: "").endsWith(".apk")
            }?.let { (it["browser_download_url"] as? JsonPrimitive)?.content }
            return UpdateInfo(tag, page, apk)
        }
    }

    fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        http.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}")
            val total = resp.body?.contentLength() ?: -1L
            dest.outputStream().use { out ->
                val input = resp.body!!.byteStream()
                val buffer = ByteArray(64 * 1024)
                var read = 0L
                var lastPercent = -1
                while (true) {
                    val n = input.read(buffer)
                    if (n <= 0) break
                    out.write(buffer, 0, n)
                    read += n
                    if (total > 0) {
                        val percent = (read * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }
        }
    }

    /** local 0.1.3 / remote v0.1.4 → true */
    fun isNewer(local: String, remoteTag: String): Boolean {
        fun parse(v: String): List<Int>? =
            v.removePrefix("v").substringBefore('-').split('.').mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }
        val a = parse(local) ?: return false
        val b = parse(remoteTag) ?: return false
        for (i in 0..2) {
            if (b[i] != a[i]) return b[i] > a[i]
        }
        return false
    }
}
