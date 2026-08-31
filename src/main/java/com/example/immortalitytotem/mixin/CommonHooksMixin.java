package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在事件总线 post 之前返回"已取消"，保证受保护玩家不会发布 LivingDeathEvent。 */
@Mixin(value = CommonHooks.class, remap = false)
abstract class CommonHooksMixin {
    /** 拦截 NeoForge 的死亡钩子入口；remap=false 表示该类不在原版混淆映射中。 */
    @Inject(method = "onLivingDeath", at = @At("HEAD"), cancellable = true)
    private static void immortalitytotem$skipDeathEvent(
            LivingEntity entity,
            DamageSource source,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // 目标是携带图腾的玩家：直接短路返回"已取消"（true），不再 post 任何事件
        if (entity instanceof Player player && ImmortalityProtection.stabilize(player)) {
            cir.setReturnValue(true);
        }
    }
}
