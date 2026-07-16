package com.hwb.aianswerer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 主题预设定义 — 8 款内置可视化主题，每款包含亮色/暗色两套配色。
 *
 * 使用 ThemeManager 切换主题，主题持久化通过 MMKV。
 * 自定义主题通过 JSON 导入，存储为 CustomPreset。
 */
object ThemePresets {

    /** 内置主题 ID 常量 */
    const val WARM_AUTUMN = "warm_autumn"
    const val PREMIUM_INDIGO = "premium_indigo"
    const val OCEAN_DEPTHS = "ocean_depths"
    const val FOREST_CALM = "forest_calm"
    const val ROSE_GOLD = "rose_gold"
    const val AURORA_FLUX = "aurora_flux"
    const val DEEP_CREAM = "deep_cream"
    const val SOFT_CORAL = "soft_coral"

    /** 内置主题 ID → (显示名, 亮色Th, 暗色Th) */
    val BUILT_IN: Map<String, Triple<String, Th, Th>> by lazy { mapOf(
        WARM_AUTUMN to Triple("暖秋", WarmAutumnLight, WarmAutumnDark),
        PREMIUM_INDIGO to Triple("暗夜靛蓝", PremiumIndigoLight, PremiumIndigoDark),
        OCEAN_DEPTHS to Triple("深海蓝调", OceanDepthsLight, OceanDepthsDark),
        FOREST_CALM to Triple("静谧森林", ForestCalmLight, ForestCalmDark),
        ROSE_GOLD to Triple("玫瑰金粉", RoseGoldLight, RoseGoldDark),
        AURORA_FLUX to Triple("极光流明", AuroraFluxLight, AuroraFluxDark),
        DEEP_CREAM to Triple("深层奶白", DeepCreamLight, DeepCreamDark),
        SOFT_CORAL to Triple("柔桃珊瑚", SoftCoralLight, SoftCoralDark),
    ) }

