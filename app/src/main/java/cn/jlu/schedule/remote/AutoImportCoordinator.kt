package cn.jlu.schedule.remote

import android.content.Context
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.ImportedScheduleStorage
import cn.jlu.schedule.data.ScheduleRepository
import cn.jlu.schedule.parser.DoScheduleParser
import cn.jlu.schedule.auth.CasLoginResult

/**
 * 一键导入编排：解析端点 → 拉取课表 JSON（会话失效时静默重登一次）→ 复用解析器 → 入库。
 * 全程无需打开浏览器；只有端点未学习或静默登录不可行时才引导用户走网页导入。
 */
object AutoImportCoordinator {

    /** 流程阶段，UI 可据此更新进度文案 */
    sealed class Phase {
        data object Checking : Phase()
        data object ReLogin : Phase()
        data object Fetching : Phase()
        data class Done(val courseCount: Int, val newProfileName: String?) : Phase()
        data class NeedWebImport(val reason: String) : Phase()
        data class Failed(val message: String) : Phase()
    }

    suspend fun run(
        context: Context,
        mode: ImportedScheduleStorage.ImportMode,
        newProfileName: String?,
        onPhase: (Phase) -> Unit = {}
    ) {
        val endpoint = JwEndpoints.resolveScheduleEndpoint(context)
        if (endpoint == null) {
            onPhase(
                Phase.NeedWebImport(
                    "还没有学习到课表接口地址，请先使用一次\"网页导入\"，之后即可一键导入"
                )
            )
            return
        }

        onPhase(Phase.Fetching)
        var fetch = ScheduleRemoteSource.fetch(context, endpoint)
        if (fetch.isFailure && fetch.exceptionOrNull() is ScheduleRemoteSource.FetchError.SessionExpired) {
            if (!AppPreferences.isRememberPassword(context)) {
                onPhase(Phase.NeedWebImport("登录已过期，且未开启\"记住密码\"，请重新登录网页版"))
                return
            }
            onPhase(Phase.ReLogin)
            when (JwApiClient.silentLogin(context)) {
                is CasLoginResult.Success -> {
                    onPhase(Phase.Fetching)
                    fetch = ScheduleRemoteSource.fetch(context, endpoint)
                }
                is CasLoginResult.InvalidCredentials ->
                    onPhase(Phase.NeedWebImport("自动登录失败：账号或密码已修改，请重新登录"))
                CasLoginResult.NeedsManualLogin ->
                    onPhase(Phase.NeedWebImport("本次登录需要验证码，请重新登录网页版"))
                is CasLoginResult.Error ->
                    onPhase(Phase.NeedWebImport("自动登录失败：无法连接统一认证，请检查网络"))
            }
        }

        val payload = fetch.getOrElse { error ->
            onPhase(Phase.Failed(describe(error)))
            return
        }

        val courses = DoScheduleParser.parse(payload.json)
            .filter { it.courseName.isNotBlank() && it.meetings.isNotEmpty() }
        if (courses.isEmpty()) {
            onPhase(Phase.Failed("教务返回了空课表"))
            return
        }

        onPhase(Phase.Checking)
        val import = ScheduleRepository.importParsedCourses(
            context,
            courses,
            mode,
            newProfileName,
            JwEndpoints.inferSemesterStart(courses)
        )
        import.fold(
            onSuccess = { result -> onPhase(Phase.Done(result.courseCount, newProfileName)) },
            onFailure = { error -> onPhase(Phase.Failed(describe(error))) }
        )
    }

    private fun describe(error: Throwable): String = when (error) {
        is ScheduleRemoteSource.FetchError.SessionExpired -> "登录已过期"
        is ScheduleRemoteSource.FetchError.NotSchedulePayload -> error.message ?: "接口响应异常"
        is ScheduleRemoteSource.FetchError.Network -> error.message ?: "网络异常"
        else -> error.message ?: "未知错误"
    }
}
