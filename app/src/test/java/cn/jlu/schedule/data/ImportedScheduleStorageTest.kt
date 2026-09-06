package cn.jlu.schedule.data

import cn.jlu.schedule.model.CourseSchedule
import cn.jlu.schedule.model.MeetingTime
import cn.jlu.schedule.model.WeekParity
import cn.jlu.schedule.model.WeekRule
import cn.jlu.schedule.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import kotlin.concurrent.thread

class ImportedScheduleStorageTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val filesDir: File
        get() = tmp.root

    private fun storageDir(): File = File(tmp.root, "timetables")

    private fun manualCourse(name: String = "手动课程"): ImportedScheduleStorage.ManualCourseInput {
        return ImportedScheduleStorage.ManualCourseInput(
            courseName = name,
            teacher = "老师",
            location = "教室",
            weekday = Weekday.MONDAY,
            startSection = 1,
            endSection = 2,
            startWeek = 1,
            endWeek = 16
        )
    }

    private fun parsedCourse(name: String): CourseSchedule {
        return CourseSchedule(
            courseName = name,
            teacher = "张三",
            semester = "2026-2027学年第一学期",
            credit = 2.0,
            rawWeekText = "1-16周",
            meetings = listOf(
                MeetingTime(Weekday.MONDAY, 1, 2, listOf(WeekRule(1, 16, WeekParity.ALL)), "三教302")
            )
        )
    }

    @Test
    fun init_createsSingleDefaultProfileWithEmptyCourses() {
        val profiles = ImportedScheduleStorage.listProfiles(filesDir)

        assertEquals(1, profiles.size)
        assertTrue(profiles.single().isActive)
        assertEquals("默认课表", profiles.single().name)
        assertTrue(ImportedScheduleStorage.loadActiveCourses(filesDir).isEmpty())
    }

    @Test
    fun addManualCourse_persistsAndReloads() {
        ImportedScheduleStorage.listProfiles(filesDir)
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse("大学物理"))

        val courses = ImportedScheduleStorage.loadActiveCourses(filesDir)
        assertEquals(1, courses.size)
        assertEquals("大学物理", courses.single().courseName)
        assertEquals(Weekday.MONDAY, courses.single().meetings.single().weekday)
    }

    @Test
    fun addManualCourse_rejectsInvalidInput() {
        ImportedScheduleStorage.listProfiles(filesDir)

        assertThrows(IllegalArgumentException::class.java) {
            ImportedScheduleStorage.addManualCourseToActive(
                filesDir,
                manualCourse().copy(startSection = 0)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportedScheduleStorage.addManualCourseToActive(
                filesDir,
                manualCourse().copy(endWeek = 31)
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ImportedScheduleStorage.addManualCourseToActive(
                filesDir,
                manualCourse().copy(courseName = "  ")
            )
        }
        assertTrue(ImportedScheduleStorage.loadActiveCourses(filesDir).isEmpty())
    }

    @Test
    fun createEmptyProfile_switchesActiveAndAddsProfile() {
        val original = ImportedScheduleStorage.listProfiles(filesDir).single()

        val created = ImportedScheduleStorage.createEmptyProfile(filesDir, "第二课表")

        val profiles = ImportedScheduleStorage.listProfiles(filesDir)
        assertEquals(2, profiles.size)
        assertTrue(profiles.single { it.id == created.id }.isActive)
        assertFalse(profiles.single { it.id == original.id }.isActive)
        assertTrue(ImportedScheduleStorage.loadActiveCourses(filesDir).isEmpty())
    }

    @Test
    fun deleteProfile_removesProfileAndCoursesFile() {
        val original = ImportedScheduleStorage.listProfiles(filesDir).single()
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse())
        val created = ImportedScheduleStorage.createEmptyProfile(filesDir, "待删除")

        val deleted = ImportedScheduleStorage.deleteProfile(filesDir, created.id)

        assertTrue(deleted)
        val profiles = ImportedScheduleStorage.listProfiles(filesDir)
        assertEquals(1, profiles.size)
        assertEquals(original.id, profiles.single().id)
        assertTrue(profiles.single().isActive)
        // 被删除课表的课程文件应一并清理
        assertEquals(1, storageDir().listFiles { f -> f.name.startsWith("courses_") }!!.size)
    }

    @Test
    fun deleteProfile_refusesToRemoveLastProfile() {
        val only = ImportedScheduleStorage.listProfiles(filesDir).single()

        assertFalse(ImportedScheduleStorage.deleteProfile(filesDir, only.id))
        assertEquals(1, ImportedScheduleStorage.listProfiles(filesDir).size)
    }

    @Test
    fun corruptedMeta_recoversProfilesFromOrphanCourseFiles() {
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse("持久课程"))
        val metaFile = File(storageDir(), "meta.json")
        assertTrue(metaFile.exists())
        metaFile.writeText("{corrupted json", Charsets.UTF_8)

        val profiles = ImportedScheduleStorage.listProfiles(filesDir)

        assertEquals(1, profiles.size)
        val courses = ImportedScheduleStorage.loadActiveCourses(filesDir)
        assertEquals(1, courses.size)
        assertEquals("持久课程", courses.single().courseName)
        // 损坏的 meta 已备份留存
        val backups = storageDir().listFiles { f -> f.name.startsWith("meta.json.corrupt") }
        assertTrue(backups.orEmpty().isNotEmpty())
    }

    @Test
    fun importParsedCourses_overwriteActive_replacesCoursesAndSemesterStart() {
        ImportedScheduleStorage.listProfiles(filesDir)
        val start = LocalDate.of(2026, 8, 31)

        val result = ImportedScheduleStorage.importParsedCourses(
            filesDir,
            listOf(parsedCourse("高等数学"), parsedCourse("大学英语")),
            ImportedScheduleStorage.ImportMode.OVERWRITE_ACTIVE,
            semesterStartDate = start
        )

        assertFalse(result.isNewProfile)
        assertEquals(2, result.courseCount)
        assertEquals(2, ImportedScheduleStorage.loadActiveCourses(filesDir).size)
        assertEquals(start, ImportedScheduleStorage.getActiveSemesterStartDate(filesDir))
    }

    @Test
    fun importParsedCourses_createNew_makesProfileActive() {
        ImportedScheduleStorage.listProfiles(filesDir)

        val result = ImportedScheduleStorage.importParsedCourses(
            filesDir,
            listOf(parsedCourse("高等数学")),
            ImportedScheduleStorage.ImportMode.CREATE_NEW,
            newProfileName = "导入课表"
        )

        assertTrue(result.isNewProfile)
        val profiles = ImportedScheduleStorage.listProfiles(filesDir)
        assertEquals(2, profiles.size)
        assertTrue(profiles.single { it.id == result.profileId }.isActive)
        assertEquals("导入课表", result.profileName)
    }

    @Test
    fun concurrentManualAdds_doNotLoseUpdates() {
        ImportedScheduleStorage.listProfiles(filesDir)

        val workers = (1..4).map { worker ->
            thread {
                repeat(10) { index ->
                    ImportedScheduleStorage.addManualCourseToActive(
                        filesDir,
                        manualCourse("课${worker}-$index")
                    )
                }
            }
        }
        workers.forEach { it.join() }

        assertEquals(40, ImportedScheduleStorage.loadActiveCourses(filesDir).size)
    }

    @Test
    fun setActiveSemesterStartDate_normalizesToMonday() {
        ImportedScheduleStorage.listProfiles(filesDir)

        ImportedScheduleStorage.setActiveSemesterStartDate(filesDir, LocalDate.of(2026, 9, 2))

        assertEquals(LocalDate.of(2026, 8, 31), ImportedScheduleStorage.getActiveSemesterStartDate(filesDir))
    }

    @Test
    fun backup_roundTrip_restoresAllProfiles() {
        ImportedScheduleStorage.listProfiles(filesDir)
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse("课A"))
        ImportedScheduleStorage.createEmptyProfile(filesDir, "空课表")
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse("课B"))

        val backup = ImportedScheduleStorage.exportBackup(filesDir)
        assertTrue(backup.contains("课A"))
        // 备份后再改动数据，恢复应回到备份时点
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse("多余课程"))

        val restored = ImportedScheduleStorage.importBackup(filesDir, backup)

        assertEquals(2, restored)
        val profiles = ImportedScheduleStorage.listProfiles(filesDir)
        assertEquals(2, profiles.size)
        // 恢复后激活第一个备份课表（默认课表，含 课A）
        val courses = ImportedScheduleStorage.loadActiveCourses(filesDir)
        assertEquals(1, courses.size)
        assertEquals("课A", courses.single().courseName)
    }

    @Test
    fun importBackup_rejectsInvalidContent() {
        ImportedScheduleStorage.listProfiles(filesDir)

        assertThrows(IllegalArgumentException::class.java) {
            ImportedScheduleStorage.importBackup(filesDir, "not a json {")
        }
        assertEquals(1, ImportedScheduleStorage.listProfiles(filesDir).size)
    }

    @Test
    fun exportActiveTimetableText_containsCourseInfo() {
        ImportedScheduleStorage.listProfiles(filesDir)
        ImportedScheduleStorage.addManualCourseToActive(filesDir, manualCourse("大学物理"))

        val text = ImportedScheduleStorage.exportActiveTimetableText(
            filesDir,
            listOf("08:00-08:45", "08:45-09:40")
        )

        assertTrue(text.contains("大学物理"))
        assertTrue(text.contains("第1-2节"))
        assertTrue(text.contains("08:00"))
        assertTrue(text.contains("总周数：16"))
    }

    @Test
    fun renameProfile_updatesNameAndRejectsBlank() {
        val profile = ImportedScheduleStorage.listProfiles(filesDir).single()

        assertFalse(ImportedScheduleStorage.renameProfile(filesDir, profile.id, "   "))
        assertTrue(ImportedScheduleStorage.renameProfile(filesDir, profile.id, "主课表"))
        assertEquals("主课表", ImportedScheduleStorage.listProfiles(filesDir).single().name)
    }
}
