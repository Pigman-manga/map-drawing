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

import com.mojang.blaze3d.platform.NativeImage;
import wawa.mapwright.data.PageIO;
import wawa.mapwright.data.sync.MapSyncOperation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
            context.enqueueWork(() -> sendDiskSnapshot(serverPlayer));
        }
    }

    private static void sendDiskSnapshot(final ServerPlayer serverPlayer) {
        final PageIO pageIO = MapwrightClient.PAGE_MANAGER.pageIO;
        if (pageIO == null) {
            return;
        }

        final Map<Integer, NativeImage> images = pageIO.tryLoadAllPages();
        final List<MapSyncOperation> batch = new ArrayList<>(1024);
        for (final Map.Entry<Integer, NativeImage> entry : images.entrySet()) {
            final int packed = entry.getKey();
            final int rx = packed >> 16;
            final int ry = (short)(packed & 0xFFFF);
            final NativeImage image = entry.getValue();
            for (int x = 0; x < MapwrightClient.CHUNK_SIZE; x++) {
                for (int y = 0; y < MapwrightClient.CHUNK_SIZE; y++) {
                    final int rgba = image.getPixelRGBA(x, y);
                    if (rgba == 0) {
                        continue;
                    }
                    batch.add(new MapSyncOperation(rx * MapwrightClient.CHUNK_SIZE + x, ry * MapwrightClient.CHUNK_SIZE + y, rgba));
                    if (batch.size() >= 1024) {
                        PacketDistributor.sendToPlayer(serverPlayer, new MapSyncPayload(List.copyOf(batch)));
                        batch.clear();
                    }
                }
            }
            image.close();
        }

        if (!batch.isEmpty()) {
            PacketDistributor.sendToPlayer(serverPlayer, new MapSyncPayload(List.copyOf(batch)));
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
