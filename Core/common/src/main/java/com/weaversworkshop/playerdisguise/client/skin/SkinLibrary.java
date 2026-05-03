package com.weaversworkshop.playerdisguise.client.skin;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.skin.SkinValidator;
import net.minecraft.client.resources.PlayerSkin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class SkinLibrary {
    public sealed interface Entry {
        String filename();
        Path path();

        record Valid(String filename, Path path, byte[] bytes, String sha256, int width, int height)
                implements Entry {}
        record Invalid(String filename, Path path, String reason) implements Entry {}
    }

    private final Path skinsDir;
    private List<Entry> entries = List.of();

    public SkinLibrary(Path skinsDir) {
        this.skinsDir = skinsDir;
    }

    public List<Entry> entries() {
        return entries;
    }

    public void refresh() {
        try {
            Files.createDirectories(skinsDir);
        } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Could not create skins directory {}", skinsDir, e);
            entries = List.of();
            return;
        }
        List<Entry> next = new ArrayList<>();
        try (Stream<Path> s = Files.list(skinsDir)) {
            s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEach(p -> next.add(toEntry(p)));
        } catch (IOException e) {
            PlayerDisguise.LOGGER.warn("Failed to list skins directory", e);
        }
        entries = List.copyOf(next);
    }

    private static Entry toEntry(Path p) {
        String name = p.getFileName().toString();
        try {
            byte[] bytes = Files.readAllBytes(p);
            SkinValidator.Result r = SkinValidator.validate(bytes);
            if (r instanceof SkinValidator.Result.Ok ok) {
                return new Entry.Valid(name, p, bytes, ok.sha256(), ok.width(), ok.height());
            }
            return new Entry.Invalid(name, p, ((SkinValidator.Result.Err) r).message());
        } catch (IOException e) {
            return new Entry.Invalid(name, p, "Read failed: " + e.getMessage());
        }
    }

    public @Nullable Entry.Valid findByFilename(String filename) {
        for (Entry e : entries) {
            if (e instanceof Entry.Valid v && v.filename().equals(filename)) return v;
        }
        return null;
    }

    public PlayerSkin loadAsSkin(Entry.Valid v, PlayerSkin.Model model) throws IOException {
        return SkinTextureCache.getOrRegister(v.bytes(), v.sha256(), model);
    }
}
