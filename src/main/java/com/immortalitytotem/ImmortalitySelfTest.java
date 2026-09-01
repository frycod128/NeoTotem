package com.immortalitytotem;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntitySpawnRequest;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.NotNull;

/** 仅由开发运行系统属性启用的轻量运行期回归测试。 */
final class ImmortalitySelfTest {
    // 工具类：禁止实例化
    private ImmortalitySelfTest() {
    }

    /** 在专用服务器上跑完整回归测试；任何 require 失败都会抛异常中止启动。 */
    static void run(MinecraftServer server) {
        // 取主世界作为测试场地
        ServerLevel level = server.overworld();
        // 创建不参与真实玩家数据的模拟玩家
        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        // 生成一个图腾物品实例
        ItemStack totem = ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack();
        // 把图腾放到模拟玩家物品栏第 0 格
        player.getInventory().setItem(0, totem);

        // 断言：物品栏扫描必须能找到图腾（验证 hasTotem 判定路径）
        require(ImmortalityProtection.hasTotem(player), "inventory scan did not find the totem");
        // 断言：物品上确实挂着原版死亡保护组件（验证注册属性生效）
        require(
                DeathProtection.TOTEM_OF_UNDYING.equals(totem.get(DataComponents.DEATH_PROTECTION)),
                "vanilla death protection component is missing"
        );
        // 构造 9 图腾合成配方的注册键
        ResourceKey<@NotNull Recipe<?>> recipeKey = ResourceKey.create(
                Registries.RECIPE,
                Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "immortality_totem")
        );
        // 断言：配方已随数据包加载进服务端配方表
        require(level.recipeAccess().byKey(recipeKey).isPresent(), "nine-totem recipe was not loaded");

        // —— 物品栏外槽位白名单：游标槽 / 合成格 / 末影箱中的图腾同样生效 ——
        // 清空物品栏，确保只有待测槽位持有图腾
        player.getInventory().setItem(0, ItemStack.EMPTY);
        // 游标槽（499 号 / "player.cursor"）：创造模式下鼠标所持的物品
        player.containerMenu.setCarried(totem);
        require(ImmortalityProtection.hasTotem(player), "cursor-carried totem was not detected");
        player.containerMenu.setCarried(ItemStack.EMPTY);
        require(!ImmortalityProtection.hasTotem(player), "cursor whitelist did not release after clearing");
        // 合成格（500-503 号 / "player.crafting"）：背包界面的 2x2 合成格
        player.inventoryMenu.getCraftSlots().setItem(0, totem);
        require(ImmortalityProtection.hasTotem(player), "crafting-slot totem was not detected");
        player.inventoryMenu.getCraftSlots().setItem(0, ItemStack.EMPTY);
        require(!ImmortalityProtection.hasTotem(player), "crafting whitelist did not release after clearing");
        // 末影箱（200-226 号 / "enderchest"）
        player.getEnderChestInventory().setItem(0, totem);
        require(ImmortalityProtection.hasTotem(player), "ender-chest totem was not detected");
        player.getEnderChestInventory().setItem(0, ItemStack.EMPTY);
        require(!ImmortalityProtection.hasTotem(player), "ender-chest whitelist did not release after clearing");
        // 恢复物品栏第 0 格的图腾，不破坏后续测试
        player.getInventory().setItem(0, totem);

        // 依次验证五种非法生命输入都会被洗成保底 1 点生命
        assertHealthSanitized(player, 0.0F);
        assertHealthSanitized(player, -20.0F);
        assertHealthSanitized(player, Float.NaN);
        assertHealthSanitized(player, Float.POSITIVE_INFINITY);
        assertHealthSanitized(player, Float.NEGATIVE_INFINITY);

        // 构造"半死亡"状态：倒地姿态 + 生命 0
        player.setPose(Pose.DYING);
        player.setHealth(0.0F);
        // 断言：倒地姿态被修复
        require(player.getPose() != Pose.DYING, "dying pose was not repaired");
        // 断言：逻辑上不再处于"正在死亡或已死"
        require(!player.isDeadOrDying(), "protected player is deadOrDying");
        // 断言：isAlive 判定为存活
        require(player.isAlive(), "protected player is not logically alive");

