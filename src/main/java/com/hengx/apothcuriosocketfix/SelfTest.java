package com.hengx.apothcuriosocketfix;

import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.logging.LogUtils;

import dev.shadowsoffire.apotheosis.adventure.socket.SocketHelper;
import dev.shadowsoffire.apotheosis.adventure.socket.gem.GemInstance;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.slf4j.Logger;

import top.theillusivec4.curios.api.event.CurioAttributeModifierEvent;

/**
 * 只在 {@code -Dapothcuriosocketfix.selftest=true} 时注册的离线自检(正式游戏不参与)。
 *
 * <p>验证的是"什么都不该做的时候真的什么都不做":
 * 空物品 / 没有镶槽的物品 / 有镶槽但槽里没有宝石 —— 这三种情况都必须
 * ① 不抛异常,② 不给 {@code CurioAttributeModifierEvent} 添加任何修饰符。</p>
 *
 * <p>为什么不需要真实玩家:宝石修饰符只看物品 NBT;{@link CurioAttributeModifierEvent}
 * 的 {@code SlotContext} 在本模组里完全没用到,可以传 {@code null}。</p>
 */
final class SelfTest {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG = "[宝石修复自检]";

    private SelfTest() {}

    /** 由 {@link ApothCurioSocketFix} 在检测到开关时调用。 */
    static void register() {
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(SelfTest.class);
        LOGGER.info("{} 已注册", TAG);
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        int passed = 0;
        int failed = 0;

        // T1 空物品:不构造 SocketedGems、不加修饰符
        boolean t1 = leavesModifiersUntouched(ItemStack.EMPTY);
        passed += t1 ? 1 : 0;
        failed += t1 ? 0 : 1;
        log("T1 空物品 -> " + (t1 ? "PASS" : "FAIL"));

        // T2 没有镶槽的普通物品
        boolean t2 = leavesModifiersUntouched(new ItemStack(Items.STONE));
        passed += t2 ? 1 : 0;
        failed += t2 ? 0 : 1;
        log("T2 无镶槽石头 -> " + (t2 ? "PASS" : "FAIL"));

        // T3 有镶槽但槽里没有宝石:SocketHelper.getGems 返回空列表(它只返回"真的镶了宝石"的实例),
        //    所以处理器应当在 isEmpty() 处直接返回,不构造任何 GemInstance
        ItemStack socketed = new ItemStack(Items.STONE);
        SocketHelper.setSockets(socketed, 2);
        int sockets = SocketHelper.getSockets(socketed);
        int gemCount = 0;
        boolean allInvalid = true;
        for (GemInstance gem : SocketHelper.getGems(socketed)) {
            gemCount++;
            allInvalid &= !gem.isValid();
        }
        boolean t3 = sockets == 2 && gemCount == 0 && allInvalid && leavesModifiersUntouched(socketed);
        passed += t3 ? 1 : 0;
        failed += t3 ? 0 : 1;
        log("T3 两个空镶槽(sockets=" + sockets + ", gemInstances=" + gemCount + ", 全部无效=" + allInvalid
            + ") -> " + (t3 ? "PASS" : "FAIL"));

        log("RESULT PASSED=" + passed + " FAILED=" + failed);
    }

    /** 跑一遍本模组的事件处理器,断言它没有往事件里加任何修饰符。 */
    private static boolean leavesModifiersUntouched(ItemStack stack) {
        try {
            CurioAttributeModifierEvent event = new CurioAttributeModifierEvent(
                stack, null, UUID.randomUUID(), ImmutableMultimap.of());
            CurioGemAttributes.onCurioAttributeModifiers(event);
            return event.getModifiers().isEmpty();
        }
        catch (Throwable t) {
            log("异常:" + t);
            return false;
        }
    }

    private static void log(String message) {
        LOGGER.info("{} {}", TAG, message);
    }
}
