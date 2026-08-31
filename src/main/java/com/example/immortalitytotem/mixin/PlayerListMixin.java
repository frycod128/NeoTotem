package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 防止直接调用死亡重生 API 时留下旧玩家并创建重复 UUID 的新实例。 */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    /** 拦截重生入口：KILLED 理由的重生对受保护玩家直接返回原实例。 */
    @Inject(method = "respawn", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreKilledRespawn(
            ServerPlayer player,
            boolean keepAllPlayerData,
            Entity.RemovalReason reason,
            CallbackInfoReturnable<ServerPlayer> cir
    ) {
        // KILLED 理由 + 玩家受保护 → 无需重生，原玩家继续使用（避免重复 UUID）
        if (reason == Entity.RemovalReason.KILLED && ImmortalityProtection.stabilize(player)) {
            cir.setReturnValue(player);
        }
    }
}
