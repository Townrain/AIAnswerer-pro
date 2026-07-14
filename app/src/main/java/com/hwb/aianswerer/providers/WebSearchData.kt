package com.hwb.aianswerer.providers

/**
 * 联网搜索服务商配置
 * 数据来源：Cherry Studio webSearchProviders.ts
 */
data class WebSearchProviderEntry(
    val id: String,
    val name: String,
    val apiHost: String = "",
    val url: String = "",
    val requiresApiKey: Boolean = true,
    val requiresHost: Boolean = false,
    val supportsBasicAuth: Boolean = false,
    val websites: WebSearchWebsites? = null
)

data class WebSearchWebsites(
    val official: String? = null,
    val apiKey: String? = null
)

/**
 * 用户的联网搜索服务商配置
 */
data class LocalWebSearchConfig(
    val id: String,
    val name: String,
    val apiHost: String,
    val url: String,
    val requiresApiKey: Boolean,
    val requiresHost: Boolean,
    val supportsBasicAuth: Boolean,
    val websites: WebSearchWebsites?,
    // 用户配置
    val apiKey: String = "",
    val enabled: Boolean = false,
    val customApiHost: String? = null,
    val basicAuthUsername: String = "",
    val basicAuthPassword: String = ""
)

/**
 * 内置联网搜索服务商列表（来自 Cherry Studio）
 */
object WebSearchProviders {

    val PROVIDERS = listOf(
        WebSearchProviderEntry(
            id = "tavily",
            name = "Tavily",
            apiHost = "https://api.tavily.com",
            requiresApiKey = true,
            websites = WebSearchWebsites(
                official = "https://tavily.com",
                apiKey = "https://app.tavily.com/home"
            )
        ),
        WebSearchProviderEntry(
            id = "zhipu",
            name = "Zhipu",
            apiHost = "https://open.bigmodel.cn/api/paas/v4/web_search",
            requiresApiKey = true,
            websites = WebSearchWebsites(
                official = "https://docs.bigmodel.cn/cn/guide/tools/web-search",
                apiKey = "https://zhipuaishengchan.datasink.sensorsdata.cn/t/yv"
            )
        ),
        WebSearchProviderEntry(
            id = "bocha",
            name = "Bocha",
            apiHost = "https://api.bochaai.com",
            requiresApiKey = true,
            websites = WebSearchWebsites(
                official = "https://bochaai.com",
                apiKey = "https://open.bochaai.com/overview"
            )
        ),
        WebSearchProviderEntry(
            id = "exa",
            name = "Exa",
            apiHost = "https://api.exa.ai",
            requiresApiKey = true,
            websites = WebSearchWebsites(
                official = "https://exa.ai",
                apiKey = "https://dashboard.exa.ai/api-keys"
            )
        ),
        WebSearchProviderEntry(
            id = "querit",
            name = "Querit",
            apiHost = "https://api.querit.ai",
            requiresApiKey = true,
            websites = WebSearchWebsites(
                official = "https://querit.ai",
                apiKey = "https://www.querit.ai/en/dashboard/api-keys"
            )
        ),
        WebSearchProviderEntry(
            id = "searxng",
            name = "Searxng",
            apiHost = "",
            requiresApiKey = false,
            requiresHost = true,
            supportsBasicAuth = true,
            websites = WebSearchWebsites(
                official = "https://docs.searxng.org"
            )
        ),
        WebSearchProviderEntry(
            id = "exa-mcp",
            name = "ExaMCP",
            apiHost = "https://mcp.exa.ai/mcp",
            requiresApiKey = false,
            websites = WebSearchWebsites(
                official = "https://exa.ai"
            )
        ),
        WebSearchProviderEntry(
            id = "local-google",
            name = "Google",
            url = "https://www.google.com/search?q=%s",
            requiresApiKey = false,
            websites = WebSearchWebsites(
                official = "https://www.google.com"
            )
        ),
        WebSearchProviderEntry(
            id = "local-bing",
            name = "Bing",
            url = "https://cn.bing.com/search?q=%s&ensearch=1",
            requiresApiKey = false,
            websites = WebSearchWebsites(
                official = "https://www.bing.com"
            )
        ),
        WebSearchProviderEntry(
            id = "local-baidu",
            name = "Baidu",
            url = "https://www.baidu.com/s?wd=%s",
            requiresApiKey = false,
            websites = WebSearchWebsites(
                official = "https://www.baidu.com"
            )
        )
    )
}
