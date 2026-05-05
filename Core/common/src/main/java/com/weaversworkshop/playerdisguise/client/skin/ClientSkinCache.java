package com.weaversworkshop.playerdisguise.client.skin;

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
import java.util.stream.Stream;

public final class ClientSkinCache {
    /** Max cached skin files. At ~64 KB each this is ~12 MB worst case. */
    private static final int MAX_ENTRIES = 200;

    private static final ClientSkinCache INSTANCE = new ClientSkinCache();
    public static ClientSkinCache get() { return INSTANCE; }

    private @Nullable Path dir;

    private ClientSkinCache() {}

    public synchronized void setDir(Path cacheDir) {
        this.dir = cacheDir;
        try { Files.createDirectories(cacheDir); } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Could not create client skin cache dir {}", cacheDir, e);
        }
        evictIfOverCap();
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
        } catch (IOException e) { return null; }
    }

    public synchronized boolean store(String claimedHash, byte[] bytes) {
        if (dir == null) return false;
        SkinValidator.Result r = SkinValidator.validate(bytes);
        if (r instanceof SkinValidator.Result.Err err) {
            PlayerDisguise.LOGGER.warn("Refusing to cache invalid skin blob: {}", err.message());
            return false;
        }
        SkinValidator.Result.Ok ok = (SkinValidator.Result.Ok) r;
        if (!ok.sha256().equals(claimedHash)) {
            PlayerDisguise.LOGGER.warn("Refusing to cache: hash mismatch ({} vs {})", claimedHash, ok.sha256());
            return false;
        }
        try {
            Files.write(dir.resolve(claimedHash + ".png"), bytes);
            evictIfOverCap();
            return true;
        }
        catch (IOException e) { return false; }
    }

    /** Trim the cache directory to {@link #MAX_ENTRIES}, deleting oldest-by-mtime first. */
    private void evictIfOverCap() {
        if (dir == null) return;
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (p.getFileName().toString().endsWith(".png")) files.add(p);
            }
        } catch (IOException e) { return; }
        if (files.size() <= MAX_ENTRIES) return;
        files.sort(Comparator.comparingLong(p -> {
            try { return Files.getLastModifiedTime(p).toMillis(); }
            catch (IOException e) { return 0L; }
        }));
        int toRemove = files.size() - MAX_ENTRIES;
        for (int i = 0; i < toRemove; i++) {
            try { Files.delete(files.get(i)); }
            catch (IOException e) { PlayerDisguise.LOGGER.warn("Failed evicting cached skin {}", files.get(i), e); }
        }
    }
}
