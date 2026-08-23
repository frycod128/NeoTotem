package com.example.immortalitytotem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** 客户端非法/乱序死亡包的视觉兜底；专用服务端不会加载本类。 */
@EventBusSubscriber(modid = ImmortalityTotemMod.MODID, value = Dist.CLIENT)
public final class ImmortalityClientEvents {
    private ImmortalityClientEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Screen screen = event.getNewScreen();
        if (player != null
                && (screen instanceof DeathScreen || screen instanceof DeathScreen.TitleConfirmScreen)
                && ImmortalityProtection.stabilize(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        Screen screen = minecraft.gui.screen();
        if (player != null
                && (screen instanceof DeathScreen || screen instanceof DeathScreen.TitleConfirmScreen)
                && ImmortalityProtection.stabilize(player)) {
            minecraft.gui.setScreen(null);
        }
    }
}
