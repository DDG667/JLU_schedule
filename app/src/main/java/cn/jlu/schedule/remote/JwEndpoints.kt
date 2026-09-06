package cn.jlu.schedule.remote

import android.content.Context
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.SemesterStartDatePolicy
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 教务系统端点注册表。
 *
 * 课表接口已在网页导入链路中实际验证；其余接口路径为占位，
 * 待真实账号登录抓包确认后（v2.1）再实现对应功能页面。
 */
object JwEndpoints {

    /** 课表查询接口的稳定特征（ScheduleImportCacheParser 同源） */
    const val SCHEDULE_ENDPOINT_HINT = "cxxszhxqkb.do"
    const val SCHEDULE_MODULE_HINT = "modules/xskcb"

    /** 校内直连的常见默认值；一键导入始终优先使用自学习到的 URL */
    const val DEFAULT_SCHEDULE_URL =
        "https://iedu.jlu.edu.cn/jwapp/sys/wdkb/modules/xskcb/cxxszhxqkb.do"

    /** 一键导入使用的课表接口：自学习优先，缺省时返回 null（引导先做一次网页导入） */
    fun resolveScheduleEndpoint(context: Context): String? =
        resolveScheduleEndpoint(AppPreferences.getLearnedScheduleEndpoint(context))

    /** 纯函数版本（可测）：仅接受合法 URL 形态的自学习地址 */
    fun resolveScheduleEndpoint(learned: String?): String? {
        if (learned.isNullOrBlank()) return null
        return if (learned.toHttpUrlOrNull() != null) learned else null
    }

    /** 网页导入捕获到课表响应时调用，学习完整 URL 供一键导入直接复用 */
    fun learnScheduleEndpoint(context: Context, url: String) {
        learnScheduleEndpoint(
            current = AppPreferences.getLearnedScheduleEndpoint(context),
            candidate = url
        ) { learned -> AppPreferences.setLearnedScheduleEndpoint(context, learned) }
    }

    /** 纯函数版本（可测）：候选必须是课表接口特征 URL 且与现值不同才落盘 */
    fun learnScheduleEndpoint(current: String?, candidate: String, store: (String) -> Unit) {
        val normalized = candidate.substringBefore("#")
        if (!isScheduleEndpoint(normalized)) return
        if (normalized.toHttpUrlOrNull() == null) return
        if (normalized == current) return
        store(normalized)
    }

    fun isScheduleEndpoint(url: String): Boolean {
        return url.contains(SCHEDULE_ENDPOINT_HINT, ignoreCase = true) ||
            url.contains(SCHEDULE_MODULE_HINT, ignoreCase = true)
    }

    /** 判断响应是否为课表 JSON 载荷（与 ScheduleImportCacheParser 的判定一致） */
    fun looksLikeSchedulePayload(text: String): Boolean {
        return text.contains("\"datas\"") && text.contains("\"rows\"") && text.contains("\"KCM\"")
    }

    /** 学期开始日期推断（与网页导入共用策略） */
    fun inferSemesterStart(courses: List<cn.jlu.schedule.model.CourseSchedule>) =
        SemesterStartDatePolicy.inferFromCoursesOrNull(courses)

    /**
     * 后续功能占位：路径特征来自金智 eMAP 平台惯例，均需真实账号抓包确认。
     * v2.1 计划：成绩单/绩点计算、考试安排、培养方案查询。
     */
    data class FeatureEndpoint(
        val key: String,
        val title: String,
        val pathHint: String,
        val confirmed: Boolean
    )

    val upcomingFeatures: List<FeatureEndpoint> = listOf(
        FeatureEndpoint("grades", "成绩查询", "jwapp/sys/cjcx/modules/cjcx/*", confirmed = false),
        FeatureEndpoint("exams", "考试安排", "jwapp/sys/kscx/modules/*", confirmed = false),
        FeatureEndpoint("cultivate", "培养方案", "jwapp/sys/pyfa/modules/*", confirmed = false),
        FeatureEndpoint("gpa", "绩点计算", "（本地计算，依赖成绩查询）", confirmed = false)
    )
}
