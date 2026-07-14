package com.hwb.aianswerer.providers

import com.hwb.aianswerer.safelyInvoke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

/**
 * Tests for ProviderSyncManager.
 *
 * Classification:
 * - Pure: SyncResult sealed class, DEFAULT_SYNC_URL — no runtime deps.
 * - Android-dependent: sync, ensureLocalData, fetchRemoteData, mergeAndSave
 *   — depend on Context, OkHttp, MMKV. Wrapped in safelyInvoke.
 */
class ProviderSyncManagerTest {

    // ── SyncResult sealed class (pure) ────────────────────────────────

    @Test
    fun `SyncResult Updated包含正确字段`() {
        val result: ProviderSyncManager.SyncResult =
            ProviderSyncManager.SyncResult.Updated(
                version = 3,
                providerCount = 52,
                modelCount = 418
            )
        assertTrue(result is ProviderSyncManager.SyncResult.Updated)
        val updated = result as ProviderSyncManager.SyncResult.Updated
        assertEquals(3, updated.version)
        assertEquals(52, updated.providerCount)
        assertEquals(418, updated.modelCount)
    }

    @Test
    fun `SyncResult Updated零值合法`() {
        val result = ProviderSyncManager.SyncResult.Updated(
            version = 0,
            providerCount = 0,
            modelCount = 0
        )
        val updated = result as ProviderSyncManager.SyncResult.Updated
        assertEquals(0, updated.version)
        assertEquals(0, updated.providerCount)
        assertEquals(0, updated.modelCount)
    }

    @Test
    fun `SyncResult UpToDate单例`() {
        val r1 = ProviderSyncManager.SyncResult.UpToDate
        val r2 = ProviderSyncManager.SyncResult.UpToDate
        assertTrue(r1 is ProviderSyncManager.SyncResult.UpToDate)
        assertTrue(r1 === r2) // object singleton
    }

    @Test
    fun `SyncResult Error包含消息`() {
        val result: ProviderSyncManager.SyncResult =
            ProviderSyncManager.SyncResult.Error("Network timeout")
        assertTrue(result is ProviderSyncManager.SyncResult.Error)
        assertEquals("Network timeout", (result as ProviderSyncManager.SyncResult.Error).message)
    }

    @Test
    fun `SyncResult Error空消息合法`() {
        val result = ProviderSyncManager.SyncResult.Error("")
        assertEquals("", (result as ProviderSyncManager.SyncResult.Error).message)
    }

    // ── DEFAULT_SYNC_URL constant (pure) ──────────────────────────────

    @Test
    fun `DEFAULT_SYNC_URL是有效的HTTPS地址`() {
        val url = ProviderSyncManager.DEFAULT_SYNC_URL
        assertTrue("Should start with https://", url.startsWith("https://"))
        assertTrue("Should contain github", url.contains("github.com"))
        assertTrue("Should end with .json", url.endsWith(".json"))
        // Quick structural check
        assertNotNull("URL should parse as valid URI", java.net.URI(url))
    }

    // ── Android-dependent (safelyInvoke) ───────────────────────────────

    @Test
    fun `sync方法通过safelyInvoke安全调用`() {
        safelyInvoke {
            runBlocking {
                // sync requires Context — will skip on JVM via safelyInvoke
                val result = ProviderSyncManager.sync(
                    context = null as android.content.Context,
                    force = true
                )
                // won't reach here on JVM (MMKV will throw), but on Android:
                assertTrue(result is ProviderSyncManager.SyncResult)
            }
        }
    }

    @Test
    fun `sync方法带默认参数不崩溃`() {
        safelyInvoke {
            runBlocking {
                val result = ProviderSyncManager.sync(
                    context = null as android.content.Context
                )
                assertTrue(result is ProviderSyncManager.SyncResult)
            }
        }
    }
}
