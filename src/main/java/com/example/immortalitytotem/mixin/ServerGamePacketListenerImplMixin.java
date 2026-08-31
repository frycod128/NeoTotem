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
    // 影子字段：与该连接绑定的服务端玩家
    @Shadow public ServerPlayer player;

    /** 拦截客户端命令包的最早入口。 */
    @Inject(method = "handleClientCommand", at = @At("HEAD"), cancellable = true)
    private void immortalitytotem$ignoreDeathRespawnPacket(ServerboundClientCommandPacket packet, CallbackInfo ci) {
        // 仅拦截"执行重生"命令；结束诗传送等其他命令一律放行
        if (packet.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN
                // 打完末影龙看过结束诗后的重生是正常流程，必须放行
                && !this.player.wonGame
                // 玩家受保护：这次重生请求是由死亡假象引起的，先修复状态
                && ImmortalityProtection.stabilize(this.player)) {
            // 丢弃该包，客户端不会触发重生，继续留在正常游戏画面
            ci.cancel();
        }
    }
}
