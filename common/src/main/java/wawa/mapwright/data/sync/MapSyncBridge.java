package wawa.mapwright.data.sync;

import wawa.mapwright.MapwrightClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Common-side map sync coordinator.
 *
 * This class is intentionally transport-agnostic: platform code can drain pending operations and
 * forward them through networking, then re-apply remote operations with loop protection.
 */
public final class MapSyncBridge {
    private static final int MAX_BATCH_SIZE = 2048;

    private static final List<MapSyncOperation> pending = new ArrayList<>();
    private static boolean applyingRemote = false;

    private MapSyncBridge() {}

    public static synchronized void queueLocalOperation(final int x, final int y, final int rgba) {
        if (applyingRemote) {
            return;
        }

        if (pending.size() >= MAX_BATCH_SIZE) {
            pending.removeFirst();
        }
        pending.add(new MapSyncOperation(x, y, rgba));
    }

    public static synchronized List<MapSyncOperation> drainPending() {
        if (pending.isEmpty()) {
            return List.of();
        }

        final List<MapSyncOperation> copy = new ArrayList<>(pending);
        pending.clear();
        return copy;
    }

    public static synchronized int pendingCount() {
        return pending.size();
    }

    public static synchronized void applyRemoteOperations(final List<MapSyncOperation> operations) {
        if (operations.isEmpty()) {
            return;
        }

        applyingRemote = true;
        try {
            for (final MapSyncOperation op : operations) {
                MapwrightClient.PAGE_MANAGER.putPixel(op.x(), op.y(), op.rgba());
            }
        } finally {
            applyingRemote = false;
        }
    }

    public static synchronized void clear() {
        pending.clear();
        applyingRemote = false;
    }
}
