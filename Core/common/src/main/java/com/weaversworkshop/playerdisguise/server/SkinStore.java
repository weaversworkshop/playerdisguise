package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.skin.SkinValidator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

public final class SkinStore {
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
        try { return Files.readAllBytes(p); } catch (IOException e) {
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
            Files.write(dir.resolve(claimedHash + ".png"), bytes);
            return true;
        } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Failed writing skin blob {}", claimedHash, e);
            return false;
        }
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
