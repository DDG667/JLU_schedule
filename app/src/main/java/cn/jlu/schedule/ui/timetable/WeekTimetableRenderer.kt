package cn.jlu.schedule.ui.timetable

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import cn.jlu.schedule.domain.CourseMeetingDisplayRef
import cn.jlu.schedule.domain.CourseMeetingRef
import cn.jlu.schedule.model.Weekday
import cn.jlu.schedule.ui.theme.ThemePalette
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class WeekTimetableRenderer(
    private val periodRanges: List<String>,
    private val weekdayLabels: Map<Weekday, String>,
    private val fontScale: Float,
    private val palette: ThemePalette,
    private val hasCustomBackground: Boolean
) {
    private val weekdays = listOf(
        Weekday.MONDAY,
        Weekday.TUESDAY,
        Weekday.WEDNESDAY,
        Weekday.THURSDAY,
        Weekday.FRIDAY,
        Weekday.SATURDAY,
        Weekday.SUNDAY
    )

    private val panelAlpha = if (hasCustomBackground) 0.34f else 1f

    fun render(
        headerRow: LinearLayout,
        bodyRow: LinearLayout,
        metrics: TimetableMetrics.Spec,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (CourseMeetingRef) -> Unit,
        weekStart: LocalDate,
        today: LocalDate,
        currentSection: Int?
    ) {
        headerRow.removeAllViews()
        bodyRow.removeAllViews()
        headerRow.setPadding(metrics.outerPadding, 0, metrics.outerPadding, 0)
        bodyRow.setPadding(metrics.outerPadding, 0, metrics.outerPadding, 0)

        buildHeader(headerRow, metrics, weekStart, today)
        buildBody(bodyRow, metrics, items, onCourseClick, weekStart, today, currentSection)
    }

    @SuppressLint("SetTextI18n")
    private fun buildHeader(
        headerRow: LinearLayout,
        m: TimetableMetrics.Spec,
        weekStart: LocalDate,
        today: LocalDate
    ) {
        val context = headerRow.context
        val corner = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, m.headerCellHeight)
            text = String.format(Locale.getDefault(), "%d月", weekStart.monthValue)
            gravity = Gravity.CENTER
            textSize = 11f * fontScale
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(palette.textPrimary)
            background = roundedBackground(0x00000000, radius = 10f)
            includeFontPadding = false
        }
        headerRow.addView(corner)

        weekdays.forEachIndexed { index, weekday ->
            val date = weekStart.plusDays(index.toLong())
            val isToday = date == today
            val dayHeader = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(m.dayColumnWidth, m.headerCellHeight).apply {
                    marginStart = m.cellGap
                }
                text = String.format(
                    Locale.getDefault(),
                    "%s\n%s",
                    weekdayLabels[weekday] ?: "一",
                    date.format(DateTimeFormatter.ofPattern("M/d"))
                )
                gravity = Gravity.CENTER
                textSize = 11f * fontScale
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setTextColor(palette.textPrimary)
                background = if (isToday) {
                    roundedBackground(withAlpha(palette.gridHeaderToday, panelAlpha), radius = 10f)
                } else {
                    roundedBackground(withAlpha(palette.gridHeader, panelAlpha), radius = 10f)
                }
            }
            headerRow.addView(dayHeader)
        }
    }

    private fun buildBody(
        bodyRow: LinearLayout,
        m: TimetableMetrics.Spec,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (CourseMeetingRef) -> Unit,
        weekStart: LocalDate,
        today: LocalDate,
        currentSection: Int?
    ) {
        val context = bodyRow.context
        val leftColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        periodRanges.forEachIndexed { index, time ->
            val periodCell = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(m.leftColumnWidth, m.sectionHeight)
                background = roundedBackground(withAlpha(palette.gridLeftColumn, panelAlpha), radius = 8f)
            }
            val label = TextView(context).apply {
                text = String.format(Locale.getDefault(), "%d", index + 1)
                textSize = 12f * fontScale
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setTypeface(typeface, Typeface.BOLD)
                includeFontPadding = false
                setTextColor(palette.textPrimary)
            }
            val startTime = time.substringBefore('-')
            val endTime = time.substringAfter('-')
            val timeLabel = TextView(context).apply {
                text = startTime
                textSize = 9f * fontScale
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextColor(palette.textSecondary)
            }
            val timeLabelEnd = TextView(context).apply {
                text = endTime
                textSize = 9f * fontScale
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                includeFontPadding = false
                setTextColor(palette.textSecondary)
            }
            periodCell.addView(label)
            periodCell.addView(timeLabel)
            periodCell.addView(timeLabelEnd)
            leftColumn.addView(periodCell)
        }
        bodyRow.addView(leftColumn)

        weekdays.forEachIndexed { index, weekday ->
            val date = weekStart.plusDays(index.toLong())
            bodyRow.addView(buildDayColumn(bodyRow, m, weekday, items, onCourseClick, date == today, currentSection))
        }
    }

    private fun buildDayColumn(
        parent: LinearLayout,
        m: TimetableMetrics.Spec,
        weekday: Weekday,
        items: List<CourseMeetingDisplayRef>,
        onCourseClick: (CourseMeetingRef) -> Unit,
        isToday: Boolean,
        currentSection: Int?
    ): FrameLayout {
        val context = parent.context
        val columnHeight = m.sectionHeight * periodRanges.size
        val dayColumn = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(m.dayColumnWidth, columnHeight).apply {
                marginStart = m.cellGap
            }
            background = if (isToday) {
                roundedBackground(withAlpha(palette.gridDayToday, panelAlpha), radius = 8f)
            } else {
                roundedBackground(withAlpha(palette.gridDayCell, panelAlpha), radius = 8f)
            }
        }

        val dayItems = resolveOverlapForDay(items.filter { it.meeting.weekday == weekday })
        dayItems.forEach { item ->
            val start = item.meeting.startSection.coerceIn(1, 12)
            val end = item.meeting.endSection.coerceIn(start, 12)
            val isCurrentCourse = item.isCurrentWeek && isToday && currentSection != null && currentSection in start..end
            val top = (start - 1) * m.sectionHeight + 2
            val cardHeight = (end - start + 1) * m.sectionHeight - 4
            val cardWidth = m.dayColumnWidth - m.cellGap

            val shadow = TextView(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                    topMargin = top + 4
                    leftMargin = m.cellGap / 2 + 2
                }
                background = roundedBackground(if (item.isCurrentWeek) 0x2F000000.toInt() else 0x14000000.toInt(), radius = 10f)
            }
            dayColumn.addView(shadow)

            val card = LinearLayout(context).apply {
                layoutParams = FrameLayout.LayoutParams(cardWidth, cardHeight.coerceAtLeast(36)).apply {
                    topMargin = top
                    leftMargin = m.cellGap / 2
                }
                orientation = LinearLayout.VERTICAL
                setPadding(m.cardPadding, m.cardPadding, m.cardPadding, m.cardPadding)
                alpha = if (item.isCurrentWeek) 1f else 0.55f
                background = roundedBackground(CourseCardColors.forCourse(item.courseIndex, palette.isDark))
                elevation = if (isCurrentCourse) 10f else 6f
                setOnClickListener { onCourseClick(item.toCourseMeetingRef()) }

                val spanCount = (end - start + 1).coerceAtLeast(1)

                if (!item.isCurrentWeek) {
                    addView(TextView(context).apply {
                        text = "[非本周] 第${item.nextActiveWeek}周"
                        textSize = 8f * fontScale
                        setTextColor(CourseCardColors.textColorFor(item.courseIndex, palette.isDark))
                        maxLines = 1
                    })
                }

                // 课程名按长度分级缩放：短名原字号，长名轻度缩小（下限 9.8sp 保证可读），
                // 换行交给动态行数 + 末尾省略兜底，避免"深度学习"被拆成单字换行
                addView(TextView(context).apply {
                    text = item.course.courseName
                    textSize = fittedSize(item.course.courseName, 12f, 9.8f) * fontScale
                    setTextColor(CourseCardColors.textColorFor(item.courseIndex, palette.isDark))
                    setTypeface(typeface, Typeface.BOLD)
                    maxLines = if (spanCount >= 2) 3 else 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                })

                addView(TextView(context).apply {
                    text = item.meeting.location.ifBlank { "教室待定" }
                    textSize = fittedSize(item.meeting.location, 9.5f, 8.2f) * fontScale
                    setTextColor(CourseCardColors.textColorFor(item.courseIndex, palette.isDark))
                    maxLines = (spanCount * 2).coerceIn(2, 6)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    includeFontPadding = false
                })
            }
            dayColumn.addView(card)
        }

        return dayColumn
    }

    private fun resolveOverlapForDay(items: List<CourseMeetingDisplayRef>): List<CourseMeetingDisplayRef> {
        val occupied = BooleanArray(13)
        val sorted = items.sortedWith(
            compareByDescending<CourseMeetingDisplayRef> { it.isCurrentWeek }
                .thenBy { it.nextActiveWeek }
                .thenBy { it.meeting.startSection }
                .thenBy { it.courseIndex }
        )
        val selected = mutableListOf<CourseMeetingDisplayRef>()

        sorted.forEach { item ->
            val start = item.meeting.startSection.coerceIn(1, 12)
            val end = item.meeting.endSection.coerceIn(start, 12)
            val overlap = (start..end).any { section -> occupied[section] }
            if (!overlap) {
                selected.add(item)
                (start..end).forEach { section -> occupied[section] = true }
            }
        }

        return selected.sortedBy { it.meeting.startSection }
    }

    private fun roundedBackground(fill: Int, radius: Float = 8f): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(fill)
        }
    }

    /** 按文本长度轻度降字号：≤6 字原字号，≤14 字打九折，更长打八折但不低于 [min] */
    private fun fittedSize(text: String, base: Float, min: Float): Float {
        return when {
            text.length <= 6 -> base
            text.length <= 14 -> base * 0.9f
            else -> maxOf(base * 0.8f, min)
        }
    }

    private fun withAlpha(color: Int, alphaFactor: Float): Int {
        val alpha = (((color ushr 24) and 0xFF) * alphaFactor).toInt().coerceIn(0, 255)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    companion object {
        fun resolveCurrentSection(periodRanges: List<String>, now: LocalTime = LocalTime.now()): Int? {
            periodRanges.forEachIndexed { index, range ->
                val start = runCatching { LocalTime.parse(range.substringBefore('-')) }.getOrNull() ?: return@forEachIndexed
                val end = runCatching { LocalTime.parse(range.substringAfter('-')) }.getOrNull() ?: return@forEachIndexed
                if (!now.isBefore(start) && now.isBefore(end)) {
                    return index + 1
                }
            }
            return null
        }
    }
}
