package cn.jlu.schedule.update.download

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import cn.jlu.schedule.update.model.UpdatePayload
import cn.jlu.schedule.update.security.UpdateSecurityConfig
import java.io.File
import java.security.MessageDigest

object ApkVerifier {

    /**
     * 校验下载完成的 APK 完整性：文件大小、SHA-256、包名、versionCode 以及签名证书指纹。
     */
    fun verify(context: Context, apkFile: File, payload: UpdatePayload) {
        // 1. 文件大小核验
        val actualSize = apkFile.length()
        if (actualSize != payload.apk.size) {
            throw IllegalStateException("APK 文件大小不匹配: 期望 ${payload.apk.size} 字节, 实际 $actualSize 字节")
        }

        // 2. SHA-256 完整性核验（GitHub Release 提供 digest 时强制校验）
        if (payload.apk.sha256.isNotBlank()) {
            val actualSha256 = calculateSha256(apkFile)
            if (!actualSha256.equals(payload.apk.sha256, ignoreCase = true)) {
                throw SecurityException("APK SHA-256 校验失败: 期望 ${payload.apk.sha256}, 实际 $actualSha256")
            }
        }

        // 3. Android PackageArchiveInfo 格式解析
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: throw IllegalStateException("无法解析 APK 安装包元数据，文件可能已损坏")

        // 4. 包名比对
        if (pkgInfo.packageName != payload.packageName) {
            throw SecurityException("APK 包名不匹配: 期望 ${payload.packageName}, 实际 ${pkgInfo.packageName}")
        }

        // 5. 版本号比对
        val actualVersionCode = extractVersionCode(pkgInfo)
        if (payload.apk.signerSha256.isNotBlank()) {
            if (actualVersionCode != payload.versionCode) {
                throw IllegalStateException("APK 版本号不匹配: 期望 ${payload.versionCode}, 实际 $actualVersionCode")
            }
        } else {
            val installedVersionCode = getInstalledVersionCode(context)
            if (installedVersionCode > 0 && actualVersionCode < installedVersionCode) {
                throw IllegalStateException("APK 版本号 ($actualVersionCode) 低于当前已安装版本 ($installedVersionCode)")
            }
        }

        // 6. 签名证书 SHA-256 指纹比对
        val certFingerprints = extractSigningCerts(pkgInfo).map { certBytes ->
            MessageDigest.getInstance("SHA-256").digest(certBytes).joinToString("") { "%02x".format(it) }
        }

        if (payload.apk.signerSha256.isNotBlank()) {
            if (!payload.apk.signerSha256.equals(UpdateSecurityConfig.OFFICIAL_SIGNER_SHA256, ignoreCase = true) ||
                certFingerprints.none { it.equals(UpdateSecurityConfig.OFFICIAL_SIGNER_SHA256, ignoreCase = true) }
            ) {
                throw SecurityException("APK 签名证书指纹不符: 实际 ${certFingerprints.joinToString()}")
            }
        } else {
            val installedCerts = getInstalledCertFingerprints(context, flags)
            if (installedCerts.isNotEmpty() && certFingerprints.none { fp ->
                    installedCerts.any { it.equals(fp, ignoreCase = true) }
                }
            ) {
                throw SecurityException("APK 签名证书与当前已安装版本不一致，无法覆盖安装")
            }
        }
    }

    private fun extractVersionCode(pkgInfo: android.content.pm.PackageInfo): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.versionCode
        }
    }

    private fun getInstalledVersionCode(context: Context): Int {
        return runCatching {
            extractVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
        }.getOrDefault(0)
    }

    private fun getInstalledCertFingerprints(context: Context, flags: Int): List<String> {
        return runCatching {
            val installedPkg = context.packageManager.getPackageInfo(context.packageName, flags)
            extractSigningCerts(installedPkg).map { certBytes ->
                MessageDigest.getInstance("SHA-256").digest(certBytes).joinToString("") { "%02x".format(it) }
            }
        }.getOrDefault(emptyList())
    }

    private fun extractSigningCerts(pkgInfo: android.content.pm.PackageInfo): List<ByteArray> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = pkgInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.map { it.toByteArray() }
            } else {
                signingInfo.signingCertificateHistory.map { it.toByteArray() }
            }
        } else {
            @Suppress("DEPRECATION")
            pkgInfo.signatures?.map { it.toByteArray() } ?: emptyList()
        }
    }

    fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(16384)
            var len: Int
            while (stream.read(buf).also { len = it } != -1) {
                digest.update(buf, 0, len)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
