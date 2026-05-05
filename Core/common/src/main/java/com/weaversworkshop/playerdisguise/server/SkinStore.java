package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.skin.SkinValidator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class SkinStore {
    /** Soft cap on stored blobs. At ~64 KB each this is ~320 MB worst case. */
    private static final int MAX_ENTRIES = 5000;

    private static final SkinStore INSTANCE = new SkinStore();
    public static SkinStore get() { return INSTANCE; }

    private @Nullable Path dir;

    private SkinStore() {}

    public synchronized void setDir(Path skinsDir) {
        this.dir = skinsDir;
        try { Files.createDirectories(skinsDir); } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Could not create server skin store dir {}", skinsDir, e);
        }
    }

    public synchronized boolean has(String hash) {
        if (dir == null || hash == null || hash.isBlank()) return false;
        return Files.exists(dir.resolve(hash + ".png"));
    }

    public synchronized byte @Nullable [] load(String hash) {
        if (dir == null) return null;
        Path p = dir.resolve(hash + ".png");
        if (!Files.exists(p)) return null;
        try {
            byte[] data = Files.readAllBytes(p);
            try { Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis())); } catch (IOException ignored) {}
            return data;
        } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Failed reading skin blob {}", hash, e);
            return null;
        }
    }

    public synchronized boolean store(String claimedHash, byte[] bytes) {
        if (dir == null) return false;
        SkinValidator.Result r = SkinValidator.validate(bytes);
        if (r instanceof SkinValidator.Result.Err err) {
            PlayerDisguise.LOGGER.warn("Rejecting skin blob: {}", err.message());
            return false;
        }
        SkinValidator.Result.Ok ok = (SkinValidator.Result.Ok) r;
        if (!ok.sha256().equals(claimedHash)) {
            PlayerDisguise.LOGGER.warn("Rejecting skin blob: hash mismatch (claimed {} vs actual {})", claimedHash, ok.sha256());
            return false;
        }
        try {
            Path p = dir.resolve(claimedHash + ".png");
            Files.write(p, bytes);
            try { Files.setLastModifiedTime(p, FileTime.fromMillis(System.currentTimeMillis())); } catch (IOException ignored) {}
            return true;
        } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Failed writing skin blob {}", claimedHash, e);
            return false;
        }
    }

    /**
     * Trim store to {@link #MAX_ENTRIES} blobs, deleting oldest-by-mtime first. Eviction is unconditional —
     * even referenced blobs may be evicted if they're the oldest. The two-step handshake handles cache misses
     * gracefully: a client whose blob was evicted will simply be asked to re-upload on next join.
     */
    public synchronized int enforceCap() {
        if (dir == null) return 0;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (p.getFileName().toString().endsWith(".png")) files.add(p);
            }
        } catch (IOException e) { return 0; }
        if (files.size() <= MAX_ENTRIES) return 0;
        files.sort(Comparator.comparingLong(p -> {
            try { return Files.getLastModifiedTime(p).toMillis(); }
            catch (IOException e) { return 0L; }
        }));
        int toRemove = files.size() - MAX_ENTRIES;
        int removed = 0;
        for (int i = 0; i < toRemove; i++) {
            try { Files.delete(files.get(i)); removed++; }
            catch (IOException e) { PlayerDisguise.LOGGER.warn("Failed evicting LRU skin blob {}", files.get(i), e); }
        }
        return removed;
    }

    /**
     * Delete every {@code .png} blob whose stem isn't in {@code liveHashes}. Offline players' skins are
     * naturally absent from the live set; they re-upload on next join, so removing them is safe.
     */
    public synchronized int gcOrphans(Set<String> liveHashes) {
        if (dir == null) return 0;
        int removed = 0;
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                String name = p.getFileName().toString();
                if (!name.endsWith(".png")) continue;
                String hash = name.substring(0, name.length() - 4);
                if (liveHashes.contains(hash)) continue;
                try {
                    Files.delete(p);
                    removed++;
                } catch (IOException e) {
                    PlayerDisguise.LOGGER.warn("Failed deleting orphan skin blob {}", p, e);
                }
            }
        } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Failed listing skin store {}", dir, e);
        }
        return removed;
    }
}
