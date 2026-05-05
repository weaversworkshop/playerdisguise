package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.server.AliasRegistry.NameInterval;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Pure-logic shaping of /whois and /namehistory results, decoupled from {@code CommandSourceStack}
 * and {@code Component} so the op-rank visibility rules can be unit-tested.
 *
 * <p>Op-rank visibility rules:
 * <ul>
 *   <li>/whois — silently omit any holder whose op level exceeds the runner's.
 *   <li>/namehistory — when the target out-ranks the runner, return a "lie" output:
 *       no history rows, and a real-name field showing the target's <em>current alias</em>
 *       (so the runner can't tell the target is even disguised).
 * </ul>
 */
public final class LookupFormatter {
    private LookupFormatter() {}

    /** A single /whois row: which player held the queried alias, during which interval. */
    public record WhoisRow(UUID holder, String displayName, NameInterval interval) {}

    /** Result of {@link #whois}: the rows visible to the runner (after op filtering). */
    public record WhoisResult(List<WhoisRow> rows) {
        public boolean isEmpty() { return rows.isEmpty(); }
    }

    /**
     * Result of {@link #nameHistory}.
     * <p>{@code lieMode == true} ⇒ runner is below the target's op level: only {@code shownRealName}
     * is meaningful (set to the target's current alias) and {@code rows} is always empty.
     */
    public record NameHistoryResult(boolean lieMode, String shownRealName, List<NameInterval> rows) {
        public boolean hasHistory() { return !rows.isEmpty(); }
    }

    /**
     * Build the visible /whois rows for an alias query.
     *
     * @param holders        all UUIDs that have ever held the queried alias, with their matching intervals
     *                       (typically {@link AliasRegistry#holdersOfAlias(String)}).
     * @param runnerOp       op level of the player running the command (0–4).
     * @param opLevelOf      maps a UUID to its op level on this server (0 = not opped).
     * @param realNameOf     maps a UUID to its known real name, or null if unknown — falls back to
     *                       a UUID prefix so output never reveals an opaque hole.
     */
    public static WhoisResult whois(
            Map<UUID, List<NameInterval>> holders,
            int runnerOp,
            ToIntFunction<UUID> opLevelOf,
            Function<UUID, @Nullable String> realNameOf
    ) {
        List<WhoisRow> out = new ArrayList<>();
        for (var entry : holders.entrySet()) {
            UUID holder = entry.getKey();
            int targetOp = opLevelOf.applyAsInt(holder);
            if (runnerOp < targetOp) continue; // silent omission
            String real = realNameOf.apply(holder);
            String display = real != null ? real : holder.toString().substring(0, 8);
            for (NameInterval ni : entry.getValue()) {
                out.add(new WhoisRow(holder, display, ni));
            }
        }
        return new WhoisResult(out);
    }

    /**
     * Build the /namehistory output for a single resolved target.
     *
     * @param target        UUID of the resolved player (caller already ran resolveTarget).
     * @param runnerOp      op level of the player running the command.
     * @param opLevelOf     UUID → op level on this server.
     * @param realNameOf    UUID → known real name (nullable; falls back to {@code queryName}).
     * @param currentAliasOf  UUID → currently active alias (nullable when the player is undisguised).
     * @param historyOf     UUID → ordered name intervals.
     * @param queryName     the literal argument the player typed, used as a real-name fallback.
     */
    public static NameHistoryResult nameHistory(
            UUID target,
            int runnerOp,
            ToIntFunction<UUID> opLevelOf,
            Function<UUID, @Nullable String> realNameOf,
            Function<UUID, @Nullable String> currentAliasOf,
            Function<UUID, List<NameInterval>> historyOf,
            String queryName
    ) {
        int targetOp = opLevelOf.applyAsInt(target);
        String real = realNameOf.apply(target);
        if (real == null) real = queryName;

        if (runnerOp < targetOp) {
            String currentAlias = currentAliasOf.apply(target);
            String shown = currentAlias != null ? currentAlias : real;
            return new NameHistoryResult(true, shown, List.of());
        }

        List<NameInterval> history = historyOf.apply(target);
        return new NameHistoryResult(false, real, history == null ? List.of() : history);
    }
}
