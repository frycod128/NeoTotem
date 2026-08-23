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
    public static final String MODID = "immortalitytotem";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);

    /**
     * DEATH_PROTECTION 让非玩家生物手持时完全复用原版不死图腾流程。
     * 玩家物品栏中的常驻保护由 ImmortalityProtection 和窄 Mixin 共同实现。
     */
    public static final DeferredItem<Item> IMMORTALITY_TOTEM = ITEMS.registerSimpleItem(
            "immortality_totem",
            properties -> properties
                    .stacksTo(1)
                    .rarity(Rarity.EPIC)
                    .component(DataComponents.DEATH_PROTECTION, DeathProtection.TOTEM_OF_UNDYING)
    );

    public ImmortalityTotemMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(this::addToCreativeTab);
        modEventBus.addListener(RequiredNetworkPayload::register);
        NeoForge.EVENT_BUS.register(new ImmortalityEvents());
    }

    private void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(IMMORTALITY_TOTEM);
        }
    }
}
