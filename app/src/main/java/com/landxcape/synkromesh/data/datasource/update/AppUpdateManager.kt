package com.landxcape.synkromesh.data.datasource.update

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.landxcape.synkromesh.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String
)

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean,
    val assetName: String
)

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/landxcape/Synkromesh/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Synkromesh-Android/${BuildConfig.VERSION_NAME}")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                connection.disconnect()
                throw IllegalStateException("GitHub API returned HTTP $responseCode")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(responseBody)
            val tagName = json.optString("tag_name", "")
            val releaseNotes = json.optString("body", "")

            val assetsArray = json.optJSONArray("assets")
            val assets = mutableListOf<ReleaseAsset>()
            if (assetsArray != null) {
                for (i in 0 until assetsArray.length()) {
                    val assetObj = assetsArray.getJSONObject(i)
                    val name = assetObj.optString("name", "")
                    val downloadUrl = assetObj.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && downloadUrl.isNotBlank()) {
                        assets.add(ReleaseAsset(name, downloadUrl))
                    }
                }
            }

            val supportedAbis = Build.SUPPORTED_ABIS.toList()
            val selectedAsset = selectBestAsset(assets, supportedAbis)
                ?: assets.firstOrNull()
                ?: throw IllegalStateException("No APK release asset found in latest release")

            val currentVersion = BuildConfig.VERSION_NAME
            val updateAvailable = isUpdateAvailable(currentVersion, tagName)

            AppUpdateInfo(
                currentVersion = currentVersion,
                latestVersion = tagName.removePrefix("v").trim(),
                releaseNotes = releaseNotes,
                downloadUrl = selectedAsset.downloadUrl,
                isUpdateAvailable = updateAvailable,
                assetName = selectedAsset.name
            )
        }
    }

    suspend fun downloadAndInstallUpdate(
        downloadUrl: String,
        onProgress: (Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply {
                if (!exists()) mkdirs()
            }
            val apkFile = File(updatesDir, "synkromesh-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
            }

            val contentLength = connection.contentLength
            var totalBytesRead = 0L

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        if (contentLength > 0) {
                            val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                            onProgress(progress)
                        }
                    }
                    output.flush()
                }
            }
            connection.disconnect()

            if (!apkFile.exists() || apkFile.length() == 0L) {
                throw IllegalStateException("Downloaded APK is empty or missing")
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, apkFile)

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
            Unit
        }
    }

    companion object {
        fun isUpdateAvailable(currentVersion: String, latestTag: String): Boolean {
            val currentClean = currentVersion.removePrefix("v").trim()
            val latestClean = latestTag.removePrefix("v").trim()

            if (latestClean.isBlank() || currentClean.isBlank()) return false

            val currentTokens = currentClean.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }
            val latestTokens = latestClean.split('.').mapNotNull { it.takeWhile { c -> c.isDigit() }.toIntOrNull() }

            val maxParts = maxOf(currentTokens.size, latestTokens.size)
            for (i in 0 until maxParts) {
                val currentPart = currentTokens.getOrElse(i) { 0 }
                val latestPart = latestTokens.getOrElse(i) { 0 }
                if (latestPart > currentPart) return true
                if (latestPart < currentPart) return false
            }

            return false
        }

        fun selectBestAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
            for (abi in supportedAbis) {
                val match = assets.firstOrNull { it.name.contains(abi, ignoreCase = true) }
                if (match != null) return match
            }
            return assets.firstOrNull { it.name.contains("universal", ignoreCase = true) }
                ?: assets.firstOrNull()
        }
    }
}
