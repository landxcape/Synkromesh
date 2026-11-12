package com.landxcape.synkromesh

import com.landxcape.synkromesh.data.datasource.update.AppUpdateManager
import com.landxcape.synkromesh.data.datasource.update.ReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManagerTest {

    @Test
    fun versionComparison_newerVersionAvailable() {
        assertTrue(AppUpdateManager.isUpdateAvailable("0.1.3", "v0.1.4"))
        assertTrue(AppUpdateManager.isUpdateAvailable("0.1.3", "0.1.4"))
        assertTrue(AppUpdateManager.isUpdateAvailable("0.1.3", "v0.2.0"))
        assertTrue(AppUpdateManager.isUpdateAvailable("0.1.3", "v1.0.0"))
        assertTrue(AppUpdateManager.isUpdateAvailable("v0.1.3", "v0.1.10"))
    }

    @Test
    fun versionComparison_sameOrOlderVersion_notAvailable() {
        assertFalse(AppUpdateManager.isUpdateAvailable("0.1.3", "v0.1.3"))
        assertFalse(AppUpdateManager.isUpdateAvailable("0.1.3", "0.1.3"))
        assertFalse(AppUpdateManager.isUpdateAvailable("0.1.4", "v0.1.3"))
        assertFalse(AppUpdateManager.isUpdateAvailable("1.0.0", "v0.9.9"))
        assertFalse(AppUpdateManager.isUpdateAvailable("0.2.0", "v0.1.9"))
    }

    @Test
    fun versionComparison_edgeCases_handledGracefully() {
        assertFalse(AppUpdateManager.isUpdateAvailable("", "v0.1.4"))
        assertFalse(AppUpdateManager.isUpdateAvailable("0.1.3", ""))
        assertFalse(AppUpdateManager.isUpdateAvailable("invalid", "invalid"))
        assertTrue(AppUpdateManager.isUpdateAvailable("0.1.3-beta", "v0.1.4"))
    }

    @Test
    fun assetSelection_matchesDevicePrimaryAbi() {
        val assets = listOf(
            ReleaseAsset("synkromesh-v0.1.4-universal-app-release.apk", "https://example.com/universal.apk"),
            ReleaseAsset("synkromesh-v0.1.4-armeabi-v7a-app-release.apk", "https://example.com/armv7.apk"),
            ReleaseAsset("synkromesh-v0.1.4-arm64-v8a-app-release.apk", "https://example.com/arm64.apk")
        )

        val selected = AppUpdateManager.selectBestAsset(assets, listOf("arm64-v8a", "armeabi-v7a"))
        assertNotNull(selected)
        assertEquals("synkromesh-v0.1.4-arm64-v8a-app-release.apk", selected?.name)
        assertEquals("https://example.com/arm64.apk", selected?.downloadUrl)
    }

    @Test
    fun assetSelection_fallsBackToUniversalWhenAbiNotFound() {
        val assets = listOf(
            ReleaseAsset("synkromesh-v0.1.4-armeabi-v7a-app-release.apk", "https://example.com/armv7.apk"),
            ReleaseAsset("synkromesh-v0.1.4-universal-app-release.apk", "https://example.com/universal.apk")
        )

        val selected = AppUpdateManager.selectBestAsset(assets, listOf("x86_64"))
        assertNotNull(selected)
        assertEquals("synkromesh-v0.1.4-universal-app-release.apk", selected?.name)
    }

    @Test
    fun assetSelection_emptyListReturnsNull() {
        val selected = AppUpdateManager.selectBestAsset(emptyList(), listOf("arm64-v8a"))
        assertNull(selected)
    }
}
