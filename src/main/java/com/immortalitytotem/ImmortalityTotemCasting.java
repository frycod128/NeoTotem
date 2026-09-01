package com.immortalitytotem;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/**
 * 永生图腾长按引导的服务端核心逻辑。
 *
 * <p>使用弓/弩的物品使用生命周期：玩家按住右键并保持主手图腾 60 tick 后，
 * 由 {@link ImmortalityTotemItem#finishUsingItem} 调用本类的完成方法；
 * 松开右键或切换物品时则由其停止回调清理效果。开始结算 Exhaustion 与虚弱 V，
 * 结束（无论是否完成）叠加失明 I。
 */
public final class ImmortalityTotemCasting {
    /** 引导时长：60 tick 等于 3 秒。 */
    public static final int DURATION_TICKS = 60;

    /** 紫水晶叮铃声的重复间隔：10 tick 等于半秒一次，形成持续的叮铃声覆盖。 */
    public static final int CHIME_INTERVAL_TICKS = 10;

    /** 每次引导开始时直接加入的消耗度。 */
    public static final float START_EXHAUSTION = 20.0F;

    /** 虚弱 V 的 amplifier：罗马数字 V 对应 amplifier 4。 */
    public static final int WEAKNESS_AMPLIFIER = 4;

    /** 失明 I 的 amplifier：罗马数字 I 对应 amplifier 0。 */
    public static final int BLINDNESS_AMPLIFIER = 0;

    /** 满额击退抗性使用的临时属性修饰符 ID。 */
    private static final Identifier KNOCKBACK_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "casting_knockback_resistance");

    /** 降低 20% 移动速度使用的临时属性修饰符 ID。 */
    private static final Identifier SPEED_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "casting_speed");

    /** 正在引导的玩家 UUID；用于区分一次引导的开始/结束，避免完成和中断重复施加失明。 */
    private static final Map<UUID, CastState> ACTIVE_CASTS = new HashMap<>();

    // 工具类：禁止实例化
    private ImmortalityTotemCasting() {
    }

    /**
     * 引导开始时应用立即生效的副作用：
     * 增加 20 点消耗度、施加/刷新 6 秒虚弱 V，并添加引导期间的临时属性。只在服务端调用。
     */
    public static void start(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (ACTIVE_CASTS.putIfAbsent(playerId, new CastState()) != null) {
            // 同一引导被重复触发时只刷新虚弱时间，不重复叠加削弱层数
            applyWeakness(player);
            addModifiers(player);
            return;
        }

        player.getFoodData().addExhaustion(START_EXHAUSTION);
        applyWeakness(player);
        addModifiers(player);
    }

    /** 每次物品使用 tick 调用；只在服务端按固定间隔播放叮铃声。 */
    public static void tick(ServerPlayer player, int ticksRemaining) {
        int ticksElapsed = DURATION_TICKS - ticksRemaining;
        if (ticksRemaining > 0 && ticksElapsed % CHIME_INTERVAL_TICKS == 0) {
            playChime(player);
        }
    }

    /**
     * 物品停止回调之外的兜底：若仍标记为引导中但玩家已不在使用图腾，按中断处理并施加失明。
     */
    public static void checkActive(Player player) {
        if (player.level().isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!ACTIVE_CASTS.containsKey(serverPlayer.getUUID())) {
            return;
        }

        if (serverPlayer.isUsingItem()
                && serverPlayer.getUseItem().is(ImmortalityTotemMod.IMMORTALITY_TOTEM.getKey())) {
            return;
        }

        cancel(serverPlayer);
    }

    /**
     * 引导结束的中断路径：松开右键、切换物品或断开连接。
     * 移除引导期间的临时属性，并叠加 18 秒失明 I；开始时的消耗度和虚弱不会被撤回。
     */
    public static void cancel(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        if (ACTIVE_CASTS.remove(player.getUUID()) == null) {
            return;
        }

        removeModifiers(player);
        if (player instanceof ServerPlayer serverPlayer) {
            applyBlindness(serverPlayer,true);
        }
    }

    /** 完整长按 60 tick 后完成引导：叠加失明、原地 flash，再传送到当前出生点。 */
    public static void finish(ServerPlayer player) {
        // 正常完成先结束状态；随后 stopUsingItem 调用的 onStopUsing 不会重复施加失明
        if (ACTIVE_CASTS.remove(player.getUUID()) == null) {
            return;
        }

        removeModifiers(player);
        applyBlindness(player,false);

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
        ServerPlayer teleported = player.teleport(transition);
        // 只有确认成功传送后，才给图腾物品添加与引导时长等长的冷却
        if (teleported != null) {
            teleported.getCooldowns().addCooldown(
                    ImmortalityTotemMod.IMMORTALITY_TOTEM.toStack(),
                    3*DURATION_TICKS
            );
        }
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

    /** 施加 6 秒虚弱 V；已存在时强制刷新剩余时间。 */
    private static void applyWeakness(ServerPlayer player) {
        player.forceAddEffect(
                new MobEffectInstance(
                        MobEffects.WEAKNESS,
                        2*DURATION_TICKS,
                        WEAKNESS_AMPLIFIER,
                        false,
                        true,
                        true
                ),
                null
        );
    }

    /** 叠加 6 秒或叠加 3 秒失明 I：已有失明时在其剩余时间上累加，而不是只刷新。 */
    private static void applyBlindness(ServerPlayer player , boolean Interrupt) {
        MobEffectInstance current = player.getEffect(MobEffects.BLINDNESS);
        int duration = 2*DURATION_TICKS;
        if (Interrupt) {
            duration /= 6;
        }
        if (current != null && !current.isInfiniteDuration()) {
            duration += (int) Math.min(
                    6*DURATION_TICKS,
                    (long) current.getDuration()
            );
        }
        player.addEffect(new MobEffectInstance(
                MobEffects.BLINDNESS,
                duration,
                BLINDNESS_AMPLIFIER,
                false,
                true,
                true
        ));
    }

    /** 一次引导的状态占位；当前只需记录 UUID 是否仍在引导。 */
    private static final class CastState {
    }
}
