package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.server.AliasRegistry.NameInterval;
import com.weaversworkshop.playerdisguise.server.LookupFormatter.NameHistoryResult;
import com.weaversworkshop.playerdisguise.server.LookupFormatter.WhoisResult;
import com.weaversworkshop.playerdisguise.server.LookupFormatter.WhoisRow;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LookupFormatterTest {

    private final UUID alice = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID bob   = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID admin = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    private static NameInterval interval(String name, long start, long end, boolean realName) {
        return new NameInterval(name, start, end, realName);
    }

    // ---- /whois ---------------------------------------------------------------

    @Test
    void whois_emptyHolders_yieldsEmptyResult() {
        WhoisResult r = LookupFormatter.whois(Map.of(), 4, u -> 0, u -> null);
        assertTrue(r.isEmpty());
        assertEquals(0, r.rows().size());
    }

    @Test
    void whois_passesIntervalsThrough_oneRowPerInterval() {
        Map<UUID, List<NameInterval>> holders = Map.of(alice, List.of(
                interval("Shadow", 1000, 2000, false),
                interval("Shadow", 5000, 0L, false)));
        WhoisResult r = LookupFormatter.whois(holders, 4, u -> 0, u -> "Alice");
        assertEquals(2, r.rows().size());
        assertEquals(alice, r.rows().get(0).holder());
        assertEquals("Alice", r.rows().get(0).displayName());
        assertEquals(1000L, r.rows().get(0).interval().startMs());
        assertEquals(0L, r.rows().get(1).interval().endMs()); // open
    }

    @Test
    void whois_silentlyOmitsHigherOpHolders() {
        // Map with two holders: alice (op 0) and admin (op 4). Runner op = 2.
        Map<UUID, List<NameInterval>> holders = new LinkedHashMap<>();
        holders.put(alice, List.of(interval("Shadow", 1000, 2000, false)));
        holders.put(admin, List.of(interval("Shadow", 3000, 0L, false)));

        Map<UUID, Integer> ops = Map.of(alice, 0, admin, 4);
        WhoisResult r = LookupFormatter.whois(holders, 2, ops::get, u -> u.equals(admin) ? "AdminUser" : "Alice");

        assertEquals(1, r.rows().size());
        assertEquals(alice, r.rows().get(0).holder()); // admin row silently dropped
    }

    @Test
    void whois_runnerEqualOp_seesPeer() {
        Map<UUID, List<NameInterval>> holders = Map.of(admin, List.of(interval("Shadow", 1000, 0L, false)));
        WhoisResult r = LookupFormatter.whois(holders, 4, u -> 4, u -> "AdminUser");
        assertEquals(1, r.rows().size());
    }

    @Test
    void whois_unknownRealName_fallsBackToUuidPrefix() {
        Map<UUID, List<NameInterval>> holders = Map.of(bob, List.of(interval("Shadow", 1000, 2000, false)));
        WhoisResult r = LookupFormatter.whois(holders, 4, u -> 0, u -> null);
        assertEquals(1, r.rows().size());
        WhoisRow row = r.rows().get(0);
        assertEquals(8, row.displayName().length());
        assertTrue(bob.toString().startsWith(row.displayName()));
    }

    // ---- /namehistory ---------------------------------------------------------

    @Test
    void nameHistory_normalCase_returnsRealNameAndHistory() {
        List<NameInterval> hist = List.of(
                interval("Alice", 1000, 2000, true),
                interval("Shadow", 2000, 3000, false),
                interval("Alice", 3000, 0L, true));

        NameHistoryResult r = LookupFormatter.nameHistory(
                alice,
                /*runnerOp*/ 4,
                u -> 0,
                u -> "Alice",
                u -> null,
                u -> hist,
                "Shadow");

        assertFalse(r.lieMode());
        assertEquals("Alice", r.shownRealName());
        assertEquals(3, r.rows().size());
        assertTrue(r.hasHistory());
    }

    @Test
    void nameHistory_unknownRealName_fallsBackToQueryName() {
        NameHistoryResult r = LookupFormatter.nameHistory(
                alice, 4, u -> 0, u -> null, u -> null, u -> List.of(), "TypedQuery");
        assertEquals("TypedQuery", r.shownRealName());
    }

    @Test
    void nameHistory_lieMode_returnsCurrentAlias_emptyHistory() {
        // Runner op 0, target op 4. Even though target has rich history, lie mode hides it.
        List<NameInterval> hiddenHistory = List.of(
                interval("AdminUser", 1000, 2000, true),
                interval("PublicAlias", 2000, 0L, false));

        NameHistoryResult r = LookupFormatter.nameHistory(
                admin,
                /*runnerOp*/ 0,
                u -> 4,
                u -> "AdminUser",
                u -> "PublicAlias",
                u -> hiddenHistory,
                "PublicAlias");

        assertTrue(r.lieMode());
        assertEquals("PublicAlias", r.shownRealName()); // target's current alias, NOT real name
        assertTrue(r.rows().isEmpty());
        assertFalse(r.hasHistory());
    }

    @Test
    void nameHistory_lieMode_targetUndisguised_showsRealName() {
        // Edge case: target out-ranks runner, target currently has no alias (currentAlias = null).
        // shownRealName falls back to the resolved real name. In practice runner can't tell whether
        // target is undisguised vs. just opaque — there's no separate "no alias" tell.
        NameHistoryResult r = LookupFormatter.nameHistory(
                admin,
                /*runnerOp*/ 0,
                u -> 4,
                u -> "AdminUser",
                u -> null, // no current alias
                u -> List.of(interval("AdminUser", 1000, 0L, true)),
                "AdminUser");

        assertTrue(r.lieMode());
        assertEquals("AdminUser", r.shownRealName());
        assertTrue(r.rows().isEmpty());
    }

    @Test
    void nameHistory_runnerEqualOp_seesEverything() {
        List<NameInterval> hist = List.of(interval("Alice", 1000, 0L, true));
        NameHistoryResult r = LookupFormatter.nameHistory(
                alice,
                /*runnerOp*/ 4,
                u -> 4, // peer op level
                u -> "Alice",
                u -> null,
                u -> hist,
                "Alice");
        assertFalse(r.lieMode());
        assertEquals(1, r.rows().size());
    }

    @Test
    void nameHistory_nullHistorySupplier_normalizedToEmptyList() {
        NameHistoryResult r = LookupFormatter.nameHistory(
                alice, 4, u -> 0, u -> "Alice", u -> null, u -> null, "Alice");
        assertNotNull(r.rows());
        assertTrue(r.rows().isEmpty());
        assertFalse(r.hasHistory());
    }

    @Test
    void whois_lookupFunctionsCalledExactlyOncePerHolder() {
        Map<UUID, List<NameInterval>> holders = new HashMap<>();
        holders.put(alice, List.of(interval("Shadow", 1, 2, false), interval("Shadow", 3, 4, false)));
        holders.put(bob, List.of(interval("Shadow", 5, 0L, false)));

        Map<UUID, Integer> opCalls = new HashMap<>();
        Map<UUID, Integer> realCalls = new HashMap<>();
        LookupFormatter.whois(holders, 4,
                u -> { opCalls.merge(u, 1, Integer::sum); return 0; },
                u -> { realCalls.merge(u, 1, Integer::sum); return "X"; });

        // Each holder's op level and real name should be looked up exactly once even when they have
        // multiple intervals — guards against quadratic blowup if the loop is ever refactored.
        assertEquals(1, opCalls.get(alice));
        assertEquals(1, opCalls.get(bob));
        assertEquals(1, realCalls.get(alice));
        assertEquals(1, realCalls.get(bob));
    }
}
