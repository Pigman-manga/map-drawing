package wawa.mapwright.neoforge.sync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import wawa.mapwright.MapwrightClient;
import wawa.mapwright.data.sync.PinSyncOperation;

import java.util.List;

public record PinSyncPayload(List<PinSyncOperation> operations) implements CustomPacketPayload {
    public static final Type<PinSyncPayload> TYPE = new Type<>(MapwrightClient.id("pin_sync"));

    private static final StreamCodec<ByteBuf, PinSyncOperation> OP_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, PinSyncOperation::pinId,
            ByteBufCodecs.DOUBLE, PinSyncOperation::x,
            ByteBufCodecs.DOUBLE, PinSyncOperation::y,
            ByteBufCodecs.BOOL, PinSyncOperation::removed,
            PinSyncOperation::new
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, PinSyncPayload> STREAM_CODEC = StreamCodec.composite(
            OP_CODEC.apply(ByteBufCodecs.list()), PinSyncPayload::operations, PinSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
