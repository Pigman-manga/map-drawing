package wawa.mapwright.data.sync;

import wawa.mapwright.MapwrightClient;
import wawa.mapwright.data.Pin;
import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.List;

public final class PinSyncBridge {
    private static final List<PinSyncOperation> pending = new ArrayList<>();
    private static boolean applyingRemote = false;

    private PinSyncBridge() {}

    public static synchronized void queuePut(final Pin.Type type, final double x, final double y) {
        if (!applyingRemote) pending.add(new PinSyncOperation(type.id().toString(), x, y, false));
    }

    public static synchronized void queueRemove(final Pin.Type type) {
        if (!applyingRemote) pending.add(new PinSyncOperation(type.id().toString(), 0, 0, true));
    }

    public static synchronized List<PinSyncOperation> drainPending() {
        final List<PinSyncOperation> out = List.copyOf(pending);
        pending.clear();
        return out;
    }

    public static synchronized void applyRemote(final List<PinSyncOperation> ops) {
        applyingRemote = true;
        try {
            for (final PinSyncOperation op : ops) {
                final Pin.Type type = Pin.getType(net.minecraft.resources.ResourceLocation.tryParse(op.pinId()));
                if (type == null) continue;
                if (op.removed()) MapwrightClient.PAGE_MANAGER.removePin(type);
                else MapwrightClient.PAGE_MANAGER.putPin(type, new Vector2d(op.x(), op.y()));
            }
        } finally {
            applyingRemote = false;
        }
    }

    public static synchronized void clear() { pending.clear(); applyingRemote = false; }
}
