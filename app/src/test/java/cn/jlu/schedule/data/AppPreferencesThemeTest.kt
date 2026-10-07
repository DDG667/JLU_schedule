package cn.jlu.schedule.data

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import cn.jlu.schedule.update.InMemorySharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 主题色偏好的白名单行为：合法值往返保存，未知值（含历史脏数据）一律回退暖色。
 */
class AppPreferencesThemeTest {
    private lateinit var prefs: InMemorySharedPreferences
    private lateinit var context: Context

    @Before
    fun setup() {
        prefs = InMemorySharedPreferences()
        context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = prefs
        }
    }

    @Test
    fun themeColor_defaultsToWarm() {
        assertEquals(AppPreferences.THEME_WARM, AppPreferences.getThemeColor(context))
    }

    @Test
    fun themeColor_roundTripsAllSupportedThemes() {
        listOf(
            AppPreferences.THEME_WARM,
            AppPreferences.THEME_OCEAN,
            AppPreferences.THEME_MINT,
            AppPreferences.THEME_TOKYO
        ).forEach { theme ->
            AppPreferences.setThemeColor(context, theme)
            assertEquals(theme, AppPreferences.getThemeColor(context))
        }
    }

    @Test
    fun themeColor_setterSanitizesUnknownValue() {
        AppPreferences.setThemeColor(context, "neon")
        assertEquals(AppPreferences.THEME_WARM, AppPreferences.getThemeColor(context))
    }

    @Test
    fun themeColor_storedUnknownValueFallsBackToWarm() {
        prefs.edit().putString("theme_color", "neon").apply()
        assertEquals(AppPreferences.THEME_WARM, AppPreferences.getThemeColor(context))
    }
}
