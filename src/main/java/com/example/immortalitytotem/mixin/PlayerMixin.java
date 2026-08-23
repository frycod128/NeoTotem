package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 在 Player 自身副作用（例如移除肩上实体）发生前拒绝代码杀和非法伤害。 */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreKillDamage(
            ServerLevel level,
            DamageSource source,
            float damage,
            CallbackInfoReturnable<Boolean> cir
    ) {
        Player self = (Player) (Object) this;
        if (ImmortalityProtection.hasTotem(self)
                && (source.is(DamageTypes.GENERIC_KILL) || !Float.isFinite(damage))) {
            ImmortalityProtection.stabilize(self);
            cir.setReturnValue(false);
        }
    }

    /** 覆盖逻辑客户端 Player 或自定义 Player 实现直接调用 die 的情况。 */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDirectDie(DamageSource source, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (ImmortalityProtection.stabilize(self)) {
            ci.cancel();
        }
    }
}
