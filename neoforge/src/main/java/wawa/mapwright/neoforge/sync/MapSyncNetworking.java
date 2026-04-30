package wawa.mapwright.neoforge.sync;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import wawa.mapwright.MapwrightClient;
import wawa.mapwright.data.sync.MapSyncBridge;

import java.util.List;

@EventBusSubscriber(modid = MapwrightClient.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MapSyncNetworking {
    private MapSyncNetworking() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playBidirectional(MapSyncPayload.TYPE, MapSyncPayload.STREAM_CODEC, MapSyncNetworking::handleSync);
        registrar.playToServer(MapSnapshotRequestPayload.TYPE, MapSnapshotRequestPayload.STREAM_CODEC, MapSyncNetworking::handleSnapshotRequest);
    }

    private static void handleSync(final MapSyncPayload payload, final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, payload);
            return;
        }

        context.enqueueWork(() -> MapSyncBridge.applyRemoteOperations(payload.operations()));
    }

    private static void handleSnapshotRequest(final MapSnapshotRequestPayload payload, final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            // On integrated server this mirrors the host's current map state to a joining client via server relay.
            final List<wawa.mapwright.data.sync.MapSyncOperation> ops = MapSyncBridge.drainPending();
            if (!ops.isEmpty()) {
                PacketDistributor.sendToPlayer(serverPlayer, new MapSyncPayload(ops));
            }
        }
    }

    public static void flushClientPending() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }

        final var ops = MapSyncBridge.drainPending();
        if (!ops.isEmpty()) {
            PacketDistributor.sendToServer(new MapSyncPayload(ops));
        }
    }

    public static void requestSnapshot() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            PacketDistributor.sendToServer(new MapSnapshotRequestPayload());
        }
    }
}
