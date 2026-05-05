package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Server-side blocklist that screens requested aliases against an admin-editable wordlist
 * at {@code <world>/playerdisguise/alias-blocklist.txt}. The file ships empty; admins add
 * one entry per line (lines starting with {@code #} and blank lines are ignored).
 *
 * <p>Matching: input and blocklist entries are lowercased, then substring-checked. Admins who want
 * to catch leet/punctuation variants add them as explicit lines.
 */
public final class AliasContentFilter {
    private static final AliasContentFilter INSTANCE = new AliasContentFilter();
    public static AliasContentFilter get() { return INSTANCE; }

    private final List<String> normalizedEntries = new ArrayList<>();
    private @Nullable Path file;

    private AliasContentFilter() {}

    public synchronized void setFile(Path file) {
        this.file = file;
        load();
    }

    public synchronized void load() {
        normalizedEntries.clear();
        if (file == null) return;
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file,
                        "# PlayerDisguise alias blocklist\n"
                                + "# One forbidden term per line. Lines starting with # are ignored.\n"
                                + "# Matching is case-insensitive substring. To catch leet/punctuation\n"
                                + "# variants, add them as separate lines.\n"
                                + "# Example:\n"
                                + "#   badwords\n"
                                + "#   b4dw0rds\n",
                        StandardCharsets.UTF_8);
                return;
            }
            for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String norm = normalize(line);
                if (!norm.isEmpty()) normalizedEntries.add(norm);
            }
            PlayerDisguise.LOGGER.info("AliasContentFilter loaded {} blocklist entr{} from {}",
                    normalizedEntries.size(), normalizedEntries.size() == 1 ? "y" : "ies", file);
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to load alias blocklist from {}", file, e);
        }
    }

    public synchronized boolean isBlocked(String alias) {
        if (alias == null || alias.isBlank() || normalizedEntries.isEmpty()) return false;
        String norm = normalize(alias);
        if (norm.isEmpty()) return false;
        for (String entry : normalizedEntries) {
            if (norm.contains(entry)) return true;
        }
        return false;
    }

    public synchronized int size() { return normalizedEntries.size(); }

    /** Visible for tests. */
    static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT);
    }
}
