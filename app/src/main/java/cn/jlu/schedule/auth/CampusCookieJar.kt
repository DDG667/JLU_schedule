package cn.jlu.schedule.auth

import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.io.File

/**
 * 吉大域名的持久化 CookieJar：仅保留 *.jlu.edu.cn 下的会话 Cookie，
 * 落盘到 filesDir/auth/cookies.json（该目录已在备份规则中排除），
 * 冷启动时恢复，最大限度延续登录态；失效后由上层走静默重登。
 */
class CampusCookieJar(private val storeFile: File) : CookieJar {

    @Serializable
    private data class StoredCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String,
        val expiresAt: Long,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean
    )

    private val lock = Any()
    private val cookies = LinkedHashMap<String, Cookie>()
    private var loaded = false

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            ensureLoaded()
            cookies.forEach { cookie ->
                if (!isAllowed(cookie)) return@forEach
                val key = cookieKey(cookie)
                if (cookie.expiresAt <= System.currentTimeMillis()) {
                    this.cookies.remove(key)
                } else {
                    this.cookies[key] = cookie
                }
            }
            trimToLimit()
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        synchronized(lock) {
            ensureLoaded()
            val now = System.currentTimeMillis()
            val matched = mutableListOf<Cookie>()
            val expired = mutableListOf<String>()
            cookies.forEach { (key, cookie) ->
                if (cookie.expiresAt <= now) {
                    expired += key
                } else if (cookie.matches(url)) {
                    matched += cookie
                }
            }
            if (expired.isNotEmpty()) {
                expired.forEach { cookies.remove(it) }
                persist()
            }
            return matched
        }
    }

    /** 把 WebView CookieManager 里的会话迁移进原生 CookieJar，返回迁移条数 */
    fun importFromCookieHeader(url: HttpUrl, header: String?): Int {
        if (header.isNullOrBlank()) return 0
        var imported = 0
        synchronized(lock) {
            ensureLoaded()
            header.split(";").forEach { part ->
                val cookie = Cookie.parse(url, part.trim()) ?: return@forEach
                if (isAllowed(cookie) && cookie.expiresAt > System.currentTimeMillis()) {
                    cookies[cookieKey(cookie)] = cookie
                    imported++
                }
            }
            if (imported > 0) {
                trimToLimit()
                persist()
            }
        }
        return imported
    }

    fun clear() {
        synchronized(lock) {
            cookies.clear()
            persist()
        }
    }

    private fun isAllowed(cookie: Cookie): Boolean =
        cookie.domain.endsWith(ALLOWED_DOMAIN_SUFFIX, ignoreCase = true)

    private fun cookieKey(cookie: Cookie): String =
        "${cookie.domain}|${cookie.path}|${cookie.name}"

    private fun trimToLimit() {
        while (cookies.size > MAX_COOKIES) {
            val eldest = cookies.keys.firstOrNull() ?: break
            cookies.remove(eldest)
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        if (!storeFile.exists()) return
        runCatching {
            val stored = json.decodeFromString<List<StoredCookie>>(storeFile.readText())
            stored.forEach { entry ->
                val builder = Cookie.Builder()
                    .name(entry.name)
                    .value(entry.value)
                    .path(entry.path)
                    .expiresAt(entry.expiresAt)
                if (entry.hostOnly) builder.hostOnlyDomain(entry.domain) else builder.domain(entry.domain)
                if (entry.secure) builder.secure()
                if (entry.httpOnly) builder.httpOnly()
                val cookie = builder.build()
                cookies[cookieKey(cookie)] = cookie
            }
        }.onFailure { Log.w(TAG, "恢复会话 Cookie 失败，忽略旧会话", it) }
    }

    private fun persist() {
        runCatching {
            storeFile.parentFile?.mkdirs()
            val tmp = File(storeFile.parentFile, storeFile.name + ".${System.nanoTime()}.tmp")
            val payload = cookies.values.map { cookie ->
                StoredCookie(
                    name = cookie.name,
                    value = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly
                )
            }
            tmp.writeText(json.encodeToString(payload))
            if (!tmp.renameTo(storeFile)) {
                storeFile.delete()
                tmp.renameTo(storeFile)
            }
        }.onFailure { Log.w(TAG, "会话 Cookie 落盘失败", it) }
    }

    companion object {
        private const val TAG = "CampusCookieJar"
        const val ALLOWED_DOMAIN_SUFFIX = "jlu.edu.cn"
        const val MAX_COOKIES = 300
    }
}
