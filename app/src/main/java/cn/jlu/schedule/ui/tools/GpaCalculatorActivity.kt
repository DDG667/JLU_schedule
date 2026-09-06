package cn.jlu.schedule.ui.tools

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import cn.jlu.schedule.R
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.data.GpaCourseStore
import cn.jlu.schedule.domain.GpaCalculator
import cn.jlu.schedule.domain.GpaCourse
import cn.jlu.schedule.domain.GpaGradeType
import cn.jlu.schedule.ui.theme.ThemePalette
import cn.jlu.schedule.ui.theme.ThemePaletteProvider
import cn.jlu.schedule.ui.theme.UiFeedback
import java.util.Locale
import java.util.UUID

/**
 * 绩点计算器：原生改造自 DailyPotato/JLU-GPA-Calculator 与
 * Coldymemos/JLU-GPA-Calculator-for-Windows-Desktop（已获原作者同意）。
 * 手动录入成绩与学分，逐门可排除，实时计算保研绩点 / 加权平均分 / 算术平均分。
 */
class GpaCalculatorActivity : AppCompatActivity() {

    private lateinit var palette: ThemePalette
    private val courses = mutableListOf<GpaCourse>()

    private lateinit var valueGpa: TextView
    private lateinit var valueWeighted: TextView
    private lateinit var valueArithmetic: TextView
    private lateinit var resultMeta: TextView
    private lateinit var emptyHint: TextView
    private lateinit var courseList: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var scoreInput: EditText
    private lateinit var creditInput: EditText
    private lateinit var typePercent: TextView
    private lateinit var typeLevel: TextView
    private lateinit var levelGroup: LinearLayout

