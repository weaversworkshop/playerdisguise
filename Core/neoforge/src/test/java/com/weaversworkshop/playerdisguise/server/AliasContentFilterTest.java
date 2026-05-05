package com.weaversworkshop.playerdisguise.server;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AliasContentFilterTest {

    @TempDir Path tmp;
    private AliasContentFilter filter;
    private Path file;

    @BeforeEach
    void setUp() {
        filter = AliasContentFilter.get();
        file = tmp.resolve("alias-blocklist.txt");
    }

    private void writeBlocklist(String contents) throws IOException {
        Files.writeString(file, contents, StandardCharsets.UTF_8);
        filter.setFile(file);
    }

    @Test
    void missingFileIsSeededAndEmpty() {
        filter.setFile(file);
        assertTrue(Files.exists(file), "expected the filter to seed an empty blocklist file");
        assertEquals(0, filter.size());
        assertFalse(filter.isBlocked("anything"));
    }

    @Test
    void emptyAndCommentLinesAreIgnored() throws IOException {
        writeBlocklist("# header comment\n\n   \n# another\n");
        assertEquals(0, filter.size());
        assertFalse(filter.isBlocked("nothing"));
    }

    @Test
    void exactMatchIsBlocked() throws IOException {
        writeBlocklist("badword\n");
        assertTrue(filter.isBlocked("badword"));
    }

    @Test
    void caseIsIgnored() throws IOException {
        writeBlocklist("badword\n");
        assertTrue(filter.isBlocked("BadWord"));
        assertTrue(filter.isBlocked("BADWORD"));
    }

    @Test
    void substringMatchIsBlocked() throws IOException {
        writeBlocklist("badword\n");
        assertTrue(filter.isBlocked("xxbadwordxx"));
        assertTrue(filter.isBlocked("prefix_badword"));
    }

    @Test
    void leetVariantsRequireExplicitEntries() throws IOException {
        writeBlocklist("badword\n");
        // Leet/punctuation variants are NOT auto-folded — admin must add them explicitly.
        assertFalse(filter.isBlocked("b4dw0rd"));
        assertFalse(filter.isBlocked("b.a.d.w.o.r.d"));
    }

    @Test
    void leetVariantMatchesWhenListedExplicitly() throws IOException {
        writeBlocklist("badword\nb4dw0rd\n");
        assertTrue(filter.isBlocked("b4dw0rd"));
        assertTrue(filter.isBlocked("B4DW0RD"));
    }

    @Test
    void multiWordEntryMatchesLiterally() throws IOException {
        writeBlocklist("bad phrase\n");
        assertTrue(filter.isBlocked("a bad phrase here"));
        assertTrue(filter.isBlocked("BAD PHRASE"));
        assertFalse(filter.isBlocked("badphrase"));
    }

    @Test
    void unrelatedAliasIsNotBlocked() throws IOException {
        writeBlocklist("badword\nforbidden\nnaughty\n");
        assertFalse(filter.isBlocked("Steve"));
        assertFalse(filter.isBlocked("CoolPlayer42"));
        assertFalse(filter.isBlocked("Alex"));
    }

    @Test
    void emptyBlocklistBlocksNothing() throws IOException {
        writeBlocklist("# only comments\n");
        assertFalse(filter.isBlocked("badword"));
        assertFalse(filter.isBlocked(""));
    }

    @Test
    void blankAndNullInputAreNotBlocked() throws IOException {
        writeBlocklist("badword\n");
        assertFalse(filter.isBlocked(null));
        assertFalse(filter.isBlocked(""));
        assertFalse(filter.isBlocked("   "));
    }

    @Test
    void multipleEntriesAllMatch() throws IOException {
        writeBlocklist("badword\nforbidden\nnaughty\n");
        assertTrue(filter.isBlocked("badword"));
        assertTrue(filter.isBlocked("forbidden"));
        assertTrue(filter.isBlocked("naughty"));
        assertEquals(3, filter.size());
    }

    @Test
    void reloadPicksUpFileChanges() throws IOException {
        writeBlocklist("badword\n");
        assertTrue(filter.isBlocked("badword"));
        assertFalse(filter.isBlocked("forbidden"));

        Files.writeString(file, "forbidden\n", StandardCharsets.UTF_8);
        filter.load();

        assertFalse(filter.isBlocked("badword"));
        assertTrue(filter.isBlocked("forbidden"));
    }

    @Test
    void normalizeIsLowercaseOnly() {
        assertEquals("hello", AliasContentFilter.normalize("HELLO"));
        assertEquals("h.e.l.l.o!", AliasContentFilter.normalize("H.E.L.L.O!"));
        assertEquals("abc123", AliasContentFilter.normalize("ABC123"));
    }
}
