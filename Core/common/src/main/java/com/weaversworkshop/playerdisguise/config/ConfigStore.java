package com.weaversworkshop.playerdisguise.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.weaversworkshop.playerdisguise.PlayerDisguise;
import com.weaversworkshop.playerdisguise.skin.SkinValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static final String JSON_NAME = "client.json";
    private static final String SKINS_DIR = "skins";

    private final Path dir;
    private PseudonymConfig current = PseudonymConfig.EMPTY;

    public ConfigStore(Path modConfigDir) {
        this.dir = modConfigDir;
    }

    public PseudonymConfig current() {
        return current;
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
                current = PseudonymConfig.EMPTY;
                return;
            }
            PseudonymConfig loaded = GSON.fromJson(Files.readString(json), PseudonymConfig.class);
            if (loaded == null) loaded = PseudonymConfig.EMPTY;

            if (loaded.hasSkin()) {
                Path skin = skinPath(loaded.skinFileName());
                if (!Files.exists(skin)) {
                    PlayerDisguise.LOGGER.warn("Configured skin '{}' is missing; clearing.", loaded.skinFileName());
                    loaded = new PseudonymConfig(loaded.pseudonymName(), null, null);
                } else {
                    SkinValidator.Result r = SkinValidator.validate(skin);
                    if (r instanceof SkinValidator.Result.Err err) {
                        PlayerDisguise.LOGGER.warn("Stored skin '{}' failed validation ({}); clearing.",
                                loaded.skinFileName(), err.message());
                        loaded = new PseudonymConfig(loaded.pseudonymName(), null, null);
                    } else if (r instanceof SkinValidator.Result.Ok ok && !ok.sha256().equals(loaded.skinHash())) {
                        PlayerDisguise.LOGGER.info("Skin '{}' changed on disk; updating hash.", loaded.skinFileName());
                        loaded = new PseudonymConfig(loaded.pseudonymName(), loaded.skinFileName(), ok.sha256());
                    }
                }
            }
            current = loaded;
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to load pseudonym config", e);
            current = PseudonymConfig.EMPTY;
        }
    }

    public void save(PseudonymConfig cfg) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve(JSON_NAME), GSON.toJson(cfg));
        current = cfg;
    }
}
