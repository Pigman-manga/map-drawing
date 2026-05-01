package wawa.mapwright.neoforge.sync;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.joml.Vector2dc;
import wawa.mapwright.MapwrightClient;
import wawa.mapwright.data.PageIO;
import wawa.mapwright.data.Pin;
import wawa.mapwright.data.sync.MapSyncBridge;
import wawa.mapwright.data.sync.MapSyncOperation;
import wawa.mapwright.data.sync.PinSyncBridge;
import wawa.mapwright.data.sync.PinSyncOperation;

import java.util.*;

@EventBusSubscriber(modid = MapwrightClient.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MapSyncNetworking {
    private static final Map<Long, Integer> SERVER_PIXELS = new HashMap<>();
    private static boolean serverLoaded = false;

    private MapSyncNetworking() {}

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1");
        registrar.playBidirectional(MapSyncPayload.TYPE, MapSyncPayload.STREAM_CODEC, MapSyncNetworking::handleSync);
        registrar.playBidirectional(PinSyncPayload.TYPE, PinSyncPayload.STREAM_CODEC, MapSyncNetworking::handlePinSync);
        registrar.playToServer(MapSnapshotRequestPayload.TYPE, MapSnapshotRequestPayload.STREAM_CODEC, MapSyncNetworking::handleSnapshotRequest);
    }

    private static void handleSync(final MapSyncPayload payload, final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            ensureServerSnapshotLoaded();
            for (MapSyncOperation op : payload.operations()) SERVER_PIXELS.put(pack(op.x(), op.y()), op.rgba());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, payload);
            return;
        }
        context.enqueueWork(() -> MapSyncBridge.applyRemoteOperations(payload.operations()));
    }

    private static void handlePinSync(final PinSyncPayload payload, final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(serverPlayer, payload);
            return;
        }
        context.enqueueWork(() -> PinSyncBridge.applyRemote(payload.operations()));
    }

    private static void handleSnapshotRequest(final MapSnapshotRequestPayload payload, final IPayloadContext context) {
        if (context.player() instanceof ServerPlayer serverPlayer) {
            context.enqueueWork(() -> {
                ensureServerSnapshotLoaded();
                sendSnapshot(serverPlayer);
                sendPinSnapshot(serverPlayer);
            });
        }
    }

    private static void sendPinSnapshot(final ServerPlayer player) {
        final List<PinSyncOperation> ops = new ArrayList<>();
        for (Pin pin : MapwrightClient.PAGE_MANAGER.getPins()) {
            final Vector2dc p = pin.getPosition();
            if (p != null) ops.add(new PinSyncOperation(pin.type.id().toString(), p.x(), p.y(), false));
        }
        if (!ops.isEmpty()) PacketDistributor.sendToPlayer(player, new PinSyncPayload(ops));
    }

    private static void sendSnapshot(final ServerPlayer serverPlayer) {
        final List<MapSyncOperation> batch = new ArrayList<>(1024);
        for (var e : SERVER_PIXELS.entrySet()) {
            final int x = (int)(e.getKey() >> 32);
            final int y = (int)(long)e.getKey();
            batch.add(new MapSyncOperation(x, y, e.getValue()));
            if (batch.size() >= 1024) {
                PacketDistributor.sendToPlayer(serverPlayer, new MapSyncPayload(List.copyOf(batch)));
                batch.clear();
            }
        }
        if (!batch.isEmpty()) PacketDistributor.sendToPlayer(serverPlayer, new MapSyncPayload(List.copyOf(batch)));
    }

    private static void ensureServerSnapshotLoaded() {
        if (serverLoaded) return;
        serverLoaded = true;
        final PageIO pageIO = MapwrightClient.PAGE_MANAGER.pageIO;
        if (pageIO == null) return;
        final Map<Integer, NativeImage> images = pageIO.tryLoadAllPages();
        for (var entry : images.entrySet()) {
            final int packed = entry.getKey();
            final int rx = packed >> 16;
            final int ry = (short) (packed & 0xFFFF);
            final NativeImage image = entry.getValue();
            for (int x = 0; x < MapwrightClient.CHUNK_SIZE; x++) for (int y = 0; y < MapwrightClient.CHUNK_SIZE; y++) {
                int rgba = image.getPixelRGBA(x, y);
                if (rgba != 0) SERVER_PIXELS.put(pack(rx * MapwrightClient.CHUNK_SIZE + x, ry * MapwrightClient.CHUNK_SIZE + y), rgba);
            }
            image.close();
        }
    }

    private static long pack(int x, int y) { return (((long)x) << 32) | (y & 0xffffffffL); }

    public static void flushClientPending() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) return;
        final var ops = MapSyncBridge.drainPending();
        if (!ops.isEmpty()) PacketDistributor.sendToServer(new MapSyncPayload(ops));
        final var pinOps = PinSyncBridge.drainPending();
        if (!pinOps.isEmpty()) PacketDistributor.sendToServer(new PinSyncPayload(pinOps));
    }

    public static void requestSnapshot() {
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) PacketDistributor.sendToServer(new MapSnapshotRequestPayload());
    }
}
