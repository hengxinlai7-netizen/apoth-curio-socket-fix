package com.hengx.apothcuriosocketfix;

import com.mojang.logging.LogUtils;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;

/**
 * 神化饰品宝石修复 —— 让神化(Apotheosis)镶在 Curios 饰品栏物品上的宝石属性生效。
 *
 * <h2>修的问题</h2>
 * Apotheosis 7.4.8 只在原版 {@code ItemAttributeModifierEvent} 里把宝石修饰符加进物品:
 * {@code AdventureEvents#affixModifiers} 调用
 * {@code SocketHelper.getGems(stack).addModifiers(LootCategory.forItem(stack), e.getSlotType(), ...)},
 * 而 {@code GemInstance#addModifiers(EquipmentSlot, ...)} 内部有一段
 * <b>槽位过滤</b>:
 * <pre>
 * for (EquipmentSlot itemSlot : this.cat.getSlots()) {
 *     if (itemSlot != slot) continue;      // 只有宝石分类的槽位与查询槽位一致才加
 *     ...addModifiers...
 * }
 * </pre>
 * 戒指 / 项链 / 护符这类 Curios 物品的 {@code LootCategory} 是 {@code NONE}
 * (它的 {@code getSlots()} 是空数组),所以这段循环一次都不会命中 ——
 * 宝石镶上去了、{@code isValid()} 也是 true,但属性一条都加不上。
 *
 * <h2>怎么修</h2>
 * Curios 提供了自己的 {@code CurioAttributeModifierEvent}:饰品栏物品计算属性修饰符时触发。
 * 本模组监听它,对每颗宝石<b>跳过那段槽位过滤</b>,直接调用
 * {@code Gem#getBonus(cat, rarity).addModifiers(gemStack, rarity, consumer)},
 * 也就是 Apotheosis 过滤之后真正干活的那一步。判定逻辑(cat / rarity / isValid)全部沿用神化自己的。
 *
 * <h2>依赖</h2>
 * {@code curios} 与 {@code apotheosis} 都是<b>软依赖</b>(mods.toml 里 {@code mandatory=false}),
 * 二者任一缺失时本模组只注册一个"什么都没做"的空壳、连相关类都不会加载。
 */
@Mod(ApothCurioSocketFix.MODID)
public final class ApothCurioSocketFix {

    /** 模组 id(与 mods.toml / gradle.properties 一致)。 */
    public static final String MODID = "apothcuriosocketfix";

    /** 日志(只在启用/未启用时装一行,正常游玩零输出)。 */
    private static final Logger LOGGER = LogUtils.getLogger();

    /** 软依赖的 modid。 */
    private static final String APOTHEOSIS = "apotheosis";
    private static final String CURIOS = "curios";

    public ApothCurioSocketFix() {
        // 只有两个前置都在才注册事件处理器:否则连 CurioGemAttributes 这个类都不加载
        // (它直接引用神化/Curios 的类型,提前加载会 NoClassDefFoundError)。
        if (ModList.get().isLoaded(APOTHEOSIS) && ModList.get().isLoaded(CURIOS)) {
            MinecraftForge.EVENT_BUS.register(CurioGemAttributes.class);
            LOGGER.info("[神化饰品宝石修复] 已启用:Curios 饰品栏物品上的神化宝石属性将正常生效");
            // 仅诊断用:加 -Dapothcuriosocketfix.selftest=true 才注册,正式游戏不参与
            if (Boolean.getBoolean("apothcuriosocketfix.selftest")) {
                SelfTest.register();
            }
        }
        else {
            LOGGER.info("[神化饰品宝石修复] 未启用:需要同时安装 apotheosis 与 curios");
        }
    }
}
