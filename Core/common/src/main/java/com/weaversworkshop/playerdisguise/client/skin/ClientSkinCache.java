package com.weaversworkshop.playerdisguise.client.skin;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.skin.SkinValidator;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ClientSkinCache {
    private static final ClientSkinCache INSTANCE = new ClientSkinCache();
    public static ClientSkinCache get() { return INSTANCE; }

    private @Nullable Path dir;

    private ClientSkinCache() {}

    public synchronized void setDir(Path cacheDir) {
        this.dir = cacheDir;
        try { Files.createDirectories(cacheDir); } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Could not create client skin cache dir {}", cacheDir, e);
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
        try { return Files.readAllBytes(p); } catch (IOException e) { return null; }
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
        try { Files.write(dir.resolve(claimedHash + ".png"), bytes); return true; }
        catch (IOException e) { return false; }
    }
}
