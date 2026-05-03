package com.weaversworkshop.playerdisguise.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.skin.SkinValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final String JSON_NAME = "client.json";
    private static final String SKINS_DIR = "skins";

    private final Path dir;
    private ProfileBook book = ProfileBook.EMPTY;

    public ConfigStore(Path modConfigDir) {
        this.dir = modConfigDir;
    }

    public ProfileBook book() {
        return book;
    }

    public Path skinsDir() {
        return dir.resolve(SKINS_DIR);
    }

    public Path skinPath(String filename) {
        return skinsDir().resolve(filename);
    }

    public void load() {
        try {
            Files.createDirectories(skinsDir());
            Path json = dir.resolve(JSON_NAME);
            if (!Files.exists(json)) {
                book = ProfileBook.EMPTY;
                return;
            }
            ProfileBook loaded = GSON.fromJson(Files.readString(json), ProfileBook.class);
            if (loaded == null) loaded = ProfileBook.EMPTY;

            List<Profile> sanitized = new ArrayList<>();
            for (Profile p : loaded.profiles()) {
                if (p == null || p.name() == null || p.name().isBlank()) continue;
                if (!p.hasSkin()) { sanitized.add(p); continue; }
                Path skin = skinPath(p.skinFileName());
                if (!Files.exists(skin)) {
                    PlayerDisguise.LOGGER.warn("Profile '{}' references missing skin '{}'; keeping reference.", p.name(), p.skinFileName());
                    sanitized.add(p);
                    continue;
                }
                SkinValidator.Result r = SkinValidator.validate(skin);
                if (r instanceof SkinValidator.Result.Err err) {
                    PlayerDisguise.LOGGER.warn("Profile '{}' skin '{}' invalid ({}); keeping reference.", p.name(), p.skinFileName(), err.message());
                    sanitized.add(p);
                } else if (r instanceof SkinValidator.Result.Ok ok && !ok.sha256().equals(p.skinHash())) {
                    PlayerDisguise.LOGGER.info("Profile '{}' skin '{}' changed on disk; updating hash.", p.name(), p.skinFileName());
                    sanitized.add(new Profile(p.name(), p.skinFileName(), ok.sha256()));
                } else {
                    sanitized.add(p);
                }
            }
            int active = loaded.activeIndex();
            if (active > 0 && active <= sanitized.size()) {
                Profile activeProfile = sanitized.get(active - 1);
                if (activeProfile.hasSkin() && !Files.exists(skinPath(activeProfile.skinFileName()))) {
                    PlayerDisguise.LOGGER.warn("Active profile '{}' has missing skin '{}'; reverting active to real identity.",
                            activeProfile.name(), activeProfile.skinFileName());
                    active = 0;
                }
            }
            book = new ProfileBook(sanitized, active);
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to load profile book", e);
            book = ProfileBook.EMPTY;
        }
    }

    public void save(ProfileBook updated) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(JSON_NAME), GSON.toJson(updated));
        book = updated;
    }
}
