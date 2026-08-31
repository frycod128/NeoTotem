package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ServerPlayer#die 在 NeoForge 26.2 中先发 ENTITY_DIE GameEvent，再检查 LivingDeathEvent；必须在 HEAD 阻止。
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    /** 在 ServerPlayer 专属的 die 实现最开头拦截，抢在事件副作用发生之前。 */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDirectDie(DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        // stabilize 返回 true 表示该玩家受保护：取消整个死亡流程，避免发出 ENTITY_DIE 事件
        if (ImmortalityProtection.stabilize(self)) {
            ci.cancel();
        }
    }
}
