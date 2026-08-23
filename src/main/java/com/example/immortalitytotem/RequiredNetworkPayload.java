package com.example.immortalitytotem;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * 不发送数据的必选通道标记。NeoForge 在登录协商阶段会拒绝缺少本通道或协议版本不同的一侧。
 */
public record RequiredNetworkPayload() implements CustomPacketPayload {
    public static final RequiredNetworkPayload INSTANCE = new RequiredNetworkPayload();
    public static final Type<RequiredNetworkPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "required_network")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, RequiredNetworkPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    static void register(RegisterPayloadHandlersEvent event) {
        // 默认 optional=false。无需实际发送；注册信息本身参与 NeoForge 通道协商。
        event.registrar("1").playBidirectional(
                TYPE,
                STREAM_CODEC,
                (payload, context) -> { },
                (payload, context) -> { }
        );
    }

    @Override
    public Type<RequiredNetworkPayload> type() {
        return TYPE;
    }
}
