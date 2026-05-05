package com.weaversworkshop.playerdisguise.server;

import com.weaversworkshop.playerdisguise.server.AliasRegistry.ClaimResult;
import com.weaversworkshop.playerdisguise.server.AliasRegistry.NameInterval;
import com.weaversworkshop.playerdisguise.server.AliasRegistry.SkinRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasRegistryTest {

    private final UUID alice = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID bob   = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private final UUID carol = UUID.fromString("33333333-3333-3333-3333-333333333333");

    private AliasRegistry reg;
    /** Virtual clock; tests call {@link #tick} to advance past the SHORT_INTERVAL_DROP_MS window. */
    private final long[] now = { 1_700_000_000_000L };

    /** Advance the virtual clock by 200ms, enough to outlive AliasRegistry's <100ms drop window. */
    private void tick() { now[0] += 200L; }
    private void tick(long ms) { now[0] += ms; }

    @BeforeEach
    void reset() {
        reg = AliasRegistry.get();
        reg.clear();
        reg.setCooldownMs(AliasRegistry.DEFAULT_COOLDOWN_MS);
        reg.setClockForTesting(() -> now[0]);
    }

    // ---------- claim() basic outcomes ----------

    @Test
    void claim_blank_isInvalid() {
        assertEquals(ClaimResult.INVALID, reg.claim(alice, ""));
        assertEquals(ClaimResult.INVALID, reg.claim(alice, "   "));
        assertEquals(ClaimResult.INVALID, reg.claim(alice, null));
    }

    @Test
    void claim_unique_accepted_andLookups() {
        assertEquals(ClaimResult.ACCEPTED, reg.claim(alice, "Shadow"));
        assertTrue(reg.isDisguised(alice));
        assertEquals("Shadow", reg.pseudonymOf(alice));
        // Case-insensitive alias→uuid lookup
        assertEquals(alice, reg.uuidOf("shadow"));
        assertEquals(alice, reg.uuidOf("SHADOW"));
    }

    @Test
    void claim_sameAliasBySamePlayer_idempotent() {
        assertEquals(ClaimResult.ACCEPTED, reg.claim(alice, "Shadow"));
        assertEquals(ClaimResult.ACCEPTED, reg.claim(alice, "Shadow"));
        assertEquals(ClaimResult.ACCEPTED, reg.claim(alice, "shadow")); // case-insensitive idempotency
    }

    @Test
    void claim_aliasHeldByOther_rejectedActive() {
        reg.claim(alice, "Shadow");
        assertEquals(ClaimResult.REJECTED_ACTIVE_OTHER, reg.claim(bob, "Shadow"));
        assertEquals(ClaimResult.REJECTED_ACTIVE_OTHER, reg.claim(bob, "shadow"));
    }

    @Test
    void claim_aliasMatchingOthersRealName_rejected() {
        reg.recordTrueName(bob, "RealBob");
        assertEquals(ClaimResult.REJECTED_REAL_NAME, reg.claim(alice, "RealBob"));
        assertEquals(ClaimResult.REJECTED_REAL_NAME, reg.claim(alice, "realbob"));
    }

    @Test
    void claim_yourOwnRealName_isAllowed() {
        reg.recordTrueName(alice, "Alice");
        // Reclaiming your own true name as an alias is permitted (realOwner == uuid)
        assertEquals(ClaimResult.ACCEPTED, reg.claim(alice, "Alice"));
    }

    // ---------- cooldown lifecycle ----------

    @Test
    void release_placesAliasOnCooldown_blocksOthers() {
        reg.claim(alice, "Shadow");
        reg.release(alice);
        assertFalse(reg.isDisguised(alice));
        assertEquals(ClaimResult.REJECTED_COOLDOWN_OTHER, reg.claim(bob, "Shadow"));
    }

    @Test
    void release_ownerCanResumeFromCooldown() {
        reg.claim(alice, "Shadow");
        reg.release(alice);
        assertEquals(ClaimResult.RESUMED_FROM_COOLDOWN, reg.claim(alice, "Shadow"));
        assertTrue(reg.isDisguised(alice));
        assertEquals("Shadow", reg.pseudonymOf(alice));
    }

    @Test
    void cooldown_otherPlayerCanStealAfterExpiry() {
        reg.setCooldownMs(0L);
        reg.claim(alice, "Shadow");
        reg.release(alice);
        // Cooldown expiry was now+0; after release a >0ms tick is enough, but pruning isn't required —
        // claim() lazily evicts an expired cooldown when a different player claims the same alias.
        assertEquals(ClaimResult.ACCEPTED, reg.claim(bob, "Shadow"));
        assertEquals(bob, reg.uuidOf("Shadow"));
    }

    @Test
    void pruneExpired_removesOldCooldownsOnly() {
        reg.setCooldownMs(0L);
        reg.claim(alice, "Shadow");
        reg.release(alice);
        reg.setCooldownMs(AliasRegistry.DEFAULT_COOLDOWN_MS);
        reg.claim(bob, "Phantom");
        reg.release(bob);

        int pruned = reg.pruneExpired();
        assertEquals(1, pruned);
        // Bob's fresh cooldown still blocks newcomers
        assertEquals(ClaimResult.REJECTED_COOLDOWN_OTHER, reg.claim(carol, "Phantom"));
        // Alice's expired cooldown does not
        assertEquals(ClaimResult.ACCEPTED, reg.claim(carol, "Shadow"));
    }

    // ---------- switching aliases ----------

    @Test
    void switchAlias_priorAliasGoesOnCooldown() {
        reg.claim(alice, "Shadow");
        assertEquals(ClaimResult.ACCEPTED, reg.claim(alice, "Phantom"));
        assertEquals("Phantom", reg.pseudonymOf(alice));
        // Old alias is now reserved on cooldown; another player can't grab it
        assertEquals(ClaimResult.REJECTED_COOLDOWN_OTHER, reg.claim(bob, "Shadow"));
    }

    // ---------- forceClearActive / kick-on-real-name-collision ----------

    @Test
    void forceClearActive_clearsActive_andReopensRealName() {
        reg.recordTrueName(alice, "Alice");
        reg.claim(alice, "Shadow");
        reg.forceClearActive(alice);
        assertFalse(reg.isDisguised(alice));
        assertNull(reg.pseudonymOf(alice));
        // No cooldown is created by forceClearActive
        assertEquals(ClaimResult.ACCEPTED, reg.claim(bob, "Shadow"));
        // And the open history interval should now be the real name
        List<NameInterval> hist = reg.historyOf(alice);
        NameInterval last = hist.get(hist.size() - 1);
        assertTrue(last.isOpen());
        assertTrue(last.realName());
        assertEquals("Alice", last.alias());
    }

    @Test
    void takeAliasesMatchingRealName_kicksColliders_exemptsSelf() {
        reg.claim(alice, "Newcomer");
        // Bob joins with real name "Newcomer" — Alice's alias must yield.
        List<UUID> kicked = reg.takeAliasesMatchingRealName("Newcomer", bob);
        assertEquals(List.of(alice), kicked);
        assertFalse(reg.isDisguised(alice));
        // If the only holder *is* the exempt UUID, nothing is kicked.
        reg.claim(bob, "Other");
        assertEquals(List.of(), reg.takeAliasesMatchingRealName("Other", bob));
    }

    // ---------- history intervals ----------

    @Test
    void recordTrueName_opensRealNameInterval_whenUndisguised() {
        reg.recordTrueName(alice, "Alice");
        List<NameInterval> hist = reg.historyOf(alice);
        assertEquals(1, hist.size());
        NameInterval ni = hist.get(0);
        assertEquals("Alice", ni.alias());
        assertTrue(ni.realName());
        assertTrue(ni.isOpen());
    }

    @Test
    void claim_closesRealNameInterval_andOpensAliasInterval() {
        reg.recordTrueName(alice, "Alice");
        tick(); // let the real-name interval age past SHORT_INTERVAL_DROP_MS so it's recorded, not dropped
        reg.claim(alice, "Shadow");
        List<NameInterval> hist = reg.historyOf(alice);
        assertEquals(2, hist.size());
        assertEquals("Alice", hist.get(0).alias());
        assertTrue(hist.get(0).realName());
        assertFalse(hist.get(0).isOpen());
        assertEquals("Shadow", hist.get(1).alias());
        assertFalse(hist.get(1).realName());
        assertTrue(hist.get(1).isOpen());
    }

    @Test
    void release_closesAliasInterval_andOpensRealNameInterval() {
        reg.recordTrueName(alice, "Alice");
        tick();
        reg.claim(alice, "Shadow");
        tick();
        reg.release(alice);
        List<NameInterval> hist = reg.historyOf(alice);
        // [Alice closed, Shadow closed, Alice open]
        assertEquals(3, hist.size());
        assertFalse(hist.get(1).isOpen());
        assertEquals("Shadow", hist.get(1).alias());
        assertTrue(hist.get(2).isOpen());
        assertTrue(hist.get(2).realName());
        assertEquals("Alice", hist.get(2).alias());
    }

    @Test
    void switchAlias_dropsZeroDurationRealNameInterval() {
        reg.recordTrueName(alice, "Alice");
        tick();
        reg.claim(alice, "Shadow");
        tick();
        // Immediate switch (no tick): the spurious real-name interval inside claim() should be suppressed.
        reg.claim(alice, "Phantom");
        List<NameInterval> hist = reg.historyOf(alice);
        // Expect: [Alice (real, closed), Shadow (alias, closed), Phantom (alias, open)] — no
        // intervening real-name interval between Shadow and Phantom.
        assertEquals(3, hist.size());
        assertEquals("Alice", hist.get(0).alias());
        assertEquals("Shadow", hist.get(1).alias());
        assertEquals("Phantom", hist.get(2).alias());
        assertTrue(hist.get(2).isOpen());
        // Verify no real-name interval is sandwiched between alias intervals
        assertFalse(hist.get(1).realName());
        assertFalse(hist.get(2).realName());
    }

    @Test
    void resumeFromCooldown_recordsAsNewAliasInterval() {
        reg.recordTrueName(alice, "Alice");
        tick();
        reg.claim(alice, "Shadow");
        tick();
        reg.release(alice);
        // No tick — the brief real-name reopen between release and resume should be dropped.
        reg.claim(alice, "Shadow"); // resume
        List<NameInterval> hist = reg.historyOf(alice);
        // [Alice closed, Shadow closed, Alice (closed by re-claim of Shadow), Shadow open]
        // The release-then-resume cycle within <100ms drops the spurious real-name reopen.
        assertTrue(hist.size() >= 3);
        NameInterval last = hist.get(hist.size() - 1);
        assertTrue(last.isOpen());
        assertEquals("Shadow", last.alias());
        assertFalse(last.realName());
    }

    // ---------- /whois & /namehistory query helpers ----------

    @Test
    void holdersOfAlias_returnsAllPastAndPresent_caseInsensitive() {
        // Alice held "Shadow", released; Bob now holds "Shadow"; Carol never did.
        reg.setCooldownMs(0L);
        reg.claim(alice, "Shadow");
        tick();
        reg.release(alice);
        tick();
        reg.claim(bob, "Shadow");

        Map<UUID, List<NameInterval>> holders = reg.holdersOfAlias("SHADOW");
        assertTrue(holders.containsKey(alice));
        assertTrue(holders.containsKey(bob));
        assertFalse(holders.containsKey(carol));
        // Each holder gets only their own intervals matching that alias
        for (NameInterval ni : holders.get(alice)) assertEquals("Shadow", ni.alias());
    }

    @Test
    void resolveTarget_priority_currentAlias_then_realName_then_history() {
        reg.recordTrueName(alice, "Alice");
        reg.recordTrueName(bob, "Bob");

        // Currently nobody holds "Shadow" — resolves to nobody
        assertNull(reg.resolveTarget("Shadow"));

        // History only: alice held it, then released
        reg.setCooldownMs(0L);
        reg.claim(alice, "Shadow");
        tick();
        reg.release(alice);
        tick();
        assertEquals(alice, reg.resolveTarget("Shadow"));

        // Active claim by bob takes priority over alice's older history
        reg.claim(bob, "Shadow");
        assertEquals(bob, reg.resolveTarget("Shadow"));

        // Real-name lookup
        assertEquals(alice, reg.resolveTarget("alice"));
        assertEquals(bob, reg.resolveTarget("BOB"));

        // Unknown
        assertNull(reg.resolveTarget("Nobody"));
        assertNull(reg.resolveTarget(""));
        assertNull(reg.resolveTarget(null));
    }

    // ---------- skin tracking & GC live set ----------

    @Test
    void activeSkin_setAndClear() {
        reg.claim(alice, "Shadow");
        reg.setActiveSkin(alice, "abc123", "slim");
        SkinRef s = reg.activeSkinOf(alice);
        assertNotNull(s);
        assertEquals("abc123", s.hash());
        assertEquals("slim", s.model());

        reg.setActiveSkin(alice, "", null); // blank hash clears
        assertNull(reg.activeSkinOf(alice));

        reg.setActiveSkin(alice, "def456", null);
        assertEquals("", reg.activeSkinOf(alice).model()); // null model normalized to ""
        reg.clearActiveSkin(alice);
        assertNull(reg.activeSkinOf(alice));
    }

    @Test
    void release_carriesSkinIntoCooldown_resumePreservesIt() {
        reg.claim(alice, "Shadow");
        reg.setActiveSkin(alice, "abc123", "slim");
        reg.release(alice);

        SkinRef cool = reg.cooldownSkinOf(alice);
        assertNotNull(cool);
        assertEquals("abc123", cool.hash());

        // Resume — active skin should be repopulated from the cooldown carry
        assertEquals(ClaimResult.RESUMED_FROM_COOLDOWN, reg.claim(alice, "Shadow"));
        SkinRef resumed = reg.activeSkinOf(alice);
        assertNotNull(resumed);
        assertEquals("abc123", resumed.hash());
    }

    @Test
    void liveSkinHashes_unionOfActiveAndCooldown() {
        reg.claim(alice, "Shadow");
        reg.setActiveSkin(alice, "hash-active", "slim");

        reg.claim(bob, "Phantom");
        reg.setActiveSkin(bob, "hash-cooldown", "default");
        reg.release(bob); // moves bob's skin into cooldown

        var live = reg.liveSkinHashes();
        assertTrue(live.contains("hash-active"));
        assertTrue(live.contains("hash-cooldown"));
        assertEquals(2, live.size());
    }

    // ---------- save / load round-trip ----------

    @Test
    void saveLoad_roundTrip_preservesAllState(@TempDir Path tmp) {
        reg.recordTrueName(alice, "Alice");
        reg.recordTrueName(bob, "Bob");
        reg.claim(alice, "Shadow");
        reg.setActiveSkin(alice, "hash-shadow", "slim");
        reg.claim(bob, "Phantom");
        reg.setActiveSkin(bob, "hash-phantom", "default");
        reg.release(bob); // bob → cooldown carrying skin

        Path file = tmp.resolve("alias-registry.json");
        reg.save(file);

        // Wipe & reload
        reg.clear();
        assertFalse(reg.isDisguised(alice));
        reg.load(file);

        assertTrue(reg.isDisguised(alice));
        assertEquals("Shadow", reg.pseudonymOf(alice));
        assertEquals("hash-shadow", reg.activeSkinOf(alice).hash());

        assertFalse(reg.isDisguised(bob));
        SkinRef bobCool = reg.cooldownSkinOf(bob);
        assertNotNull(bobCool);
        assertEquals("hash-phantom", bobCool.hash());

        // True names survived
        assertEquals(alice, reg.uuidByRealName("Alice"));
        assertEquals(bob, reg.uuidByRealName("bob"));

        // History survived
        assertFalse(reg.historyOf(alice).isEmpty());
        assertFalse(reg.historyOf(bob).isEmpty());
    }

    @Test
    void load_missingFile_isNoop(@TempDir Path tmp) {
        reg.claim(alice, "Shadow");
        reg.load(tmp.resolve("does-not-exist.json"));
        // load() calls clear() at entry — so existing state is wiped even when file is missing
        assertFalse(reg.isDisguised(alice));
    }

    @Test
    void activeAliasesSnapshot_returnsCurrentlyDisguisedAliases() {
        reg.claim(alice, "Shadow");
        reg.claim(bob, "Phantom");
        List<String> snap = reg.activeAliasesSnapshot();
        assertEquals(2, snap.size());
        assertTrue(snap.contains("Shadow"));
        assertTrue(snap.contains("Phantom"));
    }
}
