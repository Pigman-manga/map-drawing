package wawa.mapwright.neoforge.sync;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import wawa.mapwright.MapwrightClient;
import wawa.mapwright.data.sync.MapSyncOperation;

import java.util.List;

public record MapSyncPayload(List<MapSyncOperation> operations) implements CustomPacketPayload {
    public static final Type<MapSyncPayload> TYPE = new Type<>(MapwrightClient.id("map_sync"));

    private static final StreamCodec<ByteBuf, MapSyncOperation> OP_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapSyncOperation::x,
            ByteBufCodecs.VAR_INT, MapSyncOperation::y,
            ByteBufCodecs.INT, MapSyncOperation::rgba,
            MapSyncOperation::new
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MapSyncPayload> STREAM_CODEC = StreamCodec.composite(
            OP_CODEC.apply(ByteBufCodecs.list()),
            MapSyncPayload::operations,
            MapSyncPayload::new
    );

    public MapSyncPayload {
        operations = List.copyOf(operations);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
