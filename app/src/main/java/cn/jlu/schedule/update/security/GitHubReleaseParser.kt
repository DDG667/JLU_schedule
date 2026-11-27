package cn.jlu.schedule.update.security

import cn.jlu.schedule.update.model.ApkMetadata
import cn.jlu.schedule.update.model.UpdatePayload
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.max

object GitHubReleaseParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    private data class GitHubReleaseResponse(
        val tag_name: String = "",
        val name: String = "",
        val draft: Boolean = false,
        val prerelease: Boolean = false,
        val published_at: String = "",
        val body: String? = null,
        val assets: List<GitHubReleaseAsset> = emptyList()
    )

    @Serializable
    private data class GitHubReleaseAsset(
        val name: String = "",
        val size: Long = 0L,
        val digest: String? = null,
        val browser_download_url: String = ""
    )

    fun parse(
        releaseJson: String,
        allowedDownloadPrefix: String = UpdateSecurityConfig.GITHUB_RELEASE_DOWNLOAD_PREFIX,
        allowInsecureForTest: Boolean = false
    ): Result<UpdatePayload> = runCatching {
        val release = json.decodeFromString<GitHubReleaseResponse>(releaseJson)
        if (release.draft || release.prerelease) {
            throw IllegalStateException("跳过草稿或预发布版本")
        }

        val versionName = release.tag_name.trim().removePrefix("v").removePrefix("V").trim()
        if (versionName.isBlank()) {
            throw IllegalArgumentException("Release 缺少有效 tag_name")
        }

        val apkAsset = release.assets.firstOrNull { asset ->
            val downloadUrl = asset.browser_download_url.trim()
            val validScheme = downloadUrl.startsWith("https://", ignoreCase = true) ||
                (allowInsecureForTest && downloadUrl.startsWith("http://", ignoreCase = true))
            asset.name.endsWith(".apk", ignoreCase = true) &&
                asset.size in 1..300_000_000L &&
                validScheme &&
                downloadUrl.startsWith(allowedDownloadPrefix)
        } ?: throw IllegalStateException("Release 中未找到有效的 APK 安装包")

        val sha256 = extractSha256(apkAsset.digest)
        val notes = formatReleaseNotes(release.body, release.name.ifBlank { release.tag_name })

        UpdatePayload(
            schema = 1,
            packageName = UpdateSecurityConfig.EXPECTED_PACKAGE_NAME,
            channel = "stable",
            versionCode = deriveVersionCode(versionName),
            versionName = versionName,
            minSdk = 26,
            publishedAt = release.published_at.ifBlank { "" },
            notes = notes,
            apk = ApkMetadata(
                size = apkAsset.size,
                sha256 = sha256,
                signerSha256 = "",
                mirrors = listOf(apkAsset.browser_download_url.trim())
            )
        )
    }

    fun compareVersionNames(remote: String, current: String): Int {
        val (rBase, rRev) = splitVersionParts(remote)
        val (cBase, cRev) = splitVersionParts(current)
        val maxLen = max(rBase.size, cBase.size)
        for (i in 0 until maxLen) {
            val rv = rBase.getOrElse(i) { 0 }
            val cv = cBase.getOrElse(i) { 0 }
            if (rv != cv) return rv.compareTo(cv)
        }
        return rRev.compareTo(cRev)
    }

    internal fun deriveVersionCode(versionName: String): Int {
        val (base, rev) = splitVersionParts(versionName)
        if (base.isEmpty()) return 1
        val major = base.getOrElse(0) { 0 }.coerceIn(0, 99)
        val minor = base.getOrElse(1) { 0 }.coerceIn(0, 99)
        val patch = base.getOrElse(2) { 0 }.coerceIn(0, 99)
        val baseCode = major * 10000 + minor * 100 + patch
        val safeRev = rev.coerceIn(0, 99)
        return (baseCode * 100 + safeRev).coerceAtLeast(1)
    }

    private fun splitVersionParts(raw: String): Pair<List<Int>, Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").trim()
        val dashIdx = cleaned.indexOf('-')
        val baseStr = if (dashIdx >= 0) cleaned.substring(0, dashIdx) else cleaned
        val suffixStr = if (dashIdx >= 0) cleaned.substring(dashIdx + 1) else ""
        val baseNums = Regex("\\d+")
            .findAll(baseStr)
            .mapNotNull { it.value.toIntOrNull() }
            .toList()
        val rev = Regex("\\d+").find(suffixStr)?.value?.toIntOrNull() ?: 0
        return baseNums to rev
    }

    private fun extractSha256(digest: String?): String {
        if (digest.isNullOrBlank()) return ""
        val match = Regex("^sha256:([0-9a-fA-F]{64})$").matchEntire(digest.trim()) ?: return ""
        return match.groupValues[1].lowercase()
    }

    private fun formatReleaseNotes(body: String?, fallbackTitle: String): List<String> {
        if (body.isNullOrBlank()) return listOf(fallbackTitle)
        val result = mutableListOf<String>()
        var totalChars = 0
        for (rawLine in body.lines()) {
            val cleaned = rawLine.trim()
                .replace(Regex("^#+\\s*"), "")
                .replace(Regex("^[-*•]\\s+"), "")
                .replace("**", "")
                .trim()
            if (cleaned.isEmpty()) continue
            if (totalChars + cleaned.length > 4800) break
            result.add(cleaned)
            totalChars += cleaned.length
            if (result.size >= 30) break
        }
        return result.ifEmpty { listOf(fallbackTitle) }
    }
}
