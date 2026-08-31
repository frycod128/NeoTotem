package com.example.immortalitytotem;

import com.mojang.logging.LogUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DeathProtection;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

/** 模组入口；只注册永生图腾这一种游戏物品。 */
@Mod(ImmortalityTotemMod.MODID)
public final class ImmortalityTotemMod {
    /** 模组 ID：物品命名空间、网络通道标识、配方与语言文件均以它为前缀。 */
    public static final String MODID = "immortalitytotem";
    /** 全模组共享的 SLF4J 日志器。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /** 物品延迟注册表：真正的注册动作推迟到 NeoForge 的注册事件阶段执行。 */
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    /**
     * DEATH_PROTECTION 让非玩家生物手持时完全复用原版不死图腾流程。
     * 玩家物品栏中的常驻保护由 ImmortalityProtection 和窄 Mixin 共同实现。
     */
    public static final DeferredItem<Item> IMMORTALITY_TOTEM = ITEMS.registerSimpleItem(
            // 注册名，最终物品 ID 为 immortalitytotem:immortality_totem
            "immortality_totem",
            // 物品属性构造器：链式配置以下三项
            properties -> properties
                    // 不可堆叠，一格只能放一个
                    .stacksTo(1)
                    // 史诗（紫色）品质，与下界之星同级
                    .rarity(Rarity.EPIC)
                    // 挂上原版"死亡保护"组件：非玩家手持时会走原版不死图腾流程
                    .component(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING)
    );

    /** 模组构造器由 FML 在加载阶段调用，此处完成所有事件的静态挂载。 */
    public ImmortalityTotemMod(IEventBus modEventBus) {
        // 把延迟注册表挂到 mod 事件总线，物品才真正写入注册表
        ITEMS.register(modEventBus);
        // 注册"向创造模式标签页填充内容"的回调
        modEventBus.addListener(this::addToCreativeTab);
        // 注册必选网络通道（参与登录协商，见 RequiredNetworkPayload）
        modEventBus.addListener(RequiredNetworkPayload::register);
        // 把游戏内事件处理器挂到 NeoForge 游戏事件总线（伤害、tick、服务器启动等）
        NeoForge.EVENT_BUS.register(new ImmortalityEvents());
    }

    /** 物品进创造模式标签页的回调：只放进"战斗"标签。 */
    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        // 判断当前正在填充的标签是否为战斗标签
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            // 把永生图腾加入该标签的默认内容
            event.accept(IMMORTALITY_TOTEM);
        }
    }
}
