package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.PlayerDisguise;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Append-only log of player-submitted disguise reports.
 * Writes to {@code <world>/playerdisguise/reports.log}; each line is a tab-separated record.
 */
public final class ReportLog {
    private static final ReportLog INSTANCE = new ReportLog();
    public static ReportLog get() { return INSTANCE; }

    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private @Nullable Path file;

    private ReportLog() {}

    public synchronized void setFile(Path file) { this.file = file; }

    public synchronized boolean append(UUID reporterUuid, String reporterRealName,
                                       UUID targetUuid, String targetRealName,
                                       String targetAlias, @Nullable String reason) {
        if (file == null) return false;
        String ts = TS_FMT.format(Instant.now());
        String safeReason = reason == null ? "" : reason.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        String line = String.join("\t",
                ts,
                "reporter=" + reporterRealName + "(" + reporterUuid + ")",
                "target=" + targetRealName + "(" + targetUuid + ")",
                "alias=" + targetAlias,
                "reason=" + safeReason) + System.lineSeparator();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            PlayerDisguise.LOGGER.error("Failed to append to reports log {}", file, e);
            return false;
        }
    }
}
