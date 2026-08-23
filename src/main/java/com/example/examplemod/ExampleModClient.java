package com.example.examplemod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 物理客户端专用入口。
 * <p>
 * {@code dist = Dist.CLIENT} 保证专用服务器不会加载本类，因此这里可以安全引用
 * {@code Minecraft}、界面和渲染等客户端类。注意：物理客户端中仍同时存在逻辑客户端
 * 与逻辑服务器（单人游戏）；涉及世界数据时仍应使用 {@code Level#isClientSide()} 判断逻辑侧。
 */
@Mod(value = ExampleMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ExampleModClient {
    /**
     * 注册 NeoForge 自带的配置界面工厂。
     * 玩家可从“模组列表 -> Example Mod -> 配置”打开界面；每个配置项应在语言文件中
     * 提供翻译，否则界面会直接显示语言键。
     */
    public ExampleModClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    /**
     * 客户端生命周期初始化事件。{@link EventBusSubscriber} 会按事件类型把此静态方法
     * 自动订阅到正确的模组事件总线，无需再在构造器中手动 addListener。
     */
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        /*
         * FMLClientSetupEvent 也可能并行派发。Minecraft 单例及多数客户端状态应在
         * 客户端主线程访问，因此通过 enqueueWork 安排，而不是直接在回调线程中操作。
         */
        event.enqueueWork(() -> {
            ExampleMod.LOGGER.info("Example Mod 客户端初始化完成");
            ExampleMod.LOGGER.info("当前玩家名称 >> {}", Minecraft.getInstance().getUser().getName());
        });
    }
}
