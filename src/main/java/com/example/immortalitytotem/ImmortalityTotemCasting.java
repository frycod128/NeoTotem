package com.example.immortalitytotem;

import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 永生图腾长按引导的服务端核心逻辑。
 *
 * <p>使用弓/弩的物品使用生命周期：玩家按住右键并保持主手图腾 60 tick 后，
 * 由 {@link ImmortalityTotemItem#finishUsingItem} 调用本类的完成方法；
 * 松开右键或切换物品时则由其停止回调清理效果。
 */
public final class ImmortalityTotemCasting {
    /** 引导时长：60 tick 等于 3 秒。 */
    public static final int DURATION_TICKS = 60;

    /** 紫水晶叮铃声的重复间隔：10 tick 等于半秒一次，形成持续的叮铃声覆盖。 */
    public static final int CHIME_INTERVAL_TICKS = 10;

    /** 满额击退抗性使用的临时属性修饰符 ID。 */
    private static final Identifier KNOCKBACK_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "casting_knockback_resistance");

    /** 降低 20% 移动速度使用的临时属性修饰符 ID。 */
    private static final Identifier SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "casting_speed");

    // 工具类：禁止实例化
    private ImmortalityTotemCasting() {
    }

    /** 引导开始时应用临时属性；只在服务端调用。 */
    public static void start(ServerPlayer player) {
        addModifiers(player);
    }

    /** 每次物品使用 tick 调用；只在服务端按固定间隔播放叮铃声。 */
    public static void tick(ServerPlayer player, int ticksRemaining) {
        int ticksElapsed = DURATION_TICKS - ticksRemaining;
        if (ticksRemaining > 0 && ticksElapsed % CHIME_INTERVAL_TICKS == 0) {
            playChime(player);
        }
    }

    /** 松开右键、切换物品或断开连接时清理引导效果。 */
    public static void cancel(Player player) {
        if (!player.level().isClientSide()) {
            removeModifiers(player);
        }
    }

    /** 完整长按 60 tick 后完成引导：原地 flash，再传送到当前出生点。 */
    public static void finish(ServerPlayer player) {
        removeModifiers(player);

        Vec3 origin = player.position();
        ServerLevel level = player.level();

        // 传送前在玩家脚下位置生成一次 flash 粒子；颜色为紫色，只作视觉标记
        level.sendParticles(
                ColorParticleOption.create(ParticleTypes.FLASH, 0.55F, 0.15F, 0.95F),
                origin.x,
                origin.y + 0.5D,
                origin.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );

        // 使用当前玩家出生点；不会消耗重生锚，出生点缺失时回退到世界出生点
        TeleportTransition transition = player.findRespawnPositionAndUseSpawnBlock(
                false, TeleportTransition.DO_NOTHING
        );
        if (transition.missingRespawnBlock()) {
            transition = TeleportTransition.createDefault(player, TeleportTransition.DO_NOTHING);
        }
        player.teleport(transition);
    }

    /** 给引导中的玩家添加满额击退抗性和 -20% 移动速度。 */
    private static void addModifiers(Player player) {
        AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.addOrUpdateTransientModifier(new AttributeModifier(
                    KNOCKBACK_MODIFIER_ID,
                    1.0D,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.addOrUpdateTransientModifier(new AttributeModifier(
                    SPEED_MODIFIER_ID,
                    -0.2D,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
            ));
        }
    }

    /** 移除引导期间添加的临时属性修饰符。重复调用安全。 */
    private static void removeModifiers(Player player) {
        AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.removeModifier(KNOCKBACK_MODIFIER_ID);
        }

        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SPEED_MODIFIER_ID);
        }
    }

    /** 在玩家当前位置播放紫水晶叮铃声，供附近所有客户端收听。 */
    private static void playChime(ServerPlayer player) {
        player.level().playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS,
                1.0F,
                1.0F
        );
    }
}
