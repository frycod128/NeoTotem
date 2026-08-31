package com.immortalitytotem;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** NeoForge 可覆盖的正常入口优先使用事件，减少与其他模组伤害管线的冲突。 */
public final class ImmortalityEvents {
    /**
     * /kill 最终使用 generic_kill + Float.MAX_VALUE。尽早取消可避免巨量伤害污染统计和其他模组计算。
     * 非有限伤害同样属于非法输入；正常有限伤害继续执行，并由生命值写入点保证下限。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        // 只处理玩家，且该玩家必须带着图腾（stabilize 内部已含 hasTotem 判断）
        if (!(event.getEntity() instanceof Player player) || !ImmortalityProtection.stabilize(player)) {
            return;
        }

        // generic_kill（/kill 指令底层）与 NaN/无穷伤害属于非法输入，直接取消这次伤害
        if (event.getSource().is(DamageTypes.GENERIC_KILL) || !Float.isFinite(event.getAmount())) {
            event.setCanceled(true);
        }
    }

    /**
     * 让其他模组先完成减伤修改，再把最终伤害限制为"生命 + 吸收生命 - 1"。
     * 这样 Post 事件、伤害统计和实际扣血保持一致；setHealth Mixin 仍作为不可绕过的硬下限。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamagePre(LivingDamageEvent.Pre event) {
        // 同上：非玩家或无图腾直接放行原版逻辑
        if (!(event.getEntity() instanceof Player player) || !ImmortalityProtection.stabilize(player)) {
            return;
        }

        // 读取吸收生命（黄心）；异常值按 0 处理，避免污染伤害上限计算
        float absorption = player.getAbsorptionAmount();
        if (!Float.isFinite(absorption) || absorption < 0.0F) {
            absorption = 0.0F;
        }

        // 允许的最大伤害 = 当前生命 + 吸收生命 - 保底 1 点生命（用 double 计算防溢出）
        double allowedAsDouble = Math.max(
                0.0D,
                (double) player.getHealth() + absorption - ImmortalityProtection.MINIMUM_HEALTH
        );
        // double 结果可能超过 float 上限，钳制到 Float.MAX_VALUE
        float allowedDamage = (float) Math.min((double) Float.MAX_VALUE, allowedAsDouble);
        // 各模组修改后的最终请求伤害
        float requestedDamage = event.getNewDamage();

        // 非法伤害（NaN/无穷/负数）一律归零，不造成任何扣血
        if (!Float.isFinite(requestedDamage) || requestedDamage < 0.0F) {
            event.setNewDamage(0.0F);
            // 合法但超过上限的伤害压到上限，保证受保护玩家至少剩 1 点血
        } else if (requestedDamage > allowedDamage) {
            event.setNewDamage(allowedDamage);
        }
    }

    /** 服务端和客户端都会收到 PlayerTickEvent；tick 前先修复直接数据篡改。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        // 生命异常、处于死亡计时或倒地姿态时需要修复
        if (needsStabilization(player)) {
            // 强制回到合法状态（保底 1 血、清除死亡计时与倒地姿态）
            ImmortalityProtection.stabilize(player);
        }
    }

    /** tick 后再检查一次，覆盖本 tick 内绕过 setHealth 的直接 SynchedEntityData 改写。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        // 本 tick 结束前最后一次兜底检查
        if (needsStabilization(player)) {
            ImmortalityProtection.stabilize(player);
        }
        // 物品停止回调之外的兜底：玩家意外停止使用图腾时同样结算引导结束副作用
        ImmortalityTotemCasting.checkActive(player);
    }

    /** 玩家登出时立刻结算中断副作用，避免属性修饰符与活跃状态残留。 */
    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ImmortalityTotemCasting.cancel(event.getEntity());
    }

    /** 开发运行专用的运行期断言；正式 JAR 未设置该系统属性时完全不执行。 */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        // 仅当 JVM 参数设置 -Dimortalitytotem.selfTest=true 时才运行自检
        if (Boolean.getBoolean("immortalitytotem.selfTest")) {
            // 在专用服务器实例上执行全套回归断言
            ImmortalitySelfTest.run(event.getServer());
        }
    }

    /** 判断玩家是否处于需要修复的非法/半死亡状态。 */
    private static boolean needsStabilization(Player player) {
        // 生命非法或低于保底 1 点
        return ImmortalityProtection.isInvalidOrBelowFloor(player.getHealth())
                // 处于死亡动画计时中（deathTime > 0）
                || player.deathTime != 0
                // 处于倒地（DYING）姿态
                || player.getPose() == Pose.DYING;
    }
}
