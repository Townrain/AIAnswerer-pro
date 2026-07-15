package com.hwb.aianswerer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import com.hwb.aianswerer.models.CropRect
import com.hwb.aianswerer.safelyInvoke
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * ImageCropUtil unit tests.
 *
 * Covers: calculateFitScale / cropBitmap / saveBitmapToTempFile /
 * loadBitmapFromFile / deleteTempFile with edge cases and exception paths.
 */
class ImageCropUtilTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = createTempDir("image_crop_test_")
    }

    @After
    fun tearDown() {
        if (::tempDir.isInitialized && tempDir.exists()) {
            tempDir.deleteRecursively()
        }
        safelyInvoke { unmockkAll() }
    }

    // ==================== calculateFitScale ====================

    @Test
    fun `calculateFitScale - landscape image fit to portrait view`() {
        // 1920x1080 (landscape) -> 360x640 (portrait view)
        // scaleX = 360/1920 = 0.1875, scaleY = 640/1080 ~ 0.5926
        // min = 0.1875 (width-constrained)
        val result = ImageCropUtil.calculateFitScale(1920, 1080, 360, 640)
        assertEquals(0.1875f, result, 0.001f)
    }

    @Test
    fun `calculateFitScale - portrait image fit to portrait view`() {
        // 1080x1920 (portrait) -> 360x640 (portrait view)
        // scaleX = 360/1080 ~ 0.3333, scaleY = 640/1920 ~ 0.3333
        // min ~ 0.3333 (equal constraint)
        val result = ImageCropUtil.calculateFitScale(1080, 1920, 360, 640)
        assertEquals(0.3333f, result, 0.001f)
    }

    @Test
    fun `calculateFitScale - square image fit to portrait view`() {
        // 1080x1080 (square) -> 360x640 (portrait view)
        // scaleX = 360/1080 ~ 0.3333, scaleY = 640/1080 ~ 0.5926
        // min = 0.3333 (width-constrained)
        val result = ImageCropUtil.calculateFitScale(1080, 1080, 360, 640)
        assertEquals(0.3333f, result, 0.001f)
    }

    @Test
    fun `calculateFitScale - image smaller than view returns scale above 1`() {
        // 100x100 -> 360x640
        // scaleX = 360/100 = 3.6, scaleY = 640/100 = 6.4
        // min = 3.6 (width-constrained)
        val result = ImageCropUtil.calculateFitScale(100, 100, 360, 640)
        assertEquals(3.6f, result, 0.001f)
    }

    @Test
    fun `calculateFitScale - exact match returns scale of 1`() {
        // 360x640 -> 360x640
        // scaleX = 1.0, scaleY = 1.0, min = 1.0
        val result = ImageCropUtil.calculateFitScale(360, 640, 360, 640)
        assertEquals(1.0f, result, 0.0f)
    }

    @Test
    fun `calculateFitScale - tall narrow image fit to landscape view`() {
        // 480x1280 (very tall) -> 1920x1080 (landscape view)
        // scaleX = 1920/480 = 4.0, scaleY = 1080/1280 ~ 0.84375
        // min = 0.84375 (height-constrained)
        val result = ImageCropUtil.calculateFitScale(480, 1280, 1920, 1080)
        assertEquals(0.84375f, result, 0.001f)
    }

    // ==================== cropBitmap ====================

    @Test
    fun `cropBitmap - valid crop returns cropped bitmap`() {
        mockkStatic(Bitmap::class)

        val sourceBitmap = mockk<Bitmap>(relaxed = true)
        every { sourceBitmap.width } returns 1920
        every { sourceBitmap.height } returns 1080

        val croppedBitmap = mockk<Bitmap>(relaxed = true)
        every {
            Bitmap.createBitmap(sourceBitmap, 100, 100, 400, 400)
        } returns croppedBitmap

        val topLeft = PointF().apply { x = 100f; y = 100f }
        val bottomRight = PointF().apply { x = 500f; y = 500f }
        val cropRect = spyk(CropRect(topLeft = topLeft, bottomRight = bottomRight))
        every { cropRect.isValid(any(), any()) } returns true

        val result = ImageCropUtil.cropBitmap(sourceBitmap, cropRect)

        assertNotNull("Should return a non-null bitmap", result)
        assertSame("Should return the exact mocked cropped bitmap", croppedBitmap, result)
    }
    @Test
    fun `cropBitmap - invalid coordinates x exceeds width throws exception`() {
        safelyInvoke {
            val bitmap = mockk<Bitmap>(relaxed = true)
            every { bitmap.width } returns 1920
            every { bitmap.height } returns 1080

            // bottomRight.x = 2000 > bitmap.width = 1920 -> isValid returns false
            val cropRect = CropRect(
                topLeft = PointF(100f, 100f),
                bottomRight = PointF(2000f, 500f)
            )

            try {
                ImageCropUtil.cropBitmap(bitmap, cropRect)
                fail("Expected IllegalArgumentException for invalid coordinates")
            } catch (e: IllegalArgumentException) {
                assertTrue("Exception message should mention invalid coordinates",
                    e.message?.contains("Invalid crop coordinates") == true)
            }
        }
    }

    @Test
    fun `cropBitmap - negative x coordinate throws exception`() {
        safelyInvoke {
            val bitmap = mockk<Bitmap>(relaxed = true)
            every { bitmap.width } returns 1920
            every { bitmap.height } returns 1080

            // topLeft.x = -10 -> isValid returns false (x < 0)
            val cropRect = CropRect(
                topLeft = PointF(-10f, 100f),
                bottomRight = PointF(500f, 500f)
            )

            try {
                ImageCropUtil.cropBitmap(bitmap, cropRect)
                fail("Expected IllegalArgumentException for negative x")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `cropBitmap - zero width crop rect throws exception`() {
        safelyInvoke {
            val bitmap = mockk<Bitmap>(relaxed = true)
            every { bitmap.width } returns 1920
            every { bitmap.height } returns 1080

            // topLeft.x == bottomRight.x -> width == 0 -> isValid returns false
            val cropRect = CropRect(
                topLeft = PointF(500f, 100f),
                bottomRight = PointF(500f, 500f)
            )

            try {
                ImageCropUtil.cropBitmap(bitmap, cropRect)
                fail("Expected IllegalArgumentException for zero-width crop rect")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    @Test
    fun `cropBitmap - edge to edge crop covers entire image`() {
        mockkStatic(Bitmap::class)

        val sourceBitmap = mockk<Bitmap>(relaxed = true)
        every { sourceBitmap.width } returns 1920
        every { sourceBitmap.height } returns 1080

        val croppedBitmap = mockk<Bitmap>(relaxed = true)
        every {
            Bitmap.createBitmap(sourceBitmap, 0, 0, 1920, 1080)
        } returns croppedBitmap

        // CropRect exactly matches image dimensions -> edge-to-edge
        val topLeft = PointF().apply { x = 0f; y = 0f }
        val bottomRight = PointF().apply { x = 1920f; y = 1080f }
        val cropRect = spyk(CropRect(topLeft = topLeft, bottomRight = bottomRight))
        every { cropRect.isValid(any(), any()) } returns true

        val result = ImageCropUtil.cropBitmap(sourceBitmap, cropRect)

        assertNotNull("Edge-to-edge crop should return a bitmap", result)
        assertSame("Should return the exact cropped bitmap", croppedBitmap, result)
    }
    @Test
    fun `cropBitmap - negative y coordinate throws exception`() {
        safelyInvoke {
            val bitmap = mockk<Bitmap>(relaxed = true)
            every { bitmap.width } returns 1920
            every { bitmap.height } returns 1080

            // topLeft.y = -5 -> isValid returns false (y < 0)
            val cropRect = CropRect(
                topLeft = PointF(100f, -5f),
                bottomRight = PointF(500f, 500f)
            )

            try {
                ImageCropUtil.cropBitmap(bitmap, cropRect)
                fail("Expected IllegalArgumentException for negative y")
            } catch (e: IllegalArgumentException) {
                // Expected
            }
        }
    }

    // ==================== saveBitmapToTempFile ====================

    @Test
    fun `saveBitmapToTempFile - creates temp file and returns valid path`() {
        safelyInvoke {
            val bitmap = mockk<Bitmap>(relaxed = true)
            every {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, any())
            } returns true

            val path = ImageCropUtil.saveBitmapToTempFile(bitmap, tempDir)

            assertNotNull("Returned path should not be null", path)
            assertTrue("Returned path should not be empty", path.isNotEmpty())

            val file = File(path)
            assertTrue("Temp file should exist on disk", file.exists())

            verify(exactly = 1) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, any())
            }
        }
    }

    @Test
    fun `saveBitmapToTempFile - path contains expected prefix and extension`() {
        safelyInvoke {
            val bitmap = mockk<Bitmap>(relaxed = true)
            every {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, any())
            } returns true

            val path = ImageCropUtil.saveBitmapToTempFile(bitmap, tempDir)

            assertTrue("Path should contain 'temp_crop_'", path.contains("temp_crop_"))
            assertTrue("Path should end with '.jpg'", path.endsWith(".jpg"))
            assertTrue("Path should be within tempDir", path.startsWith(tempDir.absolutePath))
        }
    }

    // ==================== loadBitmapFromFile ====================

    @Test
    fun `loadBitmapFromFile - valid path returns bitmap`() {
        safelyInvoke {
            mockkStatic(BitmapFactory::class)

            val expectedBitmap = mockk<Bitmap>(relaxed = true)
            every { BitmapFactory.decodeFile("/valid/image.jpg") } returns expectedBitmap

            val result = ImageCropUtil.loadBitmapFromFile("/valid/image.jpg")

            assertNotNull("Should return a non-null bitmap", result)
            assertSame("Should return the expected bitmap", expectedBitmap, result)
        }
    }

    @Test
    fun `loadBitmapFromFile - non-existent file throws IllegalArgumentException`() {
        safelyInvoke {
            mockkStatic(BitmapFactory::class)
            every { BitmapFactory.decodeFile("/nonexistent/file.jpg") } returns null

            try {
                ImageCropUtil.loadBitmapFromFile("/nonexistent/file.jpg")
                fail("Expected IllegalArgumentException for non-existent file")
            } catch (e: IllegalArgumentException) {
                assertTrue("Exception message should mention file path",
                    e.message?.contains("/nonexistent/file.jpg") == true)
            }
        }
    }

    // ==================== deleteTempFile ====================

    @Test
    fun `deleteTempFile - existing file is deleted`() {
        val file = File(tempDir, "test_delete_me.tmp")
        assertTrue("File creation should succeed", file.createNewFile())
        assertTrue("File should exist before deletion", file.exists())

        ImageCropUtil.deleteTempFile(file.absolutePath)

        assertFalse("File should not exist after deleteTempFile", file.exists())
    }

    @Test
    fun `deleteTempFile - non-existent file handled without exception`() {
        val nonExistentPath = File(tempDir, "never_created.tmp").absolutePath
        assertFalse("Pre-condition: file should not exist", File(nonExistentPath).exists())

        try {
            ImageCropUtil.deleteTempFile(nonExistentPath)
            // Reaching here without exception means success
        } catch (e: Exception) {
            fail("deleteTempFile should not throw for non-existent file: ${e.message}")
        }
    }
}
