package com.weaversworkshop.playerdisguise.server;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PendingJoinTracker {
    private static final Set<UUID> PENDING = ConcurrentHashMap.newKeySet();

    private PendingJoinTracker() {}

    public static void mark(UUID uuid) { PENDING.add(uuid); }

    public static boolean clear(UUID uuid) { return PENDING.remove(uuid); }

    public static boolean isPending(UUID uuid) { return PENDING.contains(uuid); }
}
