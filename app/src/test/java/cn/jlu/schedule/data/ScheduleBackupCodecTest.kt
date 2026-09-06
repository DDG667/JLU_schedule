package cn.jlu.schedule.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBackupCodecTest {
    @Test
    fun encode_decode_roundTrip() {
        val profiles = listOf(
            ScheduleBackupCodec.BackupProfile(
                name = "主课表",
                semesterStartDate = "2026-08-31",
                courses = listOf(
                    ScheduleBackupCodec.BackupCourse(
                        courseName = "高等数学",
                        teacher = "张三",
                        semester = "2026-2027学年第一学期",
                        credit = 4.0,
                        rawWeekText = "1-16周",
                        meetings = listOf(
                            ScheduleBackupCodec.BackupMeeting(
                                weekday = "MONDAY",
                                startSection = 1,
                                endSection = 2,
                                weekRules = listOf(
                                    ScheduleBackupCodec.BackupWeekRule(1, 16, "ALL"),
                                    ScheduleBackupCodec.BackupWeekRule(17, 18, "EVEN")
                                ),
                                location = "三教302"
                            )
                        )
                    )
                )
            )
        )

        val decoded = ScheduleBackupCodec.decode(
            ScheduleBackupCodec.encode("2026-09-05T12:00:00", profiles)
        )

        assertNotNull(decoded)
        assertEquals(1, decoded!!.size)
        assertEquals("主课表", decoded[0].name)
        assertEquals("2026-08-31", decoded[0].semesterStartDate)
        val course = decoded[0].courses.single()
        assertEquals("高等数学", course.courseName)
        assertEquals(4.0, course.credit!!, 0.0001)
        val meeting = course.meetings.single()
        assertEquals("MONDAY", meeting.weekday)
        assertEquals(2, meeting.weekRules.size)
        assertEquals("EVEN", meeting.weekRules[1].parity)
    }

    @Test
    fun decode_rejectsInvalidContent() {
        assertNull(ScheduleBackupCodec.decode("not a json {"))
        assertNull(ScheduleBackupCodec.decode("{\"version\": 99, \"profiles\": []}"))
    }

    @Test
    fun decode_acceptsEmptyBackup() {
        val profiles = ScheduleBackupCodec.decode("{\"version\": 1, \"profiles\": []}")
        assertNotNull(profiles)
        assertTrue(profiles!!.isEmpty())
    }
}
