package com.hengx.apothcuriosocketfix;

import com.google.common.collect.Multimap;

import dev.shadowsoffire.apotheosis.adventure.loot.LootRarity;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.SocketedGems;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.bonus.GemBonus;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * 把 Curios 饰品栏物品里的神化宝石属性接进 Curios 的属性修饰符计算。
 *
 * <p>本类直接引用神化 / Curios 的类型,因此<b>只能</b>在两个前置都加载后由
 * {@link ApothCurioSocketFix} 注册(见那里的 {@code ModList.isLoaded} 守卫)。</p>
 *
 * <h2>与神化原逻辑的差异</h2>
 * 神化自己(@code AdventureEvents#affixModifiers)走的是:
 * <pre>
 * SocketHelper.getGems(stack).addModifiers(cat, slot, consumer);
 *   └─ GemInstance#addModifiers(slot, consumer)
 *        └─ if (cat.getSlots() 里没有 slot) 什么都不加     ← Curios 物品在这里被过滤掉
 * </pre>
 * 这里跳过最外层那个"槽位过滤"循环,直接调用过滤通过后的同一步:
 * <pre>
 * gem.getBonus(cat, rarity).ifPresent(b -&gt; b.addModifiers(gemStack, rarity, consumer));
 * </pre>
 * 除"槽位过滤"外,所有判定(category、rarity、{@code isValid()} 的 bonus 存在性)与神化完全一致,
 * 所以不会给"本来就该被过滤掉"的宝石凭空加属性 —— 只有 Curios 这种"没有原版槽位"的物品受影响。
 */
public final class CurioGemAttributes {

    private CurioGemAttributes() {}

    /**
     * Curios 计算饰品栏物品属性修饰符时触发(装备变化 / 属性刷新时,不是每 tick)。
     *
     * <p>空物品、没有镶槽、镶槽里没有宝石这三种情况都立即返回:
     * 不构造 {@code GemInstance}、不分配临时集合。</p>
     */
    @SubscribeEvent
    public static void onCurioAttributeModifiers(CurioAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        SocketedGems gems = SocketHelper.getGems(stack);
        if (gems.isEmpty()) {
            return;
        }
        for (int i = 0, size = gems.size(); i < size; i++) {
            GemInstance gem = gems.get(i);
            if (gem == null || !gem.isValid()) {
                continue;   // 空槽 / 宝石无效 / 该分类下没有对应 bonus
            }
            LootRarity rarity = gem.rarity().get();
            GemBonus bonus = gem.gem().get().getBonus(gem.cat(), rarity).orElse(null);
            if (bonus == null) {
                continue;
            }
            bonus.addModifiers(gem.gemStack(), rarity, (attribute, modifier) -> addOnce(event, attribute, modifier));
        }
    }

    /**
     * 加一条修饰符,但<b>同一个 UUID 不重复加</b>。
     *
     * <p>宝石修饰符的 UUID 写在宝石的 NBT 里({@code GemItem#getUUIDs}),同一颗宝石被 Curios
     * 重复计算、或物品同时出现在别的槽位时可能出现同 UUID 的修饰符;重复会让属性翻倍,
     * 所以先扫一遍现有修饰符(宝石通常只有 1~3 条,扫描成本可以忽略)。</p>
     */
    private static void addOnce(CurioAttributeModifierEvent event, Attribute attribute, AttributeModifier modifier) {
        if (attribute == null || modifier == null) {
            return;
        }
        Multimap<Attribute, AttributeModifier> existing = event.getModifiers();
        if (existing != null && !existing.isEmpty()) {
            for (AttributeModifier present : existing.values()) {
                if (present.getId().equals(modifier.getId())) {
                    return;   // 同 UUID 已经有了:跳过
                }
            }
        }
        event.addModifier(attribute, modifier);
    }
}
