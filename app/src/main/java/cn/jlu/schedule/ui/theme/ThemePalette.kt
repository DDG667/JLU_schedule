package cn.jlu.schedule.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences

data class ThemePalette(
    val pageBackground: Int,
    val navBackground: Int,
    val panelBackground: Int,
    val panelAltBackground: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val iconTint: Int,
    val buttonBackground: Int,
    val buttonText: Int,
    val gridHeader: Int,
    val gridHeaderToday: Int,
    val gridLeftColumn: Int,
    val gridDayCell: Int,
    val gridDayToday: Int,
    val detailCard: Int,
    val detailTitle: Int,
    val detailBody: Int,
    val detailMeta: Int,
    val isDark: Boolean
)

object ThemePaletteProvider {
    /**
     * @param nightOverride 强制使用浅色/深色资源变体；传 null 时跟随系统配置。
     * 桌面小组件等非 Activity 场景需要按用户偏好强制变体时使用。
     */
    fun fromContext(context: Context, nightOverride: Boolean? = null): ThemePalette {
        return fromTheme(context, AppPreferences.getThemeColor(context), nightOverride)
    }

    fun fromTheme(context: Context, theme: String, nightOverride: Boolean? = null): ThemePalette {
        val prefix = when (theme) {
            AppPreferences.THEME_OCEAN -> "ocean"
            AppPreferences.THEME_MINT -> "mint"
            AppPreferences.THEME_TOKYO -> "tokyo"
            else -> "warm"
        }
        val resourceContext = if (nightOverride == null) {
            context
        } else {
            val configuration = Configuration(context.resources.configuration)
            configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                (if (nightOverride) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            context.createConfigurationContext(configuration)
        }
        fun color(suffix: String): Int {
            val id = resourceContext.resources.getIdentifier(
                "${prefix}_$suffix", "color", resourceContext.packageName
            )
            return resourceContext.getColor(if (id != 0) id else R.color.warm_page_background)
        }
        val isDark = nightOverride
            ?: ((resourceContext.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        return ThemePalette(
            pageBackground = color("page_background"),
            navBackground = color("nav_background"),
            panelBackground = color("panel_background"),
            panelAltBackground = color("panel_alt_background"),
            textPrimary = color("text_primary"),
            textSecondary = color("text_secondary"),
            iconTint = color("icon_tint"),
            buttonBackground = color("button_background"),
            buttonText = color("button_text"),
            gridHeader = color("grid_header"),
            gridHeaderToday = color("grid_header_today"),
            gridLeftColumn = color("grid_left_column"),
            gridDayCell = color("grid_day_cell"),
            gridDayToday = color("grid_day_today"),
            detailCard = color("detail_card"),
            detailTitle = color("detail_title"),
            detailBody = color("detail_body"),
            detailMeta = color("detail_meta"),
            isDark = isDark
        )
    }

    fun themeStyleFor(theme: String): Int {
        return when (theme) {
            AppPreferences.THEME_OCEAN -> R.style.Theme_JLU_Ocean
            AppPreferences.THEME_MINT -> R.style.Theme_JLU_Mint
            AppPreferences.THEME_TOKYO -> R.style.Theme_JLU_Tokyo
            else -> R.style.Theme_JLU_Warm
        }
    }

    /** 按用户偏好（跟随系统/浅色/深色）应用全局夜间模式，配置变化时 Activity 自动重建 */
    fun applyNightMode(context: Context) {
        val mode = when (AppPreferences.getDarkMode(context)) {
            AppPreferences.DARK_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            AppPreferences.DARK_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }
}
