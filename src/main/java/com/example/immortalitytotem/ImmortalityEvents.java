package com.example.immortalitytotem;

import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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
        if (!(event.getEntity() instanceof Player player) || !ImmortalityProtection.stabilize(player)) {
            return;
        }

        if (event.getSource().is(DamageTypes.GENERIC_KILL) || !Float.isFinite(event.getAmount())) {
            event.setCanceled(true);
        }
    }

    /**
     * 让其他模组先完成减伤修改，再把最终伤害限制为“生命 + 吸收生命 - 1”。
     * 这样 Post 事件、伤害统计和实际扣血保持一致；setHealth Mixin 仍作为不可绕过的硬下限。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player) || !ImmortalityProtection.stabilize(player)) {
            return;
        }

        float absorption = player.getAbsorptionAmount();
        if (!Float.isFinite(absorption) || absorption < 0.0F) {
            absorption = 0.0F;
        }

        double allowedAsDouble = Math.max(
                0.0D,
                (double) player.getHealth() + absorption - ImmortalityProtection.MINIMUM_HEALTH
        );
        float allowedDamage = (float) Math.min((double) Float.MAX_VALUE, allowedAsDouble);
        float requestedDamage = event.getNewDamage();

        if (!Float.isFinite(requestedDamage) || requestedDamage < 0.0F) {
            event.setNewDamage(0.0F);
        } else if (requestedDamage > allowedDamage) {
            event.setNewDamage(allowedDamage);
        }
    }

    /** 服务端和客户端都会收到 PlayerTickEvent；tick 前先修复直接数据篡改。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerTickPre(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (needsStabilization(player)) {
            ImmortalityProtection.stabilize(player);
        }
    }

    /** tick 后再检查一次，覆盖本 tick 内绕过 setHealth 的直接 SynchedEntityData 改写。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (needsStabilization(player)) {
            ImmortalityProtection.stabilize(player);
        }
    }

    /** 开发运行专用的运行期断言；正式 JAR 未设置该系统属性时完全不执行。 */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (Boolean.getBoolean("immortalitytotem.selfTest")) {
            ImmortalitySelfTest.run(event.getServer());
        }
    }

    private static boolean needsStabilization(Player player) {
        return ImmortalityProtection.isInvalidOrBelowFloor(player.getHealth())
                || player.deathTime != 0
                || player.getPose() == Pose.DYING;
    }
}
