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
                .sslSocketFactory(trustingSslContext().socketFactory, trustingTrustManager())
                .hostnameVerifier { hostname, _ ->
                    // 校园站点使用私有 CA 证书（网页导入时用户已确认信任），
                    // 原生请求仅对 *.jlu.edu.cn 沿用该信任决策，其余主机一律拒绝
                    hostname?.endsWith(CampusCookieJar.ALLOWED_DOMAIN_SUFFIX, ignoreCase = true) == true
                }
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

    private fun trustingSslContext(): javax.net.ssl.SSLContext {
        return javax.net.ssl.SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<javax.net.ssl.TrustManager>(trustingTrustManager()), java.security.SecureRandom())
        }
    }

    private fun trustingTrustManager(): javax.net.ssl.X509TrustManager {
        return object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
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

    // 各业务应用会话 Cookie 的 path 互不相同，需按 URL 逐个取齐（CookieManager 按 path 过滤）
    private val WEBVIEW_IMPORT_URLS = listOf(
        TpassConfig.IEDU_PORTAL_URL,
        "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/*default/index.do",
        "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do",
        TpassConfig.CAS_LOGIN_URL
    )

    /** 登录后/拉取数据前，把 WebView 各业务路径的会话全部迁入原生 CookieJar */
    fun importAllWebViewCookies(context: Context): Int {
        val manager = CookieManager.getInstance()
        var imported = 0
        for (target in WEBVIEW_IMPORT_URLS) {
            val header = runCatching { manager.getCookie(target) }.getOrNull() ?: continue
            val url = runCatching { target.toHttpUrl() }.getOrNull() ?: continue
            imported += cookieJar(context).importFromCookieHeader(url, header)
        }
        return imported
    }

    /** 用存储的加密凭据静默重登（会话失效时调用） */
    suspend fun silentLogin(context: Context): CasLoginResult {
        val credentials = JluCredentialStore.load(context)
        if (credentials == null) {
            android.util.Log.i("JwApiClient", "silentLogin skipped: no saved credentials")
            return CasLoginResult.NeedsManualLogin
        }
        return CasClient(get(context)).login(credentials.studentId, credentials.password)
    }

    /** 原生 CookieJar → WebView CookieManager（静默重登后 WebView 才能带上新会话） */
    fun syncJarToWebView(context: Context) {
        val manager = CookieManager.getInstance()
        val jar = cookieJar(context)
        listOf(
            TpassConfig.CAS_LOGIN_URL,
            TpassConfig.IEDU_PORTAL_URL,
            "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/*default/index.do",
            "https://iedu.jlu.edu.cn/jwapp/sys/cjcx/*default/index.do"
        ).forEach { target ->
            val httpUrl = runCatching { target.toHttpUrl() }.getOrNull() ?: return@forEach
            runCatching {
                jar.loadForRequest(httpUrl).forEach { cookie ->
                    manager.setCookie("https://${cookie.domain}${cookie.path}", cookie.toString())
                }
            }
        }
        manager.flush()
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

    /**
     * 原生会话能否真正访问教务应用：访问 wdkb 应用页并跟随重定向，
     * 最终仍落在 iedu 域（而非被踢到 CAS 登录页）才算有效。
     * 若 CAS 侧 TGT 仍有效，这条链会顺带把新业务会话 Cookie 写回 CookieJar。
     */
    fun probeIeduSession(context: Context): Boolean {
        return runCatching {
            val request = okhttp3.Request.Builder()
                .url("https://iedu.jlu.edu.cn/jwapp/sys/wdkb/*default/index.do")
                .build()
            get(context).newCall(request).execute().use { response ->
                response.request.url.host == TpassConfig.IEDU_HOST && response.isSuccessful
            }
        }.getOrDefault(false)
    }
}
