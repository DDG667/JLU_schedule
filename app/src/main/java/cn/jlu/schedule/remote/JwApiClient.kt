package cn.jlu.schedule.remote

import android.content.Context
import android.webkit.CookieManager
import cn.jlu.schedule.auth.CampusCookieJar
import cn.jlu.schedule.auth.CasClient
import cn.jlu.schedule.auth.CasLoginResult
import cn.jlu.schedule.auth.JluCredentialStore
import cn.jlu.schedule.auth.TpassConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 教务平台原生 HTTP 客户端单例：持久化 CookieJar + 统一 UA。
 * 首次登录由内嵌 WebView 完成，登录成功后把 WebView Cookie 迁移进来。
 */
object JwApiClient {

    @Volatile
    private var cached: OkHttpClient? = null

    fun get(context: Context): OkHttpClient {
        return cached ?: synchronized(this) {
            cached ?: OkHttpClient.Builder()
                .cookieJar(cookieJar(context))
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("User-Agent", TpassConfig.USER_AGENT)
                            .build()
                    )
                }
                .build()
                .also { cached = it }
        }
    }

    fun cookieJar(context: Context): CampusCookieJar =
        CampusCookieJar(File(context.filesDir, "auth/cookies.json"))

    /**
     * 把 WebView CookieManager 中目标域名的会话迁移进原生 CookieJar（首次登录后调用）。
     * @return 迁移的 Cookie 条数
     */
    fun importWebViewCookies(context: Context, targetUrl: String): Int {
        val manager = CookieManager.getInstance()
        val header = runCatching { manager.getCookie(targetUrl) }.getOrNull() ?: return 0
        val url: HttpUrl = targetUrl.toHttpUrl()
        return cookieJar(context).importFromCookieHeader(url, header)
    }

    /** 用存储的加密凭据静默重登（会话失效时调用） */
    suspend fun silentLogin(context: Context): CasLoginResult {
        val credentials = JluCredentialStore.load(context)
            ?: return CasLoginResult.NeedsManualLogin
        return CasClient(get(context)).login(credentials.studentId, credentials.password)
    }

    /** 清空会话（登出） */
    fun clearSession(context: Context) {
        cookieJar(context).clear()
    }

    /** iedu 域是否还有可用会话 Cookie（用于设置页状态展示） */
    fun hasSession(context: Context): Boolean {
        val url = runCatching { TpassConfig.IEDU_PORTAL_URL.toHttpUrl() }.getOrNull() ?: return false
        return cookieJar(context).loadForRequest(url).isNotEmpty()
    }
}
