package com.hwb.aianswerer.utils

import com.google.gson.JsonSyntaxException
import org.junit.Assert.*
import org.junit.Test

/**
 * JsonUtil 单元测试
 * 覆盖：全局共享 Gson 实例的序列化/反序列化功能
 */
class JsonUtilTest {

    // ── 内部测试数据类 ──
    data class TestPerson(val name: String, val age: Int, val email: String?)
    data class TestNested(val person: TestPerson, val tags: List<String>)
    data class TestContainer(val items: List<TestPerson>)

    // ── Gson 实例基础测试 ──

    @Test
    fun `gson - 实例不为null`() {
        assertNotNull(JsonUtil.gson)
    }

    @Test
    fun `gson - 多次访问返回同一实例（单例）`() {
        val first = JsonUtil.gson
        val second = JsonUtil.gson
        assertSame(first, second)
    }

    // ── 序列化 toJson ──

    @Test
    fun `toJson - 简单对象序列化`() {
        val person = TestPerson("张三", 25, null)
        val json = JsonUtil.gson.toJson(person)
        assertTrue(json.contains("\"name\""))
        assertTrue(json.contains("张三"))
        assertTrue(json.contains("\"age\""))
        assertTrue(json.contains("25"))
    }

    @Test
    fun `toJson - null字段不输出`() {
        val person = TestPerson("李四", 30, null)
        val json = JsonUtil.gson.toJson(person)
        assertFalse(json.contains("email"))
    }

    @Test
    fun `toJson - 非null字段输出`() {
        val person = TestPerson("王五", 28, "wang@test.com")
        val json = JsonUtil.gson.toJson(person)
        assertTrue(json.contains("wang@test.com"))
    }

    @Test
    fun `toJson - disableHtmlEscaping 使尖括号不被转义`() {
        val person = TestPerson("<script>", 1, "a<b>c")
        val json = JsonUtil.gson.toJson(person)
        assertTrue(json.contains("<script>"))
        assertTrue(json.contains("a<b>c"))
        assertFalse(json.contains("\\u003c"))
    }

    // ── 反序列化 fromJson ──

    @Test
    fun `fromJson - 有效JSON解析为对象`() {
        val json = """{"name":"赵六","age":22,"email":"zhao@test.com"}"""
        val person = JsonUtil.gson.fromJson(json, TestPerson::class.java)
        assertEquals("赵六", person.name)
        assertEquals(22, person.age)
        assertEquals("zhao@test.com", person.email)
    }

    @Test
    fun `fromJson - 可选字段mull值解析为null`() {
        val json = """{"name":"钱七","age":35,"email":null}"""
        val person = JsonUtil.gson.fromJson(json, TestPerson::class.java)
        assertEquals("钱七", person.name)
        assertEquals(35, person.age)
        assertNull(person.email)
    }

    @Test
    fun `fromJson - 缺少可选字段解析为null`() {
        val json = """{"name":"孙八","age":40}"""
        val person = JsonUtil.gson.fromJson(json, TestPerson::class.java)
        assertEquals("孙八", person.name)
        assertEquals(40, person.age)
        assertNull(person.email)
    }

    @Test
    fun `fromJson - 嵌套对象正确解析`() {
        val json = """{"person":{"name":"嵌套","age":1,"email":"a@b"},"tags":["tag1","tag2"]}"""
        val nested = JsonUtil.gson.fromJson(json, TestNested::class.java)
        assertEquals("嵌套", nested.person.name)
        assertEquals(1, nested.person.age)
        assertEquals(2, nested.tags.size)
        assertEquals("tag1", nested.tags[0])
        assertEquals("tag2", nested.tags[1])
    }

    @Test
    fun `fromJson - 数组正确解析`() {
        val json = """{"items":[{"name":"A","age":10,"email":null},{"name":"B","age":20,"email":"b@c"}]}"""
        val container = JsonUtil.gson.fromJson(json, TestContainer::class.java)
        assertEquals(2, container.items.size)
        assertEquals("A", container.items[0].name)
        assertEquals("B", container.items[1].name)
        assertNull(container.items[0].email)
        assertEquals("b@c", container.items[1].email)
    }

    // ── 错误处理 ──

    @Test(expected = JsonSyntaxException::class)
    fun `fromJson - 错误JSON抛出JsonSyntaxException`() {
        val badJson = """{"name": "周九", "age": }"""
        JsonUtil.gson.fromJson(badJson, TestPerson::class.java)
    }

    @Test(expected = JsonSyntaxException::class)
    fun `fromJson - 不完整JSON抛出异常`() {
        val incomplete = """{"name": "吴十", "age": 50"""
        JsonUtil.gson.fromJson(incomplete, TestPerson::class.java)
    }

    @Test
    fun `fromJson - 空字符串返回null`() {
        assertNull(JsonUtil.gson.fromJson("", TestPerson::class.java))
    }

    @Test
    fun `fromJson - 空白字符串返回null`() {
        assertNull(JsonUtil.gson.fromJson("   ", TestPerson::class.java))
    }

    @Test
    fun `fromJson - null输入返回null`() {
        assertNull(JsonUtil.gson.fromJson(null as String?, TestPerson::class.java))
    }

    // ── 转义字符测试 ──

    @Test
    fun `fromJson - 包含转义字符的JSON正确解析`() {
        val json = """{"name":"hello\"world","age":5,"email":null}"""
        val person = JsonUtil.gson.fromJson(json, TestPerson::class.java)
        assertEquals("hello\"world", person.name)
    }

    @Test
    fun `toJson - 字符串含引号正确序列化`() {
        val person = TestPerson("a\"b\"c", 99, null)
        val json = JsonUtil.gson.toJson(person)
        assertTrue(json.contains("a\\\"b\\\"c"))
    }
}