        // —— 正常伤害路径：应被接受但只扣到剩 1 点血 ——
        // 关闭一切无敌来源，确保测的是伤害逻辑而非无敌逻辑
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        // 标记客户端已完成加载（绕过尚未登录的限制）
        player.connection.markClientLoaded();
        // 清零无敌帧，让这次伤害立刻生效
        player.invulnerableTime = 0;
        // 回满生命，保证 1000 点伤害远超当前上限
        player.setHealth(player.getMaxHealth());
        // 普通伤害源（generic 类型）
        DamageSource normalSource = player.damageSources().generic();
        // 施加 1000 点致命伤害
        boolean normalDamageAccepted = player.hurtServer(level, normalSource, 1000.0F);
        // 断言：伤害被接受（未被取消）
        require(
                normalDamageAccepted,
                // 失败时附带现场状态帮助定位（无敌/能力/登录/血量）
                "normal damage was unexpectedly cancelled"
                        + " [entityInvulnerable=" + player.isInvulnerableTo(level, normalSource)
                        + ", abilityInvulnerable=" + player.getAbilities().invulnerable
                        + ", clientLoaded=" + player.connection.hasClientLoaded()
                        + ", health=" + player.getHealth() + "]"
        );
        // 断言：致命伤害后生命正好停在保底 1 点
        require(player.getHealth() == 1.0F, "normal lethal damage did not stop at one health");
        // 断言：直接 generic_kill（/kill 底层）伤害被拒绝
        require(
                !player.hurtServer(level, player.damageSources().genericKill(), Float.MAX_VALUE),
                "direct generic_kill damage was not rejected"
        );
        // 断言：被拒绝后生命仍保持 1 点
        require(player.getHealth() == 1.0F, "generic_kill changed protected health");

        // —— 非玩家生物：原版不死图腾效果生效，但图腾本身不消耗 ——
        Zombie zombie = EntityTypes.ZOMBIE.create(
                level,
                new EntitySpawnRequest(EntitySpawnReason.MOB_SUMMONED, true)
        );
        require(zombie != null, "zombie test entity was not created");
        zombie.setInvulnerable(false);
        zombie.setHealth(zombie.getMaxHealth());
        zombie.setItemInHand(InteractionHand.MAIN_HAND, ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack());

        boolean zombieDamageAccepted = zombie.hurtServer(level, zombie.damageSources().generic(), 1000.0F);
        require(zombieDamageAccepted, "non-player totem damage was unexpectedly cancelled");
        require(zombie.getHealth() == 1.0F, "non-player totem did not restore health to one");
        require(!zombie.isDeadOrDying(), "non-player totem did not save the entity");
        require(
                // 不死生物按原版规则不吃再生，因此验证同样由普通图腾施加的吸收与抗火。
                zombie.hasEffect(MobEffects.ABSORPTION)
                        && zombie.hasEffect(MobEffects.FIRE_RESISTANCE),
                "non-player totem did not apply vanilla death protection effects"
        );
        ItemStack heldTotem = zombie.getItemInHand(InteractionHand.MAIN_HAND);
        require(
                heldTotem.is(ImmortalityTotemMod.IMMORTALITY_TOTEM.getKey())
                        && heldTotem.getCount() == 1,
                "non-player totem was consumed"
        );
        zombie.discard();

        // —— 直接调用式绕过路径：kill / die / remove ——
        // 新建一个真实的 ServerPlayer 实例做直接调用测试
        ServerPlayer directPlayer = new ServerPlayer(
                server,
                level,
                new GameProfile(UUID.fromString("47ad6a12-cbd8-4f11-b1bd-1650feb48fd0"), "[ImmortalityDirectTest]"),
                ClientInformation.createDefault()
        );
        // 同样给第 0 格放图腾
        directPlayer.getInventory().setItem(0, ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack());
        // 起始 1 点生命
        directPlayer.setHealth(1.0F);

        // 直接调用 kill() 方法（不走伤害管线）
        directPlayer.kill(level);
        // 断言：没有被移除且生命保持 1
        require(!directPlayer.isRemoved() && directPlayer.getHealth() == 1.0F, "direct kill was not blocked");

        // 再试直接调用 die()（跳过受伤判定直接死亡）
        DamageSource source = directPlayer.damageSources().generic();
        directPlayer.die(source);
        // 断言：同样被拦截
        require(!directPlayer.isRemoved() && directPlayer.getHealth() == 1.0F, "direct ServerPlayer.die was not blocked");

