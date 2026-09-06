package cn.jlu.schedule.ui.timetable

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.ColorUtils
import cn.jlu.schedule.R
import cn.jlu.schedule.domain.CourseMeetingRef
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import com.google.android.material.bottomsheet.BottomSheetDialog

object CourseDetailBottomSheet {
    private val weekdayLabels = mapOf(
        Weekday.MONDAY to "周一",
        Weekday.TUESDAY to "周二",
        Weekday.WEDNESDAY to "周三",
        Weekday.THURSDAY to "周四",
        Weekday.FRIDAY to "周五",
        Weekday.SATURDAY to "周六",
        Weekday.SUNDAY to "周日"
    )

    @SuppressLint("SetTextI18n")
    fun show(context: Context, item: CourseMeetingRef, periodRanges: List<String>) {
        val dialog = BottomSheetDialog(context)
        val parent = FrameLayout(context)
        val view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_course_detail, parent, false)
        val palette = ThemePaletteProvider.fromContext(context)
        val meeting = item.meeting

        // 浮动卡片：隐藏系统 sheet 的白色底，由内层全圆角卡片自行承担背景
        dialog.setOnShowListener { dialogInterface ->
            (dialogInterface as? BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }

        val card = view.findViewById<LinearLayout>(R.id.detailCardRoot)
        card.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f
            setColor(palette.detailCard)
            setStroke(1, ColorUtils.blendARGB(palette.detailCard, palette.iconTint, 0.15f))
        }

        // 分割线按主题着色
        val dividerColor = ColorUtils.blendARGB(palette.detailCard, palette.textSecondary, 0.22f)
        view.findViewById<View>(R.id.dividerOne).backgroundTintList =
            ColorStateList.valueOf(dividerColor)
        view.findViewById<View>(R.id.dividerTwo).backgroundTintList =
            ColorStateList.valueOf(dividerColor)

        // 拖拽把手与信息行图标按主题着色
        view.findViewById<View>(R.id.detailHandle).backgroundTintList =
            ColorStateList.valueOf(ColorUtils.blendARGB(palette.detailCard, palette.textSecondary, 0.35f))
        bindRowIcon(view, R.id.detailIconTeacher, palette)
        bindRowIcon(view, R.id.detailIconLocation, palette)
        bindRowIcon(view, R.id.detailIconWeeks, palette)
        bindRowIcon(view, R.id.detailIconSemester, palette)

        // 标题
        val title = view.findViewById<TextView>(R.id.detailTitle)
        title.text = item.course.courseName
        title.setTextColor(palette.detailTitle)

        // 时间横幅：与课表格子同色，建立视觉关联；点击复制时间概要
        val banner = view.findViewById<LinearLayout>(R.id.detailBanner)
        banner.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f
            setColor(CourseCardColors.forCourse(item.courseIndex))
        }
        val bannerSection = view.findViewById<TextView>(R.id.detailBannerSection)
        bannerSection.text = "${weekdayLabels[meeting.weekday]} · 第${meeting.startSection}-${meeting.endSection}节"
        bannerSection.setTextColor(Color.WHITE)
        val bannerTime = view.findViewById<TextView>(R.id.detailBannerTime)
        val timeText = "${sectionStart(periodRanges, meeting.startSection)} - ${sectionEnd(periodRanges, meeting.endSection)}"
        bannerTime.text = timeText
        bannerTime.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 215))

        // 信息行
        val teacher = view.findViewById<TextView>(R.id.detailTeacher)
        teacher.text = item.course.teacher.ifBlank { "未知" }
        val location = view.findViewById<TextView>(R.id.detailLocation)
        location.text = meeting.location.ifBlank { "未标注" }
        val weeks = view.findViewById<TextView>(R.id.detailWeeks)
        weeks.text = meetingWeekText(item)
        val meta = view.findViewById<TextView>(R.id.detailMeta)
        val creditText = item.course.credit?.let { "${it}学分" } ?: "学分未标注"
        meta.text = "${item.course.semester} · $creditText"

        val valueColor = palette.textPrimary
        listOf(teacher, location, weeks, meta).forEach { it.setTextColor(valueColor) }

        // 点击行复制到剪贴板
        bindCopy(context, view, R.id.rowTeacher, "教师", teacher.text.toString(), palette)
        bindCopy(context, view, R.id.rowLocation, "地点", location.text.toString(), palette)
        bindCopy(context, view, R.id.rowWeeks, "周次", weeks.text.toString(), palette)
        bindCopy(context, view, R.id.rowSemester, "学期", meta.text.toString(), palette)
        bindCopy(
            context, view, R.id.detailBanner, "时间",
            "${bannerSection.text} $timeText", palette
        )

        dialog.setContentView(view)
        dialog.show()
    }

    private fun bindCopy(context: Context, view: View, rowId: Int, label: String, value: String, palette: ThemePalette) {
        view.findViewById<View>(rowId)?.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
            Toast.makeText(context, "已复制$label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindRowIcon(view: View, iconId: Int, palette: ThemePalette) {
        val icon = view.findViewById<ImageView>(iconId)
        val chip = icon.parent as? FrameLayout ?: return
        chip.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 15f
            setColor(palette.panelBackground)
        }
        icon.imageTintList = ColorStateList.valueOf(palette.iconTint)
    }

    private fun sectionStart(periodRanges: List<String>, section: Int): String =
        periodRanges.getOrNull(section - 1)?.substringBefore('-') ?: "--:--"

    private fun sectionEnd(periodRanges: List<String>, section: Int): String =
        periodRanges.getOrNull(section - 1)?.substringAfter('-') ?: "--:--"

    private fun meetingWeekText(item: CourseMeetingRef): String {
        val rules = item.meeting.weekRules
        if (rules.isEmpty()) {
            return item.course.rawWeekText.ifBlank { "未标注" }
        }
        return rules.joinToString("，") { rule ->
            val range = if (rule.startWeek == rule.endWeek) {
                "${rule.startWeek}周"
            } else {
                "${rule.startWeek}-${rule.endWeek}周"
            }
            when (rule.parity) {
                WeekParity.ODD -> "$range(单)"
                WeekParity.EVEN -> "$range(双)"
                WeekParity.ALL -> range
            }
        }
    }
}
