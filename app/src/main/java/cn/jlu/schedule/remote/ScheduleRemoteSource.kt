package cn.jlu.schedule.remote

import android.content.Context
import cn.jlu.schedule.auth.TpassConfig
import cn.jlu.schedule.parser.ScheduleImportCacheParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** 课表 JSON 拉取结果 */
data class ScheduleFetch(
    val json: String,
    val finalUrl: String
)

/**
 * 从教务平台直接拉取课表 JSON（复用现有解析链路的输入格式）。
 * 会话失效的判定标准：请求被重定向回统一认证域名，或响应体里出现登录表单。
 */
object ScheduleRemoteSource {

    sealed class FetchError(message: String) : Exception(message) {
        class SessionExpired : FetchError("登录已过期")
        class NotSchedulePayload(message: String) : FetchError(message)
        class Network(message: String) : FetchError(message)
    }

    suspend fun fetch(context: Context, endpointUrl: String): Result<ScheduleFetch> =
        fetch(JwApiClient.get(context), endpointUrl)

    /** 可注入 client 的核心实现（供单元测试使用 MockWebServer） */
    suspend fun fetch(client: OkHttpClient, endpointUrl: String): Result<ScheduleFetch> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(endpointUrl)
                    .header("Referer", TpassConfig.IEDU_PORTAL_URL)
                    .build()
                client.newCall(request).execute().use { response ->
                    val finalUrl = response.request.url.toString()
                    val body = response.body?.string().orEmpty()
                    val onCas = response.request.url.host == TpassConfig.CAS_HOST
                    if (onCas || body.contains("id=\"loginForm\"")) {
                        throw FetchError.SessionExpired()
                    }
                    if (!response.isSuccessful) {
                        throw FetchError.Network("课表接口返回 ${response.code}")
                    }
                    if (!JwEndpoints.looksLikeSchedulePayload(body) ||
                        !ScheduleImportCacheParser.isLikelyScheduleUrl(finalUrl)
                    ) {
                        throw FetchError.NotSchedulePayload("响应不是课表数据（接口地址可能已变更）")
                    }
                    ScheduleFetch(json = body, finalUrl = finalUrl)
                }
            }.recoverCatching { error ->
                throw if (error is FetchError) error else FetchError.Network(error.message ?: "网络异常")
            }
        }
}
