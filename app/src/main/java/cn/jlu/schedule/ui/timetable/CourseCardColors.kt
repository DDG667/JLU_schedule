package cn.jlu.schedule.ui.timetable

/**
 * 课程卡片色板（三端一致的 8 色循环），课表网格与课程详情横幅共用。
 */
object CourseCardColors {
    val colors: IntArray = intArrayOf(
        0xFFFAD8C0.toInt(),
        0xFFC9E7FF.toInt(),
        0xFFD8F4D2.toInt(),
        0xFFFFE6A8.toInt(),
        0xFFE6D7FF.toInt(),
        0xFFFFD7E0.toInt(),
        0xFFD8F0EE.toInt(),
        0xFFFFE1C4.toInt()
    )

    fun forCourse(courseIndex: Int): Int = colors[courseIndex % colors.size]
}
