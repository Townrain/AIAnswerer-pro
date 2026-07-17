package com.hwb.aianswerer.api.vision

import org.junit.Assert.*
import org.junit.Test

class VisionProviderTest {

    // ═══ ProviderConfigDescriptor ═══

    @Test
    fun `ProviderConfigDescriptor - 构造并读取fields`() {
        val fields = listOf(
            ConfigField.TextField(key = "key1", label = "标签1"),
            ConfigField.SwitchField(key = "sw1", label = "开关1")
        )
        val descriptor = ProviderConfigDescriptor(fields = fields)
        assertEquals(2, descriptor.fields.size)
        assertTrue(descriptor.fields[0] is ConfigField.TextField)
        assertTrue(descriptor.fields[1] is ConfigField.SwitchField)
    }

    @Test
    fun `ProviderConfigDescriptor - 空fields列表`() {
        val descriptor = ProviderConfigDescriptor(fields = emptyList())
        assertTrue(descriptor.fields.isEmpty())
    }

    // ═══ ConfigField sealed class ═══

    @Test
    fun `ConfigField密封类 - TextField完整构造`() {
        val field = ConfigField.TextField(
            key = "apiKey",
            label = "API Key",
            hint = "输入密钥",
            defaultValue = "sk-xxx",
            isPassword = true
        )
        assertEquals("apiKey", field.key)
        assertEquals("API Key", field.label)
        assertEquals("输入密钥", field.hint)
        assertEquals("sk-xxx", field.defaultValue)
        assertTrue(field.isPassword)
    }

    @Test
    fun `ConfigField密封类 - TextField默认值`() {
        val field = ConfigField.TextField(key = "k", label = "l")
        assertEquals("", field.hint)
        assertEquals("", field.defaultValue)
        assertFalse(field.isPassword)
    }

    @Test
    fun `ConfigField密封类 - SelectField构造`() {
        val options = listOf("opt1" to "选项1", "opt2" to "选项2")
        val field = ConfigField.SelectField(
            key = "model",
            label = "模型",
            options = options,
            defaultValue = "opt1"
        )
        assertEquals("model", field.key)
        assertEquals(2, field.options.size)
        assertEquals("opt1", field.defaultValue)
    }

    @Test
    fun `ConfigField密封类 - SelectField默认值`() {
        val field = ConfigField.SelectField(
            key = "m", label = "M",
            options = listOf("a" to "A")
        )
        assertEquals("", field.defaultValue)
    }

    @Test
    fun `ConfigField密封类 - SwitchField构造与默认值`() {
        val field = ConfigField.SwitchField(
            key = "jsonMode",
            label = "JSON模式",
            description = "开启后返回结构化JSON",
            defaultValue = true
        )
        assertEquals("jsonMode", field.key)
        assertEquals("JSON模式", field.label)
        assertEquals("开启后返回结构化JSON", field.description)
        assertTrue(field.defaultValue)
    }

    @Test
    fun `ConfigField密封类 - SwitchField默认关闭`() {
        val field = ConfigField.SwitchField(key = "sw", label = "开关")
        assertEquals("", field.description)
        assertFalse(field.defaultValue)
    }

    // ═══ ConfigValidationResult ═══

    @Test
    fun `ConfigValidationResult - 有效无错误`() {
        val result = ConfigValidationResult(isValid = true)
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `ConfigValidationResult - 无效带错误列表`() {
        val errors = listOf("API Key 不能为空", "地址不能为空")
        val result = ConfigValidationResult(isValid = false, errors = errors)
        assertFalse(result.isValid)
        assertEquals(2, result.errors.size)
        assertEquals("API Key 不能为空", result.errors[0])
    }

    // ═══ 接口契约验证（通过匿名内部类） ═══

    @Test
    fun `VisionProvider接口 - 方法签名可通过匿名实现验证`() {
        val provider = object : VisionProvider {
            override val providerId: String = "test"
            override val displayName: String = "测试"
            override suspend fun analyze(bitmap: android.graphics.Bitmap) =
                Result.success(VisionFilterResult())
            override suspend fun analyzeMultiple(bitmaps: List<android.graphics.Bitmap>) =
                Result.success(VisionFilterResult())
            override fun validateConfig() = ConfigValidationResult(isValid = true)
            override fun getConfigDescriptor() = ProviderConfigDescriptor(fields = emptyList())
        }
        assertEquals("test", provider.providerId)
        assertEquals("测试", provider.displayName)
        assertEquals(true, provider.validateConfig().isValid)
        assertEquals(0, provider.getConfigDescriptor().fields.size)
    }
}
