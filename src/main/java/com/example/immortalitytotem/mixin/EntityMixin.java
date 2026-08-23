package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 捕获绕过 LivingEntity#remove、直接调用最终 setRemoved(KILLED) 的代码。 */
@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDirectKilledRemoval(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (reason == Entity.RemovalReason.KILLED
                && self instanceof Player player
                && ImmortalityProtection.stabilize(player)) {
            ci.cancel();
        }
    }
}
