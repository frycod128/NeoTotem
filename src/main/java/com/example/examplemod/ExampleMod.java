package com.example.examplemod;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 模组的公共入口。
 * <p>
 * {@link Mod} 的值必须与 {@code neoforge.mods.toml} 中的 {@code modId} 完全一致。
 * 此类会在物理客户端和物理服务端都加载，所以不能在这里引用
 * {@code net.minecraft.client.*} 下的客户端专用类；客户端逻辑放在
 * {@link ExampleModClient} 中。
 */
@Mod(ExampleMod.MODID)
public class ExampleMod {
    /**
     * 模组 ID 同时用作注册表命名空间、资源包命名空间和数据包命名空间。
     * 正式开发时应尽早替换，并同步修改包路径、资源目录和 gradle.properties。
     */
    public static final String MODID = "examplemod";

    /** SLF4J 日志器；相比 System.out，它会遵循游戏的日志等级和输出格式。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /*
     * DeferredRegister（延迟注册器）只负责“声明稍后要注册什么”。
     * 真正的注册发生在 NeoForge 派发注册事件时，因此还必须在构造器中调用
     * register(modEventBus)。延迟注册可以避免过早访问尚未就绪的原版注册表。
     */
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /**
     * 声明注册名为 {@code examplemod:example_block} 的方块。
     * {@link DeferredBlock} 是一个延迟 Holder：静态初始化阶段可以安全保存它，
     * 但只有注册完成后才能通过 {@code get()} 取得真正的 {@link Block} 单例。
     */
    public static final DeferredBlock<Block> EXAMPLE_BLOCK = BLOCKS.registerSimpleBlock(
            "example_block",
            properties -> properties.mapColor(MapColor.STONE)
    );

    /**
     * 方块和物品属于两个独立注册表。若希望玩家能在背包中持有/放置方块，
     * 还必须为方块注册同名的 {@link BlockItem}。
     */
    public static final DeferredItem<BlockItem> EXAMPLE_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("example_block", EXAMPLE_BLOCK);

    /**
     * 声明注册名为 {@code examplemod:example_item} 的普通食物物品。
     * nutrition 是恢复的饥饿值，saturationModifier 是饱和度系数；
     * alwaysEdible 表示饥饿值已满时仍允许食用。
     */
    public static final DeferredItem<Item> EXAMPLE_ITEM = ITEMS.registerSimpleItem(
            "example_item",
            properties -> properties.food(new FoodProperties.Builder()
                    .alwaysEdible()
                    .nutrition(1)
                    .saturationModifier(2.0F)
                    .build())
    );

    /**
     * 自定义创造模式标签页，注册名为 {@code examplemod:example_tab}。
     * 标题使用语言键而不是硬编码文本，因此会随客户端语言切换。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB =
            CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.examplemod"))
                    // 把本标签页排在原版“战斗”标签页之前。
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    // 图标 Supplier 会在注册完成后才求值，此时调用 get() 是安全的。
                    .icon(() -> EXAMPLE_ITEM.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // 自有标签页优先在这里填充；addCreative 适合向已有标签页追加内容。
                        output.accept(EXAMPLE_ITEM.get());
                        output.accept(EXAMPLE_BLOCK_ITEM.get());
                    })
                    .build());

    /**
     * NeoForge 创建模组时会调用此构造器，并自动注入本模组专属事件总线与容器。
     * <p>
     * 这里应只做“注册监听器/注册器/配置”等装配工作，不要在构造期间查询
     * 原版注册表或访问世界；此时注册流程和游戏世界都尚未准备完成。
     */
    public ExampleMod(IEventBus modEventBus, ModContainer modContainer) {
        // FMLCommonSetupEvent 属于模组生命周期事件，因此监听在模组事件总线上。
        modEventBus.addListener(this::commonSetup);

        // 顺序体现依赖关系：先声明方块，再声明引用该方块的 BlockItem，最后注册标签页。
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        /*
         * NeoForge.EVENT_BUS 是运行期“游戏事件总线”，例如服务器启动、实体交互等。
         * 只有本类确实含有 @SubscribeEvent 实例方法时才需要 register(this)。
         */
        NeoForge.EVENT_BUS.register(this);

        // BuildCreativeModeTabContentsEvent 是模组总线事件，用来修改原版/其他模组的标签页内容。
        modEventBus.addListener(this::addCreative);

        // 集中注册配置及其加载/重载监听器；配置文件默认为 config/examplemod-common.toml。
        Config.register(modEventBus, modContainer);
    }

    /**
     * 通用初始化回调，在注册阶段结束且 COMMON 配置加载后触发。
     * 生命周期事件可能并行派发；需要操作主线程限定对象时，应使用 enqueueWork。
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Example Mod 通用初始化开始");

        // 这里特意排入主线程，演示生命周期事件中执行主线程工作的标准写法。
        event.enqueueWork(this::logConfiguredValues);
    }

    /**
     * 读取已由 Config 的加载事件整理好的运行期值。
     * 把实际逻辑拆成独立方法，既能清楚表达线程边界，也便于以后编写单元测试。
     */
    private void logConfiguredValues() {
        if (Config.shouldLogDirtBlock()) {
            // 此时注册已经完成，查询 BuiltInRegistries 是安全的。
            LOGGER.info("泥土方块注册名 >> {}", BuiltInRegistries.BLOCK.getKey(Blocks.DIRT));
        }

        LOGGER.info("{}{}", Config.magicNumberIntroduction(), Config.magicNumber());
        Config.itemsToLog().forEach(item ->
                LOGGER.info("配置中的物品 >> {}", BuiltInRegistries.ITEM.getKey(item)));
    }

    /** 向原版“建筑方块”标签页追加示例方块物品。 */
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        // 注册表键是单例，可直接用 == 比较；只处理目标标签页，避免每个标签页都重复添加。
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(EXAMPLE_BLOCK_ITEM);
        }
    }

    /**
     * 服务器即将开始接受游戏逻辑前触发的运行期事件。
     * 单人游戏也拥有逻辑服务器，所以该事件在单人世界中同样会发生。
     */
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Example Mod 已接收到服务器启动事件：{}", event.getServer());
    }
}
