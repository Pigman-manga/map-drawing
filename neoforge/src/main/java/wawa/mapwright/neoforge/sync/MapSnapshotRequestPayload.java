package wawa.mapwright.neoforge.sync;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import wawa.mapwright.MapwrightClient;

public record MapSnapshotRequestPayload() implements CustomPacketPayload {
    public static final Type<MapSnapshotRequestPayload> TYPE = new Type<>(MapwrightClient.id("map_snapshot_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MapSnapshotRequestPayload> STREAM_CODEC = StreamCodec.unit(new MapSnapshotRequestPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