    private var gradeType = GpaGradeType.PERCENT
    private var selectedLevel = GpaCalculator.LEVEL_ORDER.first()
    private var levelButtons: List<Pair<String, TextView>> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemePaletteProvider.applyNightMode(this)
        setTheme(ThemePaletteProvider.themeStyleFor(AppPreferences.getThemeColor(this)))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gpa_calculator)

        palette = ThemePaletteProvider.fromContext(this)
        courses.addAll(GpaCourseStore.load(filesDir))

        valueGpa = findViewById(R.id.gpaValueGpa)
        valueWeighted = findViewById(R.id.gpaValueWeighted)
        valueArithmetic = findViewById(R.id.gpaValueArithmetic)
        resultMeta = findViewById(R.id.gpaResultMeta)
        emptyHint = findViewById(R.id.gpaEmptyHint)
        courseList = findViewById(R.id.gpaCourseList)
        nameInput = findViewById(R.id.gpaNameInput)
        scoreInput = findViewById(R.id.gpaScoreInput)
        creditInput = findViewById(R.id.gpaCreditInput)
        typePercent = findViewById(R.id.gpaTypePercent)
        typeLevel = findViewById(R.id.gpaTypeLevel)
        levelGroup = findViewById(R.id.gpaLevelGroup)

        applySystemBarInsets()
        applyTheme()
        bindAddForm()
        render()
    }

    private fun applyTheme() {
        findViewById<View>(R.id.gpaRoot).setBackgroundColor(palette.pageBackground)
        val title = findViewById<TextView>(R.id.gpaTitle)
        title.setTextColor(palette.textPrimary)
        findViewById<TextView>(R.id.gpaSubtitle).setTextColor(palette.textSecondary)
        val resultCard = findViewById<View>(R.id.gpaResultCard)
        resultCard.background = cardDrawable()

        valueGpa.setTextColor(palette.iconTint)
        valueWeighted.setTextColor(palette.textPrimary)
        valueArithmetic.setTextColor(palette.textPrimary)
        resultMeta.setTextColor(palette.textSecondary)
        emptyHint.setTextColor(palette.textSecondary)

        styleInput(nameInput)
        styleInput(scoreInput)
        styleInput(creditInput)

        val addButton = findViewById<Button>(R.id.gpaAddButton)
        val clearButton = findViewById<Button>(R.id.gpaClearButton)
        UiFeedback.stylePrimaryButton(addButton, palette)
        UiFeedback.styleDangerButton(clearButton, palette)
    }

    private fun bindAddForm() {
        levelButtons = GpaCalculator.LEVEL_ORDER.map { level ->
            val id = when (level) {
                "优秀" -> R.id.gpaLevelExcellent
                "良好" -> R.id.gpaLevelGood
                "中等" -> R.id.gpaLevelMedium
                "及格" -> R.id.gpaLevelPass
                else -> R.id.gpaLevelFail
            }
            level to findViewById<TextView>(id)
        }

        bindSegment(listOf(typePercent, typeLevel), typePercent) { selected ->
            gradeType = if (selected == typePercent) GpaGradeType.PERCENT else GpaGradeType.LEVEL5
            scoreInput.visibility = if (gradeType == GpaGradeType.PERCENT) View.VISIBLE else View.GONE
            levelGroup.visibility = if (gradeType == GpaGradeType.PERCENT) View.GONE else View.VISIBLE
        }
        bindSegment(levelButtons.map { it.second }, levelButtons.first().second) { selected ->
            selectedLevel = levelButtons.first { it.second.id == selected.id }.first
        }

        findViewById<Button>(R.id.gpaAddButton).setOnClickListener { addCourse() }
        findViewById<Button>(R.id.gpaClearButton).setOnClickListener { confirmClearAll() }
    }

    private fun addCourse() {
        val credit = creditInput.text.toString().trim().toDoubleOrNull()
        if (credit == null || credit <= 0.0) {
            UiFeedback.showMessage(courseList, getString(R.string.gpa_invalid_credit), palette)
            return
        }
        val name = nameInput.text.toString().trim()
        val course = when (gradeType) {
            GpaGradeType.PERCENT -> {
                val score = scoreInput.text.toString().trim().toDoubleOrNull()
                if (score == null || score < GpaCalculator.MIN_SCORE || score > GpaCalculator.MAX_SCORE) {
                    UiFeedback.showMessage(courseList, getString(R.string.gpa_invalid_score), palette)
                    return
                }
                GpaCourse(id = UUID.randomUUID().toString(), name = name, gradeType = GpaGradeType.PERCENT, score = score, credit = credit)
            }
            GpaGradeType.LEVEL5 ->
                GpaCourse(id = UUID.randomUUID().toString(), name = name, gradeType = GpaGradeType.LEVEL5, level = selectedLevel, credit = credit)
        }
        courses.add(course)
        persist()
        nameInput.setText("")
        scoreInput.setText("")
        creditInput.setText("")
        render()
        creditInput.clearFocus()
    }

    private fun toggleIncluded(courseId: String, included: Boolean) {
        val index = courses.indexOfFirst { it.id == courseId }
        if (index < 0) return
        courses[index] = courses[index].copy(included = included)
        persist()
        render()
    }

    private fun deleteCourse(courseId: String) {
        if (courses.removeAll { it.id == courseId }) {
            persist()
            render()
        }
    }

    private fun confirmClearAll() {
        if (courses.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.gpa_clear))
            .setMessage(getString(R.string.gpa_clear_confirm))
            .setPositiveButton(getString(R.string.gpa_clear)) { _, _ ->
                courses.clear()
                GpaCourseStore.clear(filesDir)
                render()
                UiFeedback.showMessage(courseList, getString(R.string.gpa_cleared), palette)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .create()
            .also { UiFeedback.styleDialogSurface(it, palette); it.show() }
    }

    private fun persist() {
        runCatching { GpaCourseStore.save(filesDir, courses) }
            .onFailure { Log.w(TAG, "save gpa courses failed", it) }
    }

    private fun render() {
        val summary = GpaCalculator.calculate(courses)
        valueGpa.text = summary.recommendationGpa?.let { format(it) } ?: DASH
        valueWeighted.text = summary.weightedAverage?.let { format(it) } ?: DASH
        valueArithmetic.text = summary.arithmeticAverage?.let { format(it) } ?: DASH
        resultMeta.text = if (summary.includedCount == 0) {
            getString(R.string.gpa_result_empty)
        } else {
            val excluded = courses.size - summary.includedCount
            val base = getString(
                R.string.gpa_included_summary,
                summary.includedCount,
                format(summary.includedCredits)
            )
            if (excluded > 0) "$base · $excluded ${getString(R.string.gpa_excluded_mark)}" else base
        }

        emptyHint.visibility = if (courses.isEmpty()) View.VISIBLE else View.GONE
        courseList.removeAllViews()
        for (breakdown in summary.breakdowns) {
            courseList.addView(buildCourseRow(breakdown))
        }
    }

    private fun buildCourseRow(breakdown: GpaCalculator.CourseBreakdown): View {
        val course = breakdown.course
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = cardDrawable()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameView = TextView(this).apply {
            text = course.displayName
            textSize = 15f
            setTextColor(palette.textPrimary)
            alpha = if (breakdown.included) 1f else 0.55f
        }
        val metaView = TextView(this).apply {
            text = rowMeta(course, breakdown)
            textSize = 12f
            setTextColor(palette.textSecondary)
            alpha = if (breakdown.included) 1f else 0.55f
        }
        info.addView(nameView)
        info.addView(metaView)
        row.addView(info)

        val switch = SwitchCompat(this).apply {
            text = getString(R.string.gpa_included_switch)
            isChecked = breakdown.included
            textSize = 13f
            setOnCheckedChangeListener { _, checked -> toggleIncluded(course.id, checked) }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        }
        row.addView(switch)

        val delete = TextView(this).apply {
            text = getString(R.string.gpa_delete)
            textSize = 13f
            setTextColor(ColorUtils.blendARGB(0xFFD35454.toInt(), palette.textSecondary, 0.25f))
            setPadding(dp(10), dp(6), 0, dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(4) }
            setOnClickListener { deleteCourse(course.id) }
        }
        row.addView(delete)
        return row
    }

    private fun rowMeta(course: GpaCourse, breakdown: GpaCalculator.CourseBreakdown): String {
        val scoreText = when (course.gradeType) {
            GpaGradeType.PERCENT -> formatScore(course.score)
            GpaGradeType.LEVEL5 -> "${course.level}（折算 ${formatScore(breakdown.effectiveScore)}）"
        }
        val mark = if (breakdown.included) "" else " · ${getString(R.string.gpa_excluded_mark)}"
        return "成绩 $scoreText · 绩点 ${format(breakdown.gradePoint)} · ${formatScore(course.credit)} 学分$mark"
    }

    private fun bindSegment(buttons: List<TextView>, selected: TextView, onSelected: (TextView) -> Unit) {
        var current = selected
        fun restyle() {
            for (button in buttons) {
                val checked = button.id == current.id
                button.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 9f
                    setColor(if (checked) palette.buttonBackground else palette.panelBackground)
                }
                button.setTextColor(if (checked) palette.buttonText else palette.textSecondary)
            }
        }
        restyle()
        for (button in buttons) {
            button.setOnClickListener {
                if (button.id != current.id) {
                    current = button
                    restyle()
                    onSelected(button)
                }
            }
        }
    }

    private fun cardDrawable() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18f
        setColor(palette.panelAltBackground)
        setStroke(1, ColorUtils.blendARGB(palette.panelAltBackground, palette.iconTint, 0.16f))
    }

    private fun styleInput(input: EditText) {
        input.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 10f
            setColor(palette.panelBackground)
            setStroke(1, ColorUtils.blendARGB(palette.panelBackground, palette.iconTint, 0.35f))
        }
        input.setPadding(dp(12), dp(10), dp(12), dp(10))
        input.setTextColor(palette.textPrimary)
        input.setHintTextColor(palette.textSecondary)
    }

    private fun applySystemBarInsets() {
        val root = findViewById<View>(R.id.gpaRoot)
        val baseTop = root.paddingTop
        val baseBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = baseTop + bars.top, bottom = baseBottom + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun formatScore(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else String.format(Locale.US, "%.1f", value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val TAG = "GpaCalculator"
        const val DASH = "—"
    }
}
