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
    /** 单例实例：本载荷不含任何数据，所有收发都复用同一实例。 */
    public static final RequiredNetworkPayload INSTANCE = new RequiredNetworkPayload();
    /** 通道类型标识：immortalitytotem:required_network。 */
    public static final Type<RequiredNetworkPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(ImmortalityTotemMod.MODID, "required_network")
    );
    /** 编解码器：unit 编解码器不读写任何字节，直接复用单例。 */
    public static final StreamCodec<RegistryFriendlyByteBuf, RequiredNetworkPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    /** 注册载荷处理器（由 mod 构造器挂到 mod 事件总线）。 */
    static void register(RegisterPayloadHandlersEvent event) {
        // 默认 optional=false。无需实际发送；注册信息本身参与 NeoForge 通道协商。
        event.registrar("1").playBidirectional(
                TYPE,
                STREAM_CODEC,
                // 客户端→服务端的处理：无数据，空实现
                (payload, context) -> { },
                // 服务端→客户端的处理：无数据，空实现
                (payload, context) -> { }
        );
    }

    /** 实现 CustomPacketPayload 必须提供的类型查询。 */
    @Override
    public Type<RequiredNetworkPayload> type() {
        return TYPE;
    }
}
