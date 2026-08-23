package com.example.examplemod;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 模组的 COMMON 配置定义与运行期缓存。
 * <p>
 * 配置不是所有模组的必需组成，但集中管理可以避免配置键、默认值和校验规则散落在业务代码中。
 * COMMON 配置会在物理客户端和物理服务端各自从 {@code config} 目录加载，并且不会自动同步；
 * 会影响服务器权威游戏规则的值应改用 SERVER 配置或自行发送网络数据。
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    /** 是否在初始化时输出原版泥土方块的注册名。 */
    private static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("是否在通用初始化阶段将泥土方块的注册名写入日志")
            .translation("examplemod.configuration.logDirtBlock")
            .define("logDirtBlock", true);

    /**
     * 带范围约束的整数。配置文件中的非法值会被规范化，业务代码无需再次处理负数或溢出值。
     */
    private static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("示例魔法数字；允许范围为 0 到 Java int 最大值")
            .translation("examplemod.configuration.magicNumber")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    /** 普通字符串配置；这里用作日志前缀。 */
    private static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("输出魔法数字之前显示的文本")
            .translation("examplemod.configuration.magicNumberIntroduction")
            .define("magicNumberIntroduction", "魔法数字是…… ");

    /**
     * 配置文件保存的是字符串列表，加载后再解析成 Item 集合。
     * Supplier 提供配置界面新增行时的默认文本，最后一个参数负责逐项校验。
     */
    private static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("通用初始化阶段要写入日志的物品注册名列表，例如 minecraft:iron_ingot")
            .translation("examplemod.configuration.items")
            .defineListAllowEmpty(
                    "items",
                    List.of("minecraft:iron_ingot"),
                    () -> "minecraft:iron_ingot",
                    Config::validateItemName
            );

    /** build() 会封闭 Builder；所有配置项都必须在本行之前定义。 */
    static final ModConfigSpec SPEC = BUILDER.build();

    /*
     * 下面是业务代码读取的运行期快照。不要让业务代码到处直接调用 ConfigValue#get()：
     * 集中转换可以保证重载时状态一致，也适合把字符串 ID 预解析成注册表对象。
     */
    private static volatile boolean logDirtBlock = true;
    private static volatile int magicNumber = 42;
    private static volatile String magicNumberIntroduction = "魔法数字是…… ";
    private static volatile Set<Item> itemsToLog = Set.of();

    private Config() {
        // 工具类不应被实例化。
    }

    /**
     * 在模组入口统一调用：先让容器管理配置文件，再监听加载与热重载事件。
     */
    static void register(IEventBus modEventBus, ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.COMMON, SPEC);
        modEventBus.addListener(Config::onConfigLoading);
        modEventBus.addListener(Config::onConfigReloading);
    }

    /**
     * 校验列表元素是否为“语法正确且当前物品注册表中真实存在”的 ID。
     * 此回调接收 Object 是配置 API 的通用约定，必须先做类型判断。
     */
    private static boolean validateItemName(Object value) {
        if (!(value instanceof String itemName)) {
            return false;
        }

        try {
            return BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
        } catch (RuntimeException ignored) {
            // Identifier.parse 对非法命名空间/路径会抛异常；校验器应返回 false，而不是中断配置加载。
            return false;
        }
    }

    /** 首次从磁盘读入配置后建立运行期快照。 */
    private static void onConfigLoading(ModConfigEvent.Loading event) {
        refreshRuntimeValues(event);
    }

    /** 玩家在配置界面保存或文件被热重载后，重新建立运行期快照。 */
    private static void onConfigReloading(ModConfigEvent.Reloading event) {
        refreshRuntimeValues(event);
    }

    /**
     * 配置事件会对本模组注册的每份配置触发，所以先比较 Spec，避免未来增加 CLIENT/SERVER
     * 配置后误用另一份事件。之后一次性构造不可变集合，再替换 volatile 引用。
     */
    private static void refreshRuntimeValues(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }

        logDirtBlock = LOG_DIRT_BLOCK.getAsBoolean();
        magicNumber = MAGIC_NUMBER.getAsInt();
        magicNumberIntroduction = MAGIC_NUMBER_INTRODUCTION.get();
        itemsToLog = ITEM_STRINGS.get().stream()
                .map(Identifier::parse)
                .map(BuiltInRegistries.ITEM::getValue)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean shouldLogDirtBlock() {
        return logDirtBlock;
    }

    public static int magicNumber() {
        return magicNumber;
    }

    public static String magicNumberIntroduction() {
        return magicNumberIntroduction;
    }

    /** 返回不可变快照，防止调用方意外改写全局配置状态。 */
    public static Set<Item> itemsToLog() {
        return itemsToLog;
    }
}
