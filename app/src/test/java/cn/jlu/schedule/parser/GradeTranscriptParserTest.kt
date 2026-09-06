package cn.jlu.schedule.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GradeTranscriptParserTest {

    private val payload = """
        {"datas":{"xscjcx":{"totalSize":3,"rows":[
          {"KCH":"CS101","KCM":"数据结构","XF":3,"ZCJ":92.5,"XNXQDM":"2025-2026-1","KCXZDM_DISPLAY":"必修"},
          {"KCH":"PE100","KCM":"体育","XF":"1","ZCJ":"优秀","XNXQDM":"2025-2026-1"},
          {"KCH":"MA200","KCM":"概率论","XF":"3.5","ZCJ":null,"XNXQDM":"2025-2026-1"},
          {"KCH":"XX000","KCM":"劳动教育","XF":0,"ZCJ":"及格","XNXQDM":"2025-2026-2"}
        ]}}}
    """.trimIndent()

    @Test
    fun `recognizes grade payload shape`() {
        assertTrue(GradeTranscriptParser.isLikelyGradePayload(payload))
        assertFalse(GradeTranscriptParser.isLikelyGradePayload("""{"datas":{"xskcb":{"rows":[]}}}"""))
        assertFalse(GradeTranscriptParser.isLikelyGradePayload("系统异常"))
    }

    @Test
    fun `parses rows tolerating string and number types`() {
        val grades = GradeTranscriptParser.parse(payload)
        assertEquals(2, grades.size)
        assertEquals("数据结构", grades[0].name)
        assertEquals(3.0, grades[0].credit, 1e-9)
        assertEquals("92.5", grades[0].scoreText)
        assertEquals("2025-2026-1", grades[0].semesterCode)
        assertEquals("优秀", grades[1].scoreText)
        assertEquals(1.0, grades[1].credit, 1e-9)
    }

    @Test
    fun `malformed payload yields empty list`() {
        assertTrue(GradeTranscriptParser.parse("not json").isEmpty())
        assertTrue(GradeTranscriptParser.parse("""{"datas":null}""").isEmpty())
    }

    @Test
    fun `schedule payload is not mistaken for grades`() {
        val schedule = """{"datas":{"xskcb":{"rows":[{"KCM":"课"}]}}}
        """.trimIndent()
        assertFalse(GradeTranscriptParser.isLikelyGradePayload(schedule))
    }
}
