package com.example.immortalitytotem.mixin;

import com.example.immortalitytotem.ImmortalityProtection;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 拒绝负血/非法包竞态下伪造的死亡重生请求，同时放行结束诗传送。 */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerImplMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDeathRespawnPacket(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                && !this.player.wonGame
                && ImmortalityProtection.stabilize(this.player)) {
            ci.cancel();
        }
    }
}
