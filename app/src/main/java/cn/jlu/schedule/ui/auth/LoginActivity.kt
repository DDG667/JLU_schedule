package cn.jlu.schedule.ui.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.JluCredentialStore
import cn.jlu.schedule.auth.JluCredentials
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.remote.JwApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope

/**
 * 内嵌统一身份认证登录页。
 *
 * 直接加载智慧教育平台门户，未登录时自动跳转到 TPASS 登录页；登录成功（回到 iedu 域名）
 * 后把 WebView 会话 Cookie 迁移进原生 CookieJar 持久化，供一键导入与静默重登复用。
 * 已保存密码时自动填入学号密码（不自动提交，避免验证码/密码错误时反复尝试触发锁定）。
 * "记住密码"开启时，捕获本次登录实际提交的学号密码并加密存储，供会话失效后静默重登。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var hint: TextView
    private val finished = java.util.concurrent.atomic.AtomicBoolean(false)

    /** TPASS 表单的账号输入框无 name 属性，凭据只在 DOM 中，提交时机经 JS 桥缓存 */
    @Volatile
    private var capturedStudentId: String = ""

    @Volatile
    private var capturedPassword: String = ""

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
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = TpassConfig.USER_AGENT
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        android.webkit.CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.addJavascriptInterface(CredentialCaptureBridge(), "JluLoginBridge")
        webView.webViewClient = LoginWebViewClient()

        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // 预先把已有 Cookie 写入 WebView
        JwApiClient.syncJarToWebView(this)

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            // 直接打开 CAS 登录页，service 参数指向智慧教育门户，登录成功后由 CAS 回跳建立会话
            val loginUrl = "${TpassConfig.CAS_LOGIN_URL}?service=" +
                java.net.URLEncoder.encode(TpassConfig.IEDU_PORTAL_URL, "UTF-8")
            webView.loadUrl(loginUrl)
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
        @SuppressLint("WebViewClientOnReceivedSslError")
        override fun onReceivedSslError(
            view: WebView,
            handler: android.webkit.SslErrorHandler,
            error: android.net.http.SslError
        ) {
            handler.cancel()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return false
            }
            return try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                true
            } catch (e: Exception) {
                true
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            progress.visibility = View.GONE
            val host = runCatching { Uri.parse(url).host ?: "" }.getOrDefault("")
            if (host == TpassConfig.CAS_HOST) {
                maybeAutoFill(url)
                injectCredentialCapture()
            }
            if (host == TpassConfig.IEDU_HOST && !finished.get()) {
                if (tryFinishSuccess()) return
                // 门户新会话建立可能晚于首帧，稍候复查一次
                view.postDelayed({ if (!finished.get()) tryFinishSuccess() }, 1200)
            }
        }
    }

    /**
     * 登录页点击/提交瞬间把 #un/#pd 当前值回传缓存（autofill 值或用户实输值），
     * 登录成功后按"记住密码"决定是否落库；扫码登录无表单值则自然不保存。
     */
    private fun injectCredentialCapture() {
        val script = """
            (function(){
              if (window.__jluLoginCapture) return;
              window.__jluLoginCapture = true;
              function cap(){
                try{
                  var u=document.querySelector('#un'), p=document.querySelector('#pd');
                  if(u&&p) window.JluLoginBridge.onCredentials(u.value||'', p.value||'');
                }catch(e){}
              }
              document.addEventListener('click', cap, true);
              document.addEventListener('submit', cap, true);
              cap();
            })()
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private inner class CredentialCaptureBridge {
        @JavascriptInterface
        fun onCredentials(studentId: String?, password: String?) {
            capturedStudentId = studentId.orEmpty().trim()
            capturedPassword = password.orEmpty()
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
        // 门户外壳页无会话也会在 iedu 域渲染（过期 Cookie 同样存在），
        // 必须用原生会话实测教务应用可达，才算登录成功
        if (!finished.compareAndSet(false, true)) return true
        lifecycleScope.launch {
            JwApiClient.importAllWebViewCookies(this@LoginActivity)
            val valid = withContext(Dispatchers.IO) { JwApiClient.probeIeduSession(this@LoginActivity) }
            if (valid) {
                saveCapturedCredentialsIfRequested()
                setResult(RESULT_OK)
                finish()
            } else {
                // 会话无效（可能正被跳转去登录页），回到待登录状态等待用户操作
                finished.set(false)
            }
        }
        return true
    }

    /** 记住密码开启且本次确有表单登录时，把实际提交的凭据加密存储供静默重登 */
    private fun saveCapturedCredentialsIfRequested() {
        if (!AppPreferences.isRememberPassword(this)) return
        if (capturedStudentId.isEmpty() || capturedPassword.isEmpty()) return
        JluCredentialStore.save(this, JluCredentials(capturedStudentId, capturedPassword))
        Log.i(TAG, "credentials saved for silent relogin (studentId=${capturedStudentId.length} chars)")
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

    private companion object {
        const val TAG = "LoginActivity"
    }
}