        // 直接调用 remove(KILLED)：正常死亡路径的最终移除
        directPlayer.remove(Entity.RemovalReason.KILLED);
        require(!directPlayer.isRemoved(), "remove(KILLED) was not blocked");
        // 再试最低层的 setRemoved(KILLED)
        directPlayer.setRemoved(Entity.RemovalReason.KILLED);
        require(!directPlayer.isRemoved(), "setRemoved(KILLED) was not blocked");

        // —— 死亡事件总线：受保护玩家不得发布 LivingDeathEvent ——
        // 注册一个探针监听器统计事件是否触发
        DeathEventProbe probe = new DeathEventProbe(directPlayer);
        NeoForge.EVENT_BUS.register(probe);
        boolean cancelled;
        try {
            // 调用 NeoForge 死亡钩子（内部会 post LivingDeathEvent）
            cancelled = CommonHooks.onLivingDeath(directPlayer, source);
        } finally {
            // 无论成败都要摘掉探针，避免污染后续测试
            NeoForge.EVENT_BUS.unregister(probe);
        }
        // 断言：钩子被短路直接返回"已取消"
        require(cancelled, "death hook was not short-circuited");
        // 断言：LivingDeathEvent 从未被发布
        require(!probe.fired, "LivingDeathEvent was posted");

        // —— 死亡重生路径：不得创建替换玩家 ——
        require(
                // 用 KILLED 理由触发重生，必须返回原实例而不是新玩家
                server.getPlayerList().respawn(directPlayer, false, Entity.RemovalReason.KILLED) == directPlayer,
                "KILLED respawn created a replacement player"
        );

        // 最后一个图腾离开后，setHealth 必须原样恢复为原版 0 下限。
        // 清空物品栏第 0 格，模拟图腾离身
        player.getInventory().setItem(0, ItemStack.EMPTY);
        // 写入 0 生命
        player.setHealth(0.0F);
        // 断言：原版行为恢复，生命保持 0（不再被洗成 1）
        require(player.getHealth() == 0.0F, "vanilla health behavior did not return after removal");
        // 断言：恢复"死亡/垂死"判定
        require(player.isDeadOrDying(), "vanilla deadOrDying predicate did not return after removal");
        // 断言：恢复"非存活"判定
        require(!player.isAlive(), "vanilla isAlive predicate did not return after removal");
        // 回满血，恢复测试对象状态
        player.setHealth(player.getMaxHealth());

        // 非死亡生命周期移除必须始终放行，否则会破坏维度切换和登出。
        // 新建第二个模拟玩家专门测生命周期移除
        FakePlayer lifecyclePlayer = new FakePlayer(
                level,
                new GameProfile(UUID.fromString("9d7debbc-cfe8-46d7-9935-799c118428c9"), "[ImmortalityLifecycleTest]")
        );
        // 同样带上图腾（受保护）
        lifecyclePlayer.getInventory().setItem(0, ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack());
        // 以切维度理由移除
        lifecyclePlayer.setRemoved(Entity.RemovalReason.CHANGED_DIMENSION);
        // 断言：非 KILLED 移除必须放行
        require(lifecyclePlayer.isRemoved(), "CHANGED_DIMENSION removal was incorrectly blocked");

        // 全部通过：输出成功日志
        ImmortalityTotemMod.LOGGER.info("Immortality Totem runtime self-test passed");
    }

    /** 断言：把指定非法生命值写入后必须被洗成保底 1 点。 */
    private static void assertHealthSanitized(FakePlayer player, float value) {
        // 写入待测的非法生命值（会触发 setHealth Mixin）
        player.setHealth(value);
        // 断言：读回值必须是保底 1 点
        require(player.getHealth() == ImmortalityProtection.MINIMUM_HEALTH,
                "health was not sanitized for input " + value + ": " + player.getHealth());
    }

    /** 断言工具：条件不成立时抛异常终止自检。 */
    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Immortality Totem self-test failed: " + message);
        }
    }

    /** 事件探针：记录目标玩家是否收到过 LivingDeathEvent。 */
    private static final class DeathEventProbe {
        // 要监控的目标玩家
        private final ServerPlayer player;
        // 是否收到过该玩家的事件
        private boolean fired;

        private DeathEventProbe(ServerPlayer player) {
            this.player = player;
        }

        @SubscribeEvent
        public void onDeath(LivingDeathEvent event) {
            // 只统计目标玩家的死亡事件
            if (event.getEntity() == this.player) {
                this.fired = true;
            }
        }
    }
}
