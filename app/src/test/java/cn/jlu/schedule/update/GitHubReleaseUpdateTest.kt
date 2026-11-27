package cn.jlu.schedule.update

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import cn.jlu.schedule.data.AppPreferences
import cn.jlu.schedule.update.model.UpdateCheckResult
import cn.jlu.schedule.update.repository.UpdateRepository
import cn.jlu.schedule.update.security.GitHubReleaseParser
import cn.jlu.schedule.update.security.UpdateSecurityConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubReleaseUpdateTest {

    private lateinit var server: MockWebServer
    private lateinit var inMemoryPrefs: InMemorySharedPreferences
    private lateinit var testContext: Context

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        inMemoryPrefs = InMemorySharedPreferences()
        testContext = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = inMemoryPrefs
            override fun getPackageName(): String = "cn.jlu.schedule"
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sampleReleaseJson(
        tagName: String = "v2.2.4-tokyo",
        draft: Boolean = false,
        prerelease: Boolean = false,
        downloadUrl: String? = null
    ): String {
        val assetUrl = downloadUrl
            ?: "https://github.com/DDG667/JLU_schedule/releases/download/$tagName/JLU.Schedule_${tagName.removePrefix("v")}.apk"
        return """
            {
              "tag_name": "$tagName",
              "name": "JLU Schedule $tagName",
              "draft": $draft,
              "prerelease": $prerelease,
              "published_at": "2026-11-27T12:00:00Z",
              "body": "## 更新内容\n- 跟进上游 v2.2.4 修复\n- 保留 **Tokyo Night** 主题",
              "assets": [
                {
                  "name": "JLU.Schedule_${tagName.removePrefix("v")}.apk",
                  "size": 2618643,
                  "digest": "sha256:a609476cb6d13d47a11be77662f3d9fbf1c53f194efe55292ea61832d845312e",
                  "browser_download_url": "$assetUrl"
                }
              ]
            }
        """.trimIndent()
    }

    @Test
    fun defaultEndpointsPointOnlyToForkRepo() {
        assertEquals(
            listOf("https://api.github.com/repos/DDG667/JLU_schedule/releases/latest"),
            UpdateSecurityConfig.DEFAULT_ENDPOINTS
        )
        UpdateSecurityConfig.DEFAULT_ENDPOINTS.forEach { url ->
            assertFalse(url.contains("JFyuhong", ignoreCase = true))
            assertFalse(url.contains("jfyuhong.top", ignoreCase = true))
        }
    }

    @Test
    fun parseValidForkRelease() {
        val result = GitHubReleaseParser.parse(sampleReleaseJson())
        assertTrue(result.isSuccess)
        val payload = result.getOrThrow()
        assertEquals("2.2.4-tokyo", payload.versionName)
        assertEquals("a609476cb6d13d47a11be77662f3d9fbf1c53f194efe55292ea61832d845312e", payload.apk.sha256)
        assertEquals(2618643L, payload.apk.size)
        assertEquals("", payload.apk.signerSha256)
        assertEquals(
            listOf("https://github.com/DDG667/JLU_schedule/releases/download/v2.2.4-tokyo/JLU.Schedule_2.2.4-tokyo.apk"),
            payload.apk.mirrors
        )
        assertTrue(payload.notes.any { it.contains("Tokyo Night") })
    }

    @Test
    fun parseRejectsForeignRepoUrlAndPrerelease() {
        val foreignUrl = "https://github.com/OtherUser/JLU_schedule/releases/download/v2.2.4/app.apk"
        assertTrue(GitHubReleaseParser.parse(sampleReleaseJson(downloadUrl = foreignUrl)).isFailure)
        assertTrue(GitHubReleaseParser.parse(sampleReleaseJson(prerelease = true)).isFailure)
        assertTrue(GitHubReleaseParser.parse(sampleReleaseJson(draft = true)).isFailure)
    }

    @Test
    fun compareVersionNamesHandlesTokyoSuffixes() {
        assertTrue(GitHubReleaseParser.compareVersionNames("2.2.4-tokyo", "2.2.2-tokyo") > 0)
        assertEquals(0, GitHubReleaseParser.compareVersionNames("2.2.4-tokyo", "v2.2.4-tokyo"))
        assertTrue(GitHubReleaseParser.compareVersionNames("2.2.2-tokyo", "2.2.4-tokyo") < 0)
        assertTrue(GitHubReleaseParser.compareVersionNames("2.2.4-tokyo.2", "2.2.4-tokyo.1") > 0)
    }

    @Test
    fun checkUpdateDetectsNewerReleaseEvenInDebugBuild() = runBlocking {
        val baseUrl = server.url("/download/").toString()
        val json = sampleReleaseJson(
            tagName = "v2.2.4-tokyo",
            downloadUrl = "${baseUrl}JLU.Schedule_2.2.4-tokyo.apk"
        )
        server.enqueue(MockResponse().setBody(json))

        val repo = UpdateRepository(
            appVersionCodeOverride = 8,
            appVersionNameOverride = "2.2.2-tokyo",
            isDebugOverride = true,
            allowedGitHubDownloadPrefix = baseUrl
        )
        val endpoint = server.url("/repos/DDG667/JLU_schedule/releases/latest").toString()
        val result = repo.checkUpdate(testContext, force = true, endpoints = listOf(endpoint), allowInDebug = true)
        assertTrue(result is UpdateCheckResult.UpdateAvailable)
        val available = result as UpdateCheckResult.UpdateAvailable
        assertEquals("2.2.4-tokyo", available.payload.versionName)
    }

    @Test
    fun checkUpdateReturnsNoUpdateWhenVersionMatches() = runBlocking {
        val baseUrl = server.url("/download/").toString()
        val json = sampleReleaseJson(
            tagName = "v2.2.4-tokyo",
            downloadUrl = "${baseUrl}JLU.Schedule_2.2.4-tokyo.apk"
        )
        server.enqueue(MockResponse().setBody(json))

        val repo = UpdateRepository(
            appVersionCodeOverride = 10,
            appVersionNameOverride = "2.2.4-tokyo",
            isDebugOverride = true,
            allowedGitHubDownloadPrefix = baseUrl
        )
        val endpoint = server.url("/repos/DDG667/JLU_schedule/releases/latest").toString()
        val result = repo.checkUpdate(testContext, force = false, endpoints = listOf(endpoint), allowInDebug = true)
        assertTrue(result is UpdateCheckResult.NoUpdate)
        assertEquals("当前已是最新版本", (result as UpdateCheckResult.NoUpdate).reason)
    }

    @Test
    fun checkUpdateRespectsIgnoredVersionUnlessForced() = runBlocking {
        val baseUrl = server.url("/download/").toString()
        val json = sampleReleaseJson(
            tagName = "v2.2.4-tokyo",
            downloadUrl = "${baseUrl}JLU.Schedule_2.2.4-tokyo.apk"
        )
        AppPreferences.setIgnoredUpdateVersion(
            testContext,
            GitHubReleaseParser.deriveVersionCode("2.2.4-tokyo")
        )

        val repo = UpdateRepository(
            appVersionCodeOverride = 8,
            appVersionNameOverride = "2.2.2-tokyo",
            isDebugOverride = true,
            allowedGitHubDownloadPrefix = baseUrl
        )
        val endpoint = server.url("/repos/DDG667/JLU_schedule/releases/latest").toString()

        server.enqueue(MockResponse().setBody(json))
        val autoResult = repo.checkUpdate(testContext, force = false, endpoints = listOf(endpoint), allowInDebug = true)
        assertTrue(autoResult is UpdateCheckResult.NoUpdate)

        server.enqueue(MockResponse().setBody(json))
        val manualResult = repo.checkUpdate(testContext, force = true, endpoints = listOf(endpoint), allowInDebug = true)
        assertTrue(manualResult is UpdateCheckResult.UpdateAvailable)
    }
}
