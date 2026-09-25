package cn.jlu.schedule.ui.auth

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import cn.jlu.schedule.R
import cn.jlu.schedule.auth.CasWebLoginScript
import cn.jlu.schedule.auth.JluCredentialStore
import cn.jlu.schedule.auth.JluCredentials
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.remote.JwApiClient
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** 应用内加密保存凭据，并在官方登录页代填提交；验证码仍由用户完成。 */
class LoginActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var hint: TextView
    private lateinit var form: View
    private lateinit var studentIdInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var loginButton: Button
    private lateinit var webButton: Button
    private var webShown = false
    private var autoSubmitPending = false
    private var autoSubmitAttempts = 0
    private val finished = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        webView = findViewById(R.id.loginWebView)
        progress = findViewById(R.id.loginProgress)
        hint = findViewById(R.id.loginHint)
        form = findViewById(R.id.loginForm)
        studentIdInput = findViewById(R.id.loginStudentId)
        passwordInput = findViewById(R.id.loginPassword)
        statusText = findViewById(R.id.loginStatusText)
        loginButton = findViewById(R.id.loginNativeButton)
        webButton = findViewById(R.id.loginWebButton)
        findViewById<TextView>(R.id.loginClose).setOnClickListener { finish() }

        val palette = ThemePaletteProvider.fromContext(this)
        findViewById<TextView>(R.id.loginTitle).setTextColor(palette.textPrimary)
        findViewById<TextView>(R.id.loginClose).setTextColor(palette.textSecondary)
        statusText.setTextColor(palette.textSecondary)
        hint.setTextColor(palette.textSecondary)
        val inputPadding = (16 * resources.displayMetrics.density).toInt()
        listOf(studentIdInput, passwordInput).forEach {
            UiFeedback.styleInput(it, palette)
            it.setPadding(inputPadding, inputPadding, inputPadding, inputPadding)
        }
        UiFeedback.stylePrimaryButton(loginButton, palette)
        UiFeedback.styleSecondaryButton(webButton, palette)

        val savedCredentials = JluCredentialStore.load(this)
        savedCredentials?.let {
            studentIdInput.setText(it.studentId)
            passwordInput.setText(it.password)
        }
        loginButton.setOnClickListener { saveAndOpenWebLogin() }
        webButton.setOnClickListener { openWebLogin(clearSession = true, autoSubmit = false) }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = TpassConfig.USER_AGENT
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = LoginWebViewClient()
        JwApiClient.syncJarToWebView(this)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webShown && webView.canGoBack()) webView.goBack()
                else if (webShown) showNativeForm()
                else finish()
            }
        })

        if (savedInstanceState?.getBoolean(KEY_WEB_SHOWN) == true) {
            webShown = true
            autoSubmitPending = savedInstanceState.getBoolean(KEY_AUTO_SUBMIT_PENDING)
            autoSubmitAttempts = savedInstanceState.getInt(KEY_AUTO_SUBMIT_ATTEMPTS)
            form.visibility = View.GONE
            hint.visibility = View.VISIBLE
            hint.setText(if (autoSubmitPending) R.string.account_web_auto_hint else R.string.account_login_hint)
            webView.visibility = View.VISIBLE
            webView.restoreState(savedInstanceState)
        } else if (savedInstanceState == null && savedCredentials != null &&
            !intent.getBooleanExtra(EXTRA_EDIT_CREDENTIALS, false)
        ) {
            openWebLogin(clearSession = true, autoSubmit = true)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_WEB_SHOWN, webShown)
        outState.putBoolean(KEY_AUTO_SUBMIT_PENDING, autoSubmitPending)
        outState.putInt(KEY_AUTO_SUBMIT_ATTEMPTS, autoSubmitAttempts)
        if (webShown) webView.saveState(outState)
    }

    override fun onDestroy() {
        webView.loadUrl("about:blank")
        webView.onPause()
        super.onDestroy()
    }

    private fun enteredCredentials(): JluCredentials? {
        val id = studentIdInput.text.toString().trim()
        val password = passwordInput.text.toString()
        if (id.isEmpty() || password.isEmpty()) {
            statusText.text = "请输入校园账号和密码"
            return null
        }
        return JluCredentials(id, password)
    }

    private fun setBusy(busy: Boolean) {
        loginButton.isEnabled = !busy
        webButton.isEnabled = !busy
        progress.visibility = if (busy) View.VISIBLE else View.GONE
    }

    private fun saveAndOpenWebLogin() {
        val credentials = enteredCredentials() ?: return
        setBusy(true)
        statusText.text = "正在保存账号密码…"
        lifecycleScope.launch {
            val saved = withContext(Dispatchers.IO) {
                JluCredentialStore.save(this@LoginActivity, credentials)
            }
            setBusy(false)
            if (saved) {
                openWebLogin(clearSession = true, autoSubmit = true)
            } else {
                statusText.text = "保存失败，请重试"
            }
        }
    }

    private suspend fun clearPreviousSession() = suspendCancellableCoroutine<Unit> { continuation ->
        JwApiClient.clearSession(this) {
            if (continuation.isActive) continuation.resume(Unit)
        }
    }

    private fun openWebLogin(clearSession: Boolean, autoSubmit: Boolean) {
        autoSubmitPending = autoSubmit
        autoSubmitAttempts = 0
        form.visibility = View.GONE
        hint.visibility = View.VISIBLE
        hint.setText(if (autoSubmit) R.string.account_web_auto_hint else R.string.account_login_hint)
        webView.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        webShown = true
        if (clearSession) {
            lifecycleScope.launch {
                clearPreviousSession()
                if (webShown && !isFinishing) loadWebLogin()
            }
        } else loadWebLogin()
    }

    private fun loadWebLogin() {
        val loginUrl = "${TpassConfig.CAS_LOGIN_URL}?service=" +
            java.net.URLEncoder.encode(TpassConfig.IEDU_PORTAL_URL, "UTF-8")
        webView.loadUrl(loginUrl)
    }

    private fun showNativeForm() {
        webShown = false
        autoSubmitPending = false
        webView.stopLoading()
        webView.visibility = View.GONE
        hint.visibility = View.GONE
        form.visibility = View.VISIBLE
        progress.visibility = View.GONE
    }

    private fun isOfficialLoginPage(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == TpassConfig.CAS_HOST &&
            uri.port == -1 && uri.path == "/tpass/login"
    }

    private fun attemptSavedWebLogin(url: String) {
        if (!autoSubmitPending || autoSubmitAttempts >= MAX_AUTO_SUBMIT_ATTEMPTS ||
            !isOfficialLoginPage(url) || !isOfficialLoginPage(webView.url.orEmpty())
        ) return
        val credentials = JluCredentialStore.load(this) ?: run {
            autoSubmitPending = false
            return
        }
        // 一次页面加载只提交一次。页面的验证码或密码错误回跳时不再自动提交。
        autoSubmitPending = false
        autoSubmitAttempts++
        webView.evaluateJavascript(CasWebLoginScript.build(credentials)) { result ->
            if (result == "\"not-ready\"" && webShown && !isFinishing &&
                autoSubmitAttempts < MAX_AUTO_SUBMIT_ATTEMPTS &&
                isOfficialLoginPage(webView.url.orEmpty())
            ) {
                autoSubmitPending = true
                webView.postDelayed({ attemptSavedWebLogin(webView.url.orEmpty()) }, 600)
            }
        }
    }

    private inner class LoginWebViewClient : WebViewClient() {
        override fun onReceivedSslError(
            view: WebView,
            handler: android.webkit.SslErrorHandler,
            error: android.net.http.SslError
        ) {
            handler.cancel()
        }

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            if (url.startsWith("http://") || url.startsWith("https://")) return false
            return try {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                true
            } catch (_: Exception) {
                true
            }
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (!webShown) return
            progress.visibility = View.GONE
            if (autoSubmitPending && isOfficialLoginPage(url)) {
                attemptSavedWebLogin(url)
                return
            }
            val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
            if (host == TpassConfig.IEDU_HOST && !finished.get()) {
                if (tryFinishSuccess()) return
                view.postDelayed({ if (!finished.get()) tryFinishSuccess() }, 1200)
            }
        }
    }

    private fun tryFinishSuccess(): Boolean {
        if (!finished.compareAndSet(false, true)) return true
        lifecycleScope.launch {
            JwApiClient.importAllWebViewCookies(this@LoginActivity)
            val valid = withContext(Dispatchers.IO) {
                JwApiClient.probeIeduSession(this@LoginActivity)
            }
            if (valid) {
                setResult(RESULT_OK)
                finish()
            } else {
                finished.set(false)
            }
        }
        return true
    }

    companion object {
        const val EXTRA_EDIT_CREDENTIALS = "edit_credentials"
        private const val MAX_AUTO_SUBMIT_ATTEMPTS = 3
        const val KEY_WEB_SHOWN = "web_shown"
        private const val KEY_AUTO_SUBMIT_PENDING = "auto_submit_pending"
        private const val KEY_AUTO_SUBMIT_ATTEMPTS = "auto_submit_attempts"
    }
}
