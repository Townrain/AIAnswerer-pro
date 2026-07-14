package com.hwb.aianswerer.models

import android.graphics.PointF
import org.junit.Assert.*
import org.junit.Test
import com.hwb.aianswerer.safelyInvoke

class CropRectTest {

    // ── Constructor ──

    @Test
    fun `CropRect构造函数 - 有效坐标应正确计算宽度和高度`() {
        safelyInvoke {
            val rect = CropRect(topLeft = PointF(10f, 20f), bottomRight = PointF(110f, 80f))
            assertEquals(100f, rect.width, 0.001f)
            assertEquals(60f, rect.height, 0.001f)
        }
    }

    @Test
    fun `CropRect构造函数 - 零宽度坐标应正确反映`() {
        safelyInvoke {
            val rect = CropRect(PointF(50f, 50f), PointF(50f, 100f))
            assertEquals(0f, rect.width, 0.001f)
            assertEquals(50f, rect.height, 0.001f)
        }
    }

    @Test
    fun `CropRect构造函数 - 零高度坐标应正确反映`() {
        safelyInvoke {
            val rect = CropRect(PointF(50f, 50f), PointF(100f, 50f))
            assertEquals(50f, rect.width, 0.001f)
            assertEquals(0f, rect.height, 0.001f)
        }
    }

    @Test
    fun `CropRect构造函数 - 负坐标应直接存储`() {
        safelyInvoke {
            val rect = CropRect(PointF(-10f, -20f), PointF(100f, 200f))
            assertEquals(-10f, rect.topLeft.x, 0.001f)
            assertEquals(-20f, rect.topLeft.y, 0.001f)
            assertEquals(110f, rect.width, 0.001f)
            assertEquals(220f, rect.height, 0.001f)
        }
    }

    // ── isValid ──

    @Test
    fun `isValid - 标准有效矩形应返回true`() {
        safelyInvoke {
            val rect = CropRect(PointF(50f, 50f), PointF(150f, 150f))
            assertTrue(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 左上角x为负数应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(-1f, 0f), PointF(100f, 100f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 左上角y为负数应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(0f, -1f), PointF(100f, 100f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 右下角x超出图片宽度应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(0f, 0f), PointF(201f, 100f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 右下角y超出图片高度应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(0f, 0f), PointF(100f, 201f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 左上角x不小于右下角x应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(100f, 0f), PointF(50f, 100f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 左上角y不小于右下角y应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(0f, 100f), PointF(100f, 50f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 零宽度应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(50f, 50f), PointF(50f, 100f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 零高度应返回false`() {
        safelyInvoke {
            val rect = CropRect(PointF(50f, 50f), PointF(100f, 50f))
            assertFalse(rect.isValid(200, 200))
        }
    }

    @Test
    fun `isValid - 矩形正好等于图片边界应返回true`() {
        safelyInvoke {
            val rect = CropRect(PointF(0f, 0f), PointF(200f, 200f))
            assertTrue(rect.isValid(200, 200))
        }
    }

    // ── createDefault ──

    @Test
    fun `createDefault - 应返回80百分比中心区域`() {
        safelyInvoke {
            val rect = CropRect.createDefault(1000, 800)
            assertEquals(100f, rect.topLeft.x, 0.001f)   // 1000 * 0.1
            assertEquals(80f, rect.topLeft.y, 0.001f)    // 800 * 0.1
            assertEquals(900f, rect.bottomRight.x, 0.001f) // 1000 * 0.9
            assertEquals(720f, rect.bottomRight.y, 0.001f) // 800 * 0.9
            assertEquals(800f, rect.width, 0.001f)       // 1000 * 0.8
            assertEquals(640f, rect.height, 0.001f)      // 800 * 0.8
        }
    }

    @Test
    fun `createDefault - 正方形图片应返回正方形区域`() {
        safelyInvoke {
            val rect = CropRect.createDefault(500, 500)
            assertEquals(400f, rect.width, 0.001f)
            assertEquals(400f, rect.height, 0.001f)
        }
    }

    @Test
    fun `createDefault - 极窄矩形应正确处理`() {
        safelyInvoke {
            val rect = CropRect.createDefault(100, 1)
            assertEquals(80f, rect.width, 0.001f)
            assertEquals(0.8f, rect.height, 0.001f)
        }
    }

    @Test
    fun `createDefault - 结果应通过isValid验证`() {
        safelyInvoke {
            val rect = CropRect.createDefault(1920, 1080)
            assertTrue(rect.isValid(1920, 1080))
        }
    }

    @Test
    fun `createDefault - 小尺寸图片应正确处理`() {
        safelyInvoke {
            val rect = CropRect.createDefault(10, 10)
            assertTrue(rect.isValid(10, 10))
            assertEquals(8f, rect.width, 0.001f)
            assertEquals(8f, rect.height, 0.001f)
        }
    }

    // ── copy ──

    @Test
    fun `copy方法 - 复制后属性与原始对象相同`() {
        safelyInvoke {
            val rect = CropRect(PointF(10f, 20f), PointF(100f, 200f))
            val copied = rect.copy()
            assertEquals(rect.topLeft.x, copied.topLeft.x, 0.001f)
            assertEquals(rect.topLeft.y, copied.topLeft.y, 0.001f)
            assertEquals(rect.bottomRight.x, copied.bottomRight.x, 0.001f)
            assertEquals(rect.bottomRight.y, copied.bottomRight.y, 0.001f)
            assertEquals(rect.width, copied.width, 0.001f)
            assertEquals(rect.height, copied.height, 0.001f)
        }
    }
}
