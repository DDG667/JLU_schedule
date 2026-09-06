package cn.jlu.schedule.ui.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.JluCredentialStore
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.remote.JwApiClient

/**
 * 内嵌统一身份认证登录页。
 *
 * 直接加载智慧教育平台门户，未登录时自动跳转到 TPASS 登录页；登录成功（回到 iedu 域名）
 * 后把 WebView 会话 Cookie 迁移进原生 CookieJar 持久化，供一键导入与静默重登复用。
 * 已保存密码时自动填入学号密码（不自动提交，避免验证码/密码错误时反复尝试触发锁定）。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var hint: TextView
    private val finished = java.util.concurrent.atomic.AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        webView = findViewById(R.id.loginWebView)
        progress = findViewById(R.id.loginProgress)
        hint = findViewById(R.id.loginHint)
        findViewById<TextView>(R.id.loginClose).setOnClickListener { finish() }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = LoginWebViewClient()

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(TpassConfig.IEDU_PORTAL_URL)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.apply {
            loadUrl("about:blank")
            onPause()
        }
        super.onDestroy()
    }

    private inner class LoginWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            view.loadUrl(request.url.toString())
            return true
        }

        override fun onPageFinished(view: WebView, url: String) {
            progress.visibility = View.GONE
            val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
            if (host == TpassConfig.CAS_HOST) {
                maybeAutoFill(url)
            }
            if (host == TpassConfig.IEDU_HOST && !finished.get()) {
                if (tryFinishSuccess()) return
                // 门户新会话建立可能晚于首帧，稍候复查一次
                view.postDelayed({ if (!finished.get()) tryFinishSuccess() }, 1200)
            }
        }
    }

    private fun maybeAutoFill(pageUrl: String) {
        if (!AppPreferences.isRememberPassword(this)) return
        val credentials = JluCredentialStore.load(this) ?: return
        val script = buildString {
            append("(function(){")
            append("var u=document.querySelector('#un');var p=document.querySelector('#pd');")
            append("if(!u||!p)return;")
            append("u.value=").append(jsString(credentials.studentId)).append(";")
            append("p.value=").append(jsString(credentials.password)).append(";")
            append("})()")
        }
        webView.evaluateJavascript(script, null)
        runCatching { hint.visibility = View.VISIBLE }
    }

    private fun tryFinishSuccess(): Boolean {
        val cookieHeader = runCatching {
            android.webkit.CookieManager.getInstance().getCookie(TpassConfig.IEDU_PORTAL_URL)
        }.getOrNull()
        if (cookieHeader.isNullOrBlank()) return false
        if (!finished.compareAndSet(false, true)) return true
        JwApiClient.importWebViewCookies(this, TpassConfig.IEDU_PORTAL_URL)
        JwApiClient.importWebViewCookies(this, TpassConfig.CAS_LOGIN_URL)
        setResult(RESULT_OK)
        finish()
        return true
    }

    private fun jsString(value: String): String {
        val escaped = value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("'", "\\'")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
        return "\"$escaped\""
    }
}
