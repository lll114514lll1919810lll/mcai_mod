package com.example.mcai.behavior;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AdminApprovalQueue {
    public static class ApprovalItem {
        public final int id;
        public final UUID targetPlayerId;
        public final String targetPlayerName;
        public final String action;   // "kick"
        public final String reason;
        public final long createdAt;
        private volatile boolean resolved;

        public ApprovalItem(int id, UUID targetPlayerId, String targetPlayerName,
                            String action, String reason) {
            this.id = id;
            this.targetPlayerId = targetPlayerId;
            this.targetPlayerName = targetPlayerName;
            this.action = action;
            this.reason = reason;
            this.createdAt = System.currentTimeMillis();
            this.resolved = false;
        }

        public boolean isResolved() { return resolved; }
    }

    private final ConcurrentMap<Integer, ApprovalItem> items = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ScheduledFuture<?>> timeouts = new ConcurrentHashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(1);
    private final ScheduledExecutorService scheduler;
    private final Consumer<ApprovalItem> onApproved;

    /** @param onApproved called when an item is approved (by admin or auto-timeout) */
    public AdminApprovalQueue(ScheduledExecutorService scheduler, Consumer<ApprovalItem> onApproved) {
        this.scheduler = scheduler;
        this.onApproved = onApproved;
    }

    /** Add a new approval item. Returns the assigned ID. */
    public int addItem(UUID targetId, String targetName, String action, String reason, long timeoutMs) {
        int id = nextId.getAndIncrement();
        ApprovalItem item = new ApprovalItem(id, targetId, targetName, action, reason);
        items.put(id, item);

        ScheduledFuture<?> timeout = scheduler.schedule(() -> {
            // auto-approve on timeout
            if (tryResolve(item, true)) {
                onApproved.accept(item);
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
        timeouts.put(id, timeout);

        return id;
    }

    /** Try to approve an item. Returns the item if successfully resolved, null if already resolved or not found. */
    public ApprovalItem tryApprove(int id) {
        ApprovalItem item = items.get(id);
        if (item == null) return null;
        if (tryResolve(item, true)) {
            return item;
        }
        return null;
    }

    /** Try to reject an item. Returns the item if successfully resolved, null if already resolved or not found. */
    public ApprovalItem tryReject(int id) {
        ApprovalItem item = items.get(id);
        if (item == null) return null;
        if (tryResolve(item, false)) {
            return item;
        }
        return null;
    }

    public ApprovalItem getItem(int id) {
        return items.get(id);
    }

    /** Collect IDs of all unresolved items (for tab completion). */
    public java.util.List<Integer> getUnresolvedIds() {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (var entry : items.entrySet()) {
            if (!entry.getValue().isResolved()) {
                ids.add(entry.getKey());
            }
        }
        java.util.Collections.sort(ids);
        return ids;
    }

    private boolean tryResolve(ApprovalItem item, boolean approved) {
        synchronized (item) {
            if (item.resolved) return false;
            item.resolved = true;
        }
        ScheduledFuture<?> t = timeouts.remove(item.id);
        if (t != null) t.cancel(false);
        return true;
    }
}
