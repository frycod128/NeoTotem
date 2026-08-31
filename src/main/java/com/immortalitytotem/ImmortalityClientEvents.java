package com.immortalitytotem;

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
    // 工具类：禁止实例化
    private ImmortalityClientEvents() {
    }

    /** 任何屏幕将要打开时最先触发；在死亡屏打开前先检查玩家状态。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // 取客户端单例（主线程内有效）
        Minecraft minecraft = Minecraft.getInstance();
        // 本地玩家（单人世界或多人连接的客户端视角）
        LocalPlayer player = minecraft.player;
        // 即将打开的屏幕
        Screen screen = event.getNewScreen();
        // 玩家存在、打开的是死亡屏（含其二次确认屏）、且玩家实际受保护
        if (player != null
                && (screen instanceof DeathScreen || screen instanceof DeathScreen.TitleConfirmScreen)
                && ImmortalityProtection.stabilize(player)) {
            // 阻止死亡屏打开，直接把这次"打开"取消
            event.setCanceled(true);
        }
    }

    /** tick 末尾再兜底一次：覆盖那些没走 Opening 事件的路径。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        // 当前显示中的屏幕
        Screen screen = minecraft.gui.screen();
        // 同上条件：玩家受保护且死亡屏正显示着
        if (player != null
                && (screen instanceof DeathScreen || screen instanceof DeathScreen.TitleConfirmScreen)
                && ImmortalityProtection.stabilize(player)) {
            // 直接关掉当前死亡屏，回到游戏画面
            minecraft.gui.setScreen(null);
        }
    }
}
