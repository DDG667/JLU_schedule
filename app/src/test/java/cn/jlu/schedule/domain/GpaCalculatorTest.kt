package cn.jlu.schedule.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpaCalculatorTest {

    private fun percent(score: Double, credit: Double = 3.0, included: Boolean = true) = GpaCourse(
        id = "c-$score-$credit-$included",
        name = "课程$score",
        gradeType = GpaGradeType.PERCENT,
        score = score,
        credit = credit,
        included = included
    )

    private fun level(level: String, credit: Double = 2.0) = GpaCourse(
        id = "l-$level-$credit",
        name = "课程$level",
        gradeType = GpaGradeType.LEVEL5,
        level = level,
        credit = credit
    )

    @Test
    fun `grade point mapping covers every band edge`() {
        assertEquals(4.0, GpaCalculator.gradePointFor(90.0), 1e-9)
        assertEquals(4.0, GpaCalculator.gradePointFor(100.0), 1e-9)
        assertEquals(3.7, GpaCalculator.gradePointFor(89.9), 1e-9)
        assertEquals(3.7, GpaCalculator.gradePointFor(87.0), 1e-9)
        assertEquals(3.3, GpaCalculator.gradePointFor(86.9), 1e-9)
        assertEquals(3.3, GpaCalculator.gradePointFor(84.0), 1e-9)
        assertEquals(3.0, GpaCalculator.gradePointFor(80.0), 1e-9)
        assertEquals(2.7, GpaCalculator.gradePointFor(77.0), 1e-9)
        assertEquals(2.3, GpaCalculator.gradePointFor(74.0), 1e-9)
        assertEquals(2.0, GpaCalculator.gradePointFor(70.0), 1e-9)
        assertEquals(1.7, GpaCalculator.gradePointFor(67.0), 1e-9)
        assertEquals(1.3, GpaCalculator.gradePointFor(64.0), 1e-9)
        assertEquals(1.0, GpaCalculator.gradePointFor(60.0), 1e-9)
        assertEquals(1.0, GpaCalculator.gradePointFor(63.9), 1e-9)
        assertEquals(0.0, GpaCalculator.gradePointFor(59.9), 1e-9)
        assertEquals(0.0, GpaCalculator.gradePointFor(0.0), 1e-9)
    }

    @Test
    fun `level5 conversion matches preset`() {
        assertEquals(95.0, GpaCalculator.LEVEL_SCORES["优秀"]!!, 1e-9)
        assertEquals(85.0, GpaCalculator.LEVEL_SCORES["良好"]!!, 1e-9)
        assertEquals(75.0, GpaCalculator.LEVEL_SCORES["中等"]!!, 1e-9)
        assertEquals(65.0, GpaCalculator.LEVEL_SCORES["及格"]!!, 1e-9)
        assertEquals(0.0, GpaCalculator.LEVEL_SCORES["不及格"]!!, 1e-9)

        assertEquals(4.0, GpaCalculator.gradePointFor(GpaCalculator.effectiveScore(GpaGradeType.LEVEL5, 0.0, "优秀")), 1e-9)
        assertEquals(3.3, GpaCalculator.gradePointFor(GpaCalculator.effectiveScore(GpaGradeType.LEVEL5, 0.0, "良好")), 1e-9)
        assertEquals(2.3, GpaCalculator.gradePointFor(GpaCalculator.effectiveScore(GpaGradeType.LEVEL5, 0.0, "中等")), 1e-9)
        assertEquals(1.3, GpaCalculator.gradePointFor(GpaCalculator.effectiveScore(GpaGradeType.LEVEL5, 0.0, "及格")), 1e-9)
        assertEquals(0.0, GpaCalculator.gradePointFor(GpaCalculator.effectiveScore(GpaGradeType.LEVEL5, 0.0, "不及格")), 1e-9)
    }

    @Test
    fun `three averages use their own denominators`() {
        val courses = listOf(
            percent(90.0, credit = 4.0), // 绩点 4.0，分数 90
            percent(60.0, credit = 1.0)  // 绩点 1.0，分数 60
        )
        val summary = GpaCalculator.calculate(courses)

        // GPA = (4.0*4 + 1.0*1) / 5 = 3.4
        assertEquals(3.4, summary.recommendationGpa!!, 1e-9)
        // 加权 = (90*4 + 60*1) / 5 = 84
        assertEquals(84.0, summary.weightedAverage!!, 1e-9)
        // 算术 = (90 + 60) / 2 = 75
        assertEquals(75.0, summary.arithmeticAverage!!, 1e-9)
        assertEquals(2, summary.includedCount)
        assertEquals(5.0, summary.includedCredits, 1e-9)
    }

    @Test
    fun `level courses join gpa with converted scores`() {
        val summary = GpaCalculator.calculate(listOf(level("优秀", 3.0), level("及格", 1.0)))
        // GPA = (4.0*3 + 1.3*1) / 4
        assertEquals((4.0 * 3 + 1.3) / 4.0, summary.recommendationGpa!!, 1e-9)
        // 加权 = (95*3 + 65*1) / 4
        assertEquals((95.0 * 3 + 65.0) / 4.0, summary.weightedAverage!!, 1e-9)
    }

    @Test
    fun `excluded courses are left out of every result`() {
        val courses = listOf(
            percent(90.0, credit = 3.0),
            percent(60.0, credit = 3.0, included = false)
        )
        val summary = GpaCalculator.calculate(courses)
        assertEquals(4.0, summary.recommendationGpa!!, 1e-9)
        assertEquals(90.0, summary.weightedAverage!!, 1e-9)
        assertEquals(90.0, summary.arithmeticAverage!!, 1e-9)
        assertEquals(1, summary.includedCount)
        assertEquals(3.0, summary.includedCredits, 1e-9)
        assertFalse(summary.breakdowns[1].included)
    }

    @Test
    fun `invalid courses are excluded defensively`() {
        val overRange = percent(120.0).copy(id = "bad-score")
        val zeroCredit = percent(85.0, credit = 0.0).copy(id = "bad-credit")
        val unknownLevel = level("丙等").copy(id = "bad-level")

        for (course in listOf(overRange, zeroCredit, unknownLevel)) {
            val summary = GpaCalculator.calculate(listOf(course))
            assertNull(summary.recommendationGpa)
            assertFalse(GpaCalculator.isValidCourse(course))
        }
    }

    @Test
    fun `empty input yields empty summary`() {
        val summary = GpaCalculator.calculate(emptyList())
        assertNull(summary.recommendationGpa)
        assertNull(summary.weightedAverage)
        assertNull(summary.arithmeticAverage)
        assertEquals(0, summary.includedCount)
    }

    @Test
    fun `all excluded behaves like empty`() {
        val summary = GpaCalculator.calculate(listOf(percent(90.0, included = false)))
        assertNull(summary.recommendationGpa)
        assertEquals(0, summary.includedCount)
    }

    @Test
    fun `course store roundtrip keeps order and flags`() {
        val temp = org.junit.rules.TemporaryFolder()
        temp.create()
        try {
            val courses = listOf(percent(88.0, 2.5), level("良好", 1.0), percent(55.0, included = false))
            cn.jlu.schedule.data.GpaCourseStore.save(temp.root, courses)
            val loaded = cn.jlu.schedule.data.GpaCourseStore.load(temp.root)
            assertEquals(courses, loaded)
            cn.jlu.schedule.data.GpaCourseStore.clear(temp.root)
            assertTrue(cn.jlu.schedule.data.GpaCourseStore.load(temp.root).isEmpty())
        } finally {
            temp.delete()
        }
    }
}