    // ═══════════════════════════════════════════════
    //  暖秋 (Warm Autumn) — 当前 SandboxTheme 默认配色
    // ═══════════════════════════════════════════════
    val WarmAutumnLight by lazy {
        Th(
            bg1 = Color(0xFFC0AEE0), bg2 = Color(0xFFD8C8EC), bg3 = Color(0xFFECD8C0), bg4 = Color(0xFFD8C8EC), bg5 = Color(0xFFC0AEE0),
            p = Color(0xFFD97757), pe = Color(0xFFE8A850), pd = Color(0xFFC56A4A),
            pc = Color(0xFFFBE8E0), opc = Color(0xFF5C2D1A),
            ok = Color(0xFF7DA38B),
            ob = Color(0xFF3C2A1F), osv = Color(0xFF8B7E74),
            gt = Color(0x0DFFFFFF), gb = Color(0xF4FFFFFF), gdp = Color(0x08FFFFFF),
            ht = Color(0xB0FFF8F0), hdp = Color(0x78FFF0E0),
            ac = Color(0xFFE8A850), ua = Color(0xFFE8A850), ual = Color(0xFFE8A850),
            to = Color(0x28C49A6C), err = Color(0xFFD95757), w = Color.White, isLight = true
        )
    }
    val WarmAutumnDark by lazy {
        Th(
            bg1 = Color(0xFF0E0A12), bg2 = Color(0xFF1C141A), bg3 = Color(0xFF3A2A24), bg4 = Color(0xFF3A281A), bg5 = Color(0xFF5A3C14),
            p = Color(0xFF9B8FF8), pe = Color(0xFFCBC4FF), pd = Color(0xFFC56A4A),
            pc = Color(0xFF5C2D1A), opc = Color(0xFFFBE8E0),
            ok = Color(0xFF34C759),
            ob = Color(0xFFF5F3F8), osv = Color(0xFFD0C8C0),
            gt = Color(0x30FFFFFF), gb = Color(0x3DFFFFFF), gdp = Color(0x22FFFFFF),
            ht = Color(0x38FFFFFF), hdp = Color(0x2CFFFFFF),
            ac = Color(0xFFB87A24), ua = Color(0xFF9B8FF8), ual = Color(0xFFCBC4FF),
            to = Color(0x1AD0C8C0), err = Color(0xFFFF6961), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  暗夜靛蓝 (Premium Indigo) — 基于 Color.kt 主色系
    // ═══════════════════════════════════════════════
    val PremiumIndigoLight by lazy {
        Th(
            bg1 = Color(0xFFF2EFE6), bg2 = Color(0xFFEDEBF9), bg3 = Color(0xFFECE8DF), bg4 = Color(0xFFEDEBF9), bg5 = Color(0xFFF2EFE6),
            p = Color(0xFF6C5CE7), pe = Color(0xFF8B7CF0), pd = Color(0xFF4A3CC8),
            pc = Color(0xFFEDEBF9), opc = Color(0xFF1E1050),
            ok = Color(0xFF34C759),
            ob = Color(0xFF1C1B1F), osv = Color(0xFF6B6878),
            gt = Color(0xB3FFFFFF), gb = Color(0xE0FFFFFF), gdp = Color(0xD0FFFFFF),
            ht = Color(0xE6F5F3FF), hdp = Color(0xCCEDEBFF),
            ac = Color(0xFFBE8B5E), ua = Color(0xFF6C5CE7), ual = Color(0xFFB8B0F8),
            to = Color(0xFFE5E5EA), err = Color(0xFFFF3B30), w = Color.White, isLight = true
        )
    }
    val PremiumIndigoDark by lazy {
        Th(
            bg1 = Color(0xFF17141F), bg2 = Color(0xFF222230), bg3 = Color(0xFF252030), bg4 = Color(0xFF222230), bg5 = Color(0xFF17141F),
            p = Color(0xFFB8B0F8), pe = Color(0xFFCBC4FF), pd = Color(0xFF8B7CF0),
            pc = Color(0xFF3A3570), opc = Color(0xFFEDEBF9),
            ok = Color(0xFF34C759),
            ob = Color(0xFFF5F3F8), osv = Color(0xFFADA8BE),
            gt = Color(0x1AFFFFFF), gb = Color(0x1FFFFFFF), gdp = Color(0x0FFFFFFF),
            ht = Color(0x14FFFFFF), hdp = Color(0x0CFFFFFF),
            ac = Color(0xFFD4A44B), ua = Color(0xFFB8B0F8), ual = Color(0xFFCBC4FF),
            to = Color(0xFF3A3840), err = Color(0xFFFF6961), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  深海蓝调 (Ocean Depths) — 冷静深蓝 + 青绿强调
    // ═══════════════════════════════════════════════
    val OceanDepthsLight by lazy {
        Th(
            bg1 = Color(0xFFE8F0FE), bg2 = Color(0xFFDCE8FA), bg3 = Color(0xFFE0F0F5), bg4 = Color(0xFFDCE8FA), bg5 = Color(0xFFE8F0FE),
            p = Color(0xFF2563EB), pe = Color(0xFF3B82F6), pd = Color(0xFF1D4ED8),
            pc = Color(0xFFDBEAFE), opc = Color(0xFF1E3A5F),
            ok = Color(0xFF10B981),
            ob = Color(0xFF0F172A), osv = Color(0xFF475569),
            gt = Color(0xB3FFFFFF), gb = Color(0xE0FFFFFF), gdp = Color(0xD0FFFFFF),
            ht = Color(0xE6EEF2FF), hdp = Color(0xCCE0ECFF),
            ac = Color(0xFF06B6D4), ua = Color(0xFF2563EB), ual = Color(0xFF93C5FD),
            to = Color(0xFFCBD5E1), err = Color(0xFFEF4444), w = Color.White, isLight = true
        )
    }
    val OceanDepthsDark by lazy {
        Th(
            bg1 = Color(0xFF0F172A), bg2 = Color(0xFF1E293B), bg3 = Color(0xFF1A2740), bg4 = Color(0xFF1E293B), bg5 = Color(0xFF0F172A),
            p = Color(0xFF60A5FA), pe = Color(0xFF93C5FD), pd = Color(0xFF3B82F6),
            pc = Color(0xFF1E3A5F), opc = Color(0xFFDBEAFE),
            ok = Color(0xFF34D399),
            ob = Color(0xFFF1F5F9), osv = Color(0xFF94A3B8),
            gt = Color(0x14FFFFFF), gb = Color(0x1AFFFFFF), gdp = Color(0x0CFFFFFF),
            ht = Color(0x14FFFFFF), hdp = Color(0x0CFFFFFF),
            ac = Color(0xFF22D3EE), ua = Color(0xFF60A5FA), ual = Color(0xFF93C5FD),
            to = Color(0xFF334155), err = Color(0xFFFCA5A5), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  静谧森林 (Forest Calm) — 自然绿色系 + 大地色调
    // ═══════════════════════════════════════════════
    val ForestCalmLight by lazy {
        Th(
            bg1 = Color(0xFFECF5EC), bg2 = Color(0xFFE0F0E0), bg3 = Color(0xFFEFEDE6), bg4 = Color(0xFFE0F0E0), bg5 = Color(0xFFECF5EC),
            p = Color(0xFF2D8A4E), pe = Color(0xFF3BA765), pd = Color(0xFF1E6B3A),
            pc = Color(0xFFDCF5E5), opc = Color(0xFF1A3D24),
            ok = Color(0xFF2D8A4E),
            ob = Color(0xFF1C2321), osv = Color(0xFF5C6B62),
            gt = Color(0xB3FFFFFF), gb = Color(0xE0FFFFFF), gdp = Color(0xD0FFFFFF),
            ht = Color(0xE6F5F2EF), hdp = Color(0xCCEDE8E0),
            ac = Color(0xFFD4952D), ua = Color(0xFF2D8A4E), ual = Color(0xFF6FBF85),
            to = Color(0xFFD4DCD0), err = Color(0xFFDC3545), w = Color.White, isLight = true
        )
    }
    val ForestCalmDark by lazy {
        Th(
            bg1 = Color(0xFF0E1A10), bg2 = Color(0xFF162318), bg3 = Color(0xFF1C2E1A), bg4 = Color(0xFF162318), bg5 = Color(0xFF0E1A10),
            p = Color(0xFF6FBF85), pe = Color(0xFF8FD4A0), pd = Color(0xFF3BA765),
            pc = Color(0xFF1A3D24), opc = Color(0xFFDCF5E5),
            ok = Color(0xFF34C759),
            ob = Color(0xFFE8EFEA), osv = Color(0xFF9AB0A0),
            gt = Color(0x0FFFFFFF), gb = Color(0x14FFFFFF), gdp = Color(0x0CFFFFFF),
            ht = Color(0x10FFFFFF), hdp = Color(0x0AFFFFFF),
            ac = Color(0xFFD4952D), ua = Color(0xFF6FBF85), ual = Color(0xFF8FD4A0),
            to = Color(0xFF2A3D2E), err = Color(0xFFFF6961), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  玫瑰金粉 (Rose Gold) — 暖玫色 + 金色强调
    // ═══════════════════════════════════════════════
    val RoseGoldLight by lazy {
        Th(
            bg1 = Color(0xFFFBF0F0), bg2 = Color(0xFFF8E8EC), bg3 = Color(0xFFF5EDE4), bg4 = Color(0xFFF8E8EC), bg5 = Color(0xFFFBF0F0),
            p = Color(0xFFD9466F), pe = Color(0xFFEC6B8A), pd = Color(0xFFBE185D),
            pc = Color(0xFFFDE8EE), opc = Color(0xFF4A1425),
            ok = Color(0xFF22C55E),
            ob = Color(0xFF2D1A24), osv = Color(0xFF8B6B78),
            gt = Color(0xBFFFFFFF), gb = Color(0xE0FFFFFF), gdp = Color(0xD0FFFFFF),
            ht = Color(0xE6FFF0F3), hdp = Color(0xCCFEE4EA),
            ac = Color(0xFFD4952D), ua = Color(0xFFD9466F), ual = Color(0xFFFDA4AF),
            to = Color(0xFFE8DDE0), err = Color(0xFFDC2626), w = Color.White, isLight = true
        )
    }
    val RoseGoldDark by lazy {
        Th(
            bg1 = Color(0xFF1A0D10), bg2 = Color(0xFF24141A), bg3 = Color(0xFF2E1A20), bg4 = Color(0xFF24141A), bg5 = Color(0xFF1A0D10),
            p = Color(0xFFFDA4AF), pe = Color(0xFFFECDD3), pd = Color(0xFFEC6B8A),
            pc = Color(0xFF4A1425), opc = Color(0xFFFDE8EE),
            ok = Color(0xFF4ADE80),
            ob = Color(0xFFFCE7F3), osv = Color(0xFFC0A0B0),
            gt = Color(0x14FFFFFF), gb = Color(0x1AFFFFFF), gdp = Color(0x0CFFFFFF),
            ht = Color(0x14FFFFFF), hdp = Color(0x0CFFFFFF),
            ac = Color(0xFFFBBF24), ua = Color(0xFFFDA4AF), ual = Color(0xFFFECDD3),
            to = Color(0xFF3D2A30), err = Color(0xFFFCA5A5), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  极光流明 (Aurora Flux) — 翡翠青绿 + 极光紫罗兰
    //  冰川白昼与宇宙虚空，理性AI与人性洞察的交融
    // ═══════════════════════════════════════════════
    val AuroraFluxLight by lazy {
        Th(
            bg1 = Color(0xFFF4F7F5), bg2 = Color(0xFFECF2F0), bg3 = Color(0xFFE4EEE9), bg4 = Color(0xFFECF2F0), bg5 = Color(0xFFF4F7F5),
            p = Color(0xFF3D9E8C), pe = Color(0xFF5BC4B0), pd = Color(0xFF2D7A6C),
            pc = Color(0xFFE0F5F0), opc = Color(0xFF1A3D35),
            ok = Color(0xFF4CAF88),
            ob = Color(0xFF1C2422), osv = Color(0xFF6B7872),
            gt = Color(0x0DFFFFFF), gb = Color(0xF0FFFFFF), gdp = Color(0x08FFFFFF),
            ht = Color(0xCCF0F8F5), hdp = Color(0x99E0F0E8),
            ac = Color(0xFF8B6CE7), ua = Color(0xFF8B6CE7), ual = Color(0xFFC0A8F8),
            to = Color(0xFFD8E0DC), err = Color(0xFFE05555), w = Color.White, isLight = true
        )
    }
    val AuroraFluxDark by lazy {
        Th(
            bg1 = Color(0xFF080E10), bg2 = Color(0xFF0E181C), bg3 = Color(0xFF142420), bg4 = Color(0xFF0E181C), bg5 = Color(0xFF080E10),
            p = Color(0xFF5BC4B0), pe = Color(0xFF80D8C8), pd = Color(0xFF3D9E8C),
            pc = Color(0xFF1A3D35), opc = Color(0xFFE0F5F0),
            ok = Color(0xFF4ADE80),
            ob = Color(0xFFE8F0ED), osv = Color(0xFF8A9B95),
            gt = Color(0x0CFFFFFF), gb = Color(0x1AFFFFFF), gdp = Color(0x06FFFFFF),
            ht = Color(0x0FFFFFFF), hdp = Color(0x08FFFFFF),
            ac = Color(0xFFB8A0F8), ua = Color(0xFFB8A0F8), ual = Color(0xFFD4C8FF),
            to = Color(0xFF2A3834), err = Color(0xFFFF7070), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  深层奶白 (Deep Cream) — 暖调升级
    //  整条背景渐变压低一个暖度档位，手机上不显白
    //  ═══════════════════════════════════════════════
    val DeepCreamLight by lazy {
        Th(
            bg1 = Color(0xFFF8F4EE), bg2 = Color(0xFFF2EBE0), bg3 = Color(0xFFE9DFCF), bg4 = Color(0xFFF2EBE0), bg5 = Color(0xFFF8F4EE),
            p = Color(0xFFCC785C), pe = Color(0xFFE0A090), pd = Color(0xFFA9583E),
            pc = Color(0xFFF5E8E0), opc = Color(0xFF3D1A0A),
            ok = Color(0xFF5DB872),
            ob = Color(0xFF141413), osv = Color(0xFF6C6A64),
            gt = Color(0x0DFFFFFF), gb = Color(0xF0FFFFFF), gdp = Color(0x08FFFFFF),
            ht = Color(0xC0FFF5EB), hdp = Color(0x8DFFEBD8),
            ac = Color(0xFFD4A44B), ua = Color(0xFFCC785C), ual = Color(0xFFE0A090),
            to = Color(0xFFE6DFD8), err = Color(0xFFC64545), w = Color.White, isLight = true
        )
    }
    val DeepCreamDark by lazy {
        Th(
            bg1 = Color(0xFF0D0C0A), bg2 = Color(0xFF1A1714), bg3 = Color(0xFF22201C), bg4 = Color(0xFF1A1714), bg5 = Color(0xFF0D0C0A),
            p = Color(0xFFD97757), pe = Color(0xFFE8A09A), pd = Color(0xFFC56A4A),
            pc = Color(0xFF3D1A17), opc = Color(0xFFFAF0E8),
            ok = Color(0xFF5DB872),
            ob = Color(0xFFFAF9F5), osv = Color(0xFFA09D96),
            gt = Color(0x08FFFFFF), gb = Color(0x12FFFFFF), gdp = Color(0x04FFFFFF),
            ht = Color(0x0AFFFFFF), hdp = Color(0x06FFFFFF),
            ac = Color(0xFFD4A44B), ua = Color(0xFFD97757), ual = Color(0xFFE8A09A),
            to = Color(0xFF2A2825), err = Color(0xFFC64545), w = Color.White, isLight = false
        )
    }

    // ═══════════════════════════════════════════════
    //  柔桃珊瑚 (Soft Coral) — 真实 Anthropic 品牌色
    //  主色 #EAB8A6（网页抓取原色）+ 次色 #EA9085，更轻盈
    //  ═══════════════════════════════════════════════
    val SoftCoralLight by lazy {
        Th(
            bg1 = Color(0xFFFBF7F2), bg2 = Color(0xFFF6EFE6), bg3 = Color(0xFFF0E7DA), bg4 = Color(0xFFF6EFE6), bg5 = Color(0xFFFBF7F2),
            p = Color(0xFFEAB8A6), pe = Color(0xFFF2CFC2), pd = Color(0xFFD9A08C),
            pc = Color(0xFFFDF0EB), opc = Color(0xFF4A2015),
            ok = Color(0xFF5DB872),
            ob = Color(0xFF141413), osv = Color(0xFF6C6A64),
            gt = Color(0x0DFFFFFF), gb = Color(0xF0FFFFFF), gdp = Color(0x08FFFFFF),
            ht = Color(0xC0FFF5F0), hdp = Color(0x8DFFEBE0),
            ac = Color(0xFFEA9085), ua = Color(0xFFEAB8A6), ual = Color(0xFFF2CFC2),
            to = Color(0xFFE6DFD8), err = Color(0xFFC64545), w = Color.White, isLight = true
        )
    }
    val SoftCoralDark by lazy {
        Th(
            bg1 = Color(0xFF0F0D0B), bg2 = Color(0xFF1A1512), bg3 = Color(0xFF241C18), bg4 = Color(0xFF1A1512), bg5 = Color(0xFF0F0D0B),
            p = Color(0xFFF0C4B8), pe = Color(0xFFF8DDD5), pd = Color(0xFFEAB8A6),
            pc = Color(0xFF3D1A12), opc = Color(0xFFFDF0EB),
            ok = Color(0xFF5DB872),
            ob = Color(0xFFFAF9F5), osv = Color(0xFFA09D96),
            gt = Color(0x08FFFFFF), gb = Color(0x12FFFFFF), gdp = Color(0x04FFFFFF),
            ht = Color(0x0AFFFFFF), hdp = Color(0x06FFFFFF),
            ac = Color(0xFFEA9085), ua = Color(0xFFF0C4B8), ual = Color(0xFFF8DDD5),
            to = Color(0xFF2A2825), err = Color(0xFFC64545), w = Color.White, isLight = false
        )
    }
}

/**
 * 可序列化的颜色定义 — 用于自定义主题 JSON 导入/导出
 * 所有颜色存储为 0xAARRGGBB 格式的 Long 值
 */
data class SerializableThemeColors(
    val bg1: Long, val bg2: Long, val bg3: Long, val bg4: Long, val bg5: Long,
    val primary: Long, val primaryEnd: Long, val primaryDim: Long,
    val primaryContainer: Long, val onPrimaryContainer: Long,
    val success: Long,
    val onBg: Long, val onBgVariant: Long,
    val glassTop: Long, val glassBorder: Long, val glassDarkPrimary: Long,
    val headerTop: Long, val headerDarkPrimary: Long,
    val accent: Long,
    val uiAccent: Long, val uiAccentLight: Long,
    val trackOff: Long, val error: Long, val white: Long,
) {
    fun toTh(isLight: Boolean) = Th(
        bg1 = Color(bg1.toULong()), bg2 = Color(bg2.toULong()), bg3 = Color(bg3.toULong()),
        bg4 = Color(bg4.toULong()), bg5 = Color(bg5.toULong()),
        p = Color(primary.toULong()), pe = Color(primaryEnd.toULong()), pd = Color(primaryDim.toULong()),
        pc = Color(primaryContainer.toULong()), opc = Color(onPrimaryContainer.toULong()),
        ok = Color(success.toULong()),
        ob = Color(onBg.toULong()), osv = Color(onBgVariant.toULong()),
        gt = Color(glassTop.toULong()), gb = Color(glassBorder.toULong()), gdp = Color(glassDarkPrimary.toULong()),
        ht = Color(headerTop.toULong()), hdp = Color(headerDarkPrimary.toULong()),
        ac = Color(accent.toULong()),
        ua = Color(uiAccent.toULong()), ual = Color(uiAccentLight.toULong()),
        to = Color(trackOff.toULong()), err = Color(error.toULong()), w = Color(white.toULong()),
        isLight = isLight
    )

    companion object {
        fun fromTh(th: Th) = SerializableThemeColors(
            bg1 = th.bg1.value.toLong(), bg2 = th.bg2.value.toLong(), bg3 = th.bg3.value.toLong(),
            bg4 = th.bg4.value.toLong(), bg5 = th.bg5.value.toLong(),
            primary = th.p.value.toLong(), primaryEnd = th.pe.value.toLong(), primaryDim = th.pd.value.toLong(),
            primaryContainer = th.pc.value.toLong(), onPrimaryContainer = th.opc.value.toLong(),
            success = th.ok.value.toLong(),
            onBg = th.ob.value.toLong(), onBgVariant = th.osv.value.toLong(),
            glassTop = th.gt.value.toLong(), glassBorder = th.gb.value.toLong(), glassDarkPrimary = th.gdp.value.toLong(),
            headerTop = th.ht.value.toLong(), headerDarkPrimary = th.hdp.value.toLong(),
            accent = th.ac.value.toLong(),
            uiAccent = th.ua.value.toLong(), uiAccentLight = th.ual.value.toLong(),
            trackOff = th.to.value.toLong(), error = th.err.value.toLong(), white = th.w.value.toLong(),
        )
    }
}

/**
 * 自定义主题定义 — 用于 JSON 导入/导出
 */
data class CustomThemeDefinition(
    val id: String,
    val name: String,
    val author: String = "",
    val description: String = "",
    val light: SerializableThemeColors,
    val dark: SerializableThemeColors,
) {
    fun toThPair(): Pair<Th, Th> = light.toTh(true) to dark.toTh(false)
}
