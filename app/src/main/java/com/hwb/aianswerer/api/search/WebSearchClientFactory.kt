package com.hwb.aianswerer.api.search

import com.hwb.aianswerer.providers.LocalWebSearchConfig

object WebSearchClientFactory {
    fun create(config: LocalWebSearchConfig): BaseWebSearchProvider = when (config.id) {
        "tavily"        -> TavilySearchProvider(config)
        "zhipu"         -> ZhipuSearchProvider(config)
        "bocha"         -> BochaSearchProvider(config)
        "exa"           -> ExaSearchProvider(config)
        "querit"        -> QueritSearchProvider(config)
        "searxng"       -> SearXNGSProvider(config)
        "local-google"  -> LocalGoogleSearchProvider(config)
        "local-bing"    -> LocalBingSearchProvider(config)
        "local-baidu"   -> LocalBaiduSearchProvider(config)
        "exa-mcp"       -> ExaMCPSearchProvider(config)
        else            -> TavilySearchProvider(config) // fallback
    }
}
