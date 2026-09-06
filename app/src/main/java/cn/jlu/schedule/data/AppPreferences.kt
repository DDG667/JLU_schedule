package cn.jlu.schedule.data

import android.content.Context

object AppPreferences {
    private const val PREFS_NAME = "app_settings"
    private const val KEY_DEFAULT_OPEN_PAGE = "default_open_page"
    private const val KEY_CUSTOM_BACKGROUND_URI = "custom_background_uri"
    private const val KEY_SEMESTER_START_DATE = "semester_start_date"
    private const val KEY_THEME_COLOR = "theme_color"
    private const val KEY_TIMETABLE_FONT_SCALE = "timetable_font_scale"
    private const val KEY_SHOW_NON_CURRENT_COURSES = "show_non_current_courses"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_REMINDER_ENABLED = "daily_reminder_enabled"
    private const val KEY_REMINDER_MINUTE = "daily_reminder_minute"

    const val PAGE_TIMETABLE = "timetable"
    const val PAGE_TODAY = "today"
    const val THEME_WARM = "warm"
    const val THEME_OCEAN = "ocean"
    const val THEME_MINT = "mint"
    const val DARK_SYSTEM = "system"
    const val DARK_LIGHT = "light"
    const val DARK_DARK = "dark"

    fun getDarkMode(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DARK_MODE, DARK_SYSTEM)
        return when (raw) {
            DARK_LIGHT, DARK_DARK -> raw ?: DARK_SYSTEM
            else -> DARK_SYSTEM
        }
    }

    fun setDarkMode(context: Context, mode: String) {
        val safe = when (mode) {
            DARK_LIGHT, DARK_DARK -> mode
            else -> DARK_SYSTEM
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DARK_MODE, safe)
            .apply()
    }

    fun getDefaultOpenPage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEFAULT_OPEN_PAGE, PAGE_TIMETABLE) ?: PAGE_TIMETABLE
    }

    fun setDefaultOpenPage(context: Context, page: String) {
        val safeValue = when (page) {
            PAGE_TODAY -> PAGE_TODAY
            else -> PAGE_TIMETABLE
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEFAULT_OPEN_PAGE, safeValue)
            .apply()
    }

    fun getCustomBackgroundUri(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_BACKGROUND_URI, null)
    }

    fun setCustomBackgroundUri(context: Context, uri: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_BACKGROUND_URI, uri)
            .apply()
    }

    fun getThemeColor(context: Context): String {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_THEME_COLOR, THEME_WARM)
        return when (raw) {
            THEME_OCEAN, THEME_MINT -> raw
            else -> THEME_WARM
        }
    }

    fun setThemeColor(context: Context, theme: String) {
        val safe = when (theme) {
            THEME_OCEAN, THEME_MINT -> theme
            else -> THEME_WARM
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_COLOR, safe)
            .apply()
    }

    fun getTimetableFontScale(context: Context): Float {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getFloat(KEY_TIMETABLE_FONT_SCALE, 1.0f)
        return when {
            saved < 0.98f -> 0.95f
            saved > 1.08f -> 1.15f
            else -> 1.0f
        }
    }

    fun setTimetableFontScale(context: Context, scale: Float) {
        val safe = when {
            scale < 0.98f -> 0.95f
            scale > 1.08f -> 1.15f
            else -> 1.0f
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_TIMETABLE_FONT_SCALE, safe)
            .apply()
    }

    fun isShowNonCurrentCourses(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_NON_CURRENT_COURSES, true)
    }

    fun setShowNonCurrentCourses(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SHOW_NON_CURRENT_COURSES, show)
            .apply()
    }

    fun isReminderEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun setReminderEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_REMINDER_ENABLED, enabled)
            .apply()
    }

    /** 提醒时间，格式为当天分钟数（0-1439），默认 7:30 */
    fun getReminderMinute(context: Context): Int {
        val value = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_REMINDER_MINUTE, DEFAULT_REMINDER_MINUTE)
        return value.coerceIn(0, 24 * 60 - 1)
    }

    fun setReminderMinute(context: Context, minuteOfDay: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_REMINDER_MINUTE, minuteOfDay.coerceIn(0, 24 * 60 - 1))
            .apply()
    }

    const val DEFAULT_REMINDER_MINUTE = 7 * 60 + 30
}
