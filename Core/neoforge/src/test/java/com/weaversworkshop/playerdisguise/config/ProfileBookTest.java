package com.weaversworkshop.playerdisguise.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests focus on slot math: index 0 is "real identity", indices 1..N map to stored profile slots
 * (so {@code activeIndex - 1} indexes {@code profiles}). Tests deliberately avoid calling
 * {@link Profile#resolvedModel()} so the JVM never needs Minecraft's PlayerSkin loaded at runtime.
 */
class ProfileBookTest {

    private static Profile p(String name) {
        return new Profile(name, null, null, null);
    }

    private static Profile pSkin(String name, String file) {
        return new Profile(name, file, "hash-" + name, "default");
    }

    @Test
    void empty_constants() {
        assertEquals(0, ProfileBook.EMPTY.profiles().size());
        assertEquals(0, ProfileBook.EMPTY.activeIndex());
        assertTrue(ProfileBook.EMPTY.isRealActive());
        assertEquals(1, ProfileBook.EMPTY.totalSlots()); // real-identity slot is always present
        assertNull(ProfileBook.EMPTY.activeStored());
    }

    @Test
    void canonicalConstructor_defensiveCopiesProfilesList() {
        var mutable = new java.util.ArrayList<>(List.of(p("A"), p("B")));
        ProfileBook book = new ProfileBook(mutable, 0);
        mutable.add(p("C"));
        assertEquals(2, book.profiles().size()); // mutation didn't leak in
        assertThrows(UnsupportedOperationException.class, () -> book.profiles().add(p("D")));
    }

    @Test
    void canonicalConstructor_nullProfilesNormalizedToEmpty() {
        ProfileBook book = new ProfileBook(null, 0);
        assertEquals(0, book.profiles().size());
    }

    @Test
    void canonicalConstructor_invalidActiveIndex_normalizedToZero() {
        ProfileBook book2 = new ProfileBook(List.of(p("A")), -1);
        assertEquals(0, book2.activeIndex());

        ProfileBook book3 = new ProfileBook(List.of(p("A")), 99);
        assertEquals(0, book3.activeIndex());

        // activeIndex == size (i.e., the last stored profile) is valid
        ProfileBook book4 = new ProfileBook(List.of(p("A"), p("B")), 2);
        assertEquals(2, book4.activeIndex());
    }

    @Test
    void isRealActive_andActiveStored() {
        Profile a = p("A"), b = p("B");
        ProfileBook book = new ProfileBook(List.of(a, b), 0);
        assertTrue(book.isRealActive());
        assertNull(book.activeStored());

        ProfileBook book1 = book.withActiveIndex(1);
        assertFalse(book1.isRealActive());
        assertSame(a, book1.activeStored());

        ProfileBook book2 = book.withActiveIndex(2);
        assertSame(b, book2.activeStored());
    }

    @Test
    void totalSlots_alwaysProfilesPlusOne() {
        assertEquals(1, new ProfileBook(List.of(), 0).totalSlots());
        assertEquals(2, new ProfileBook(List.of(p("A")), 0).totalSlots());
        assertEquals(4, new ProfileBook(List.of(p("A"), p("B"), p("C")), 1).totalSlots());
    }

    @Test
    void withActiveIndex_returnsNewBook_sameProfilesList() {
        ProfileBook book = new ProfileBook(List.of(p("A"), p("B")), 0);
        ProfileBook updated = book.withActiveIndex(2);
        assertNotSame(book, updated);
        assertEquals(2, updated.activeIndex());
        // Underlying profiles list content is preserved (canonical constructor copies, so identity may differ)
        assertEquals(book.profiles(), updated.profiles());
    }

    @Test
    void addProfile_appendsAndActivatesNewSlot() {
        ProfileBook book = new ProfileBook(List.of(p("A")), 0);
        ProfileBook after = book.addProfile(pSkin("B", "b.png"));
        assertEquals(2, after.profiles().size());
        assertEquals("B", after.profiles().get(1).name());
        // Newly added profile becomes active (activeIndex = new size)
        assertEquals(2, after.activeIndex());
        assertSame(after.profiles().get(1), after.activeStored());
    }

    @Test
    void replaceProfile_keepsActiveIndex() {
        Profile a = p("A"), b = p("B"), bPrime = pSkin("B'", "b.png");
        ProfileBook book = new ProfileBook(List.of(a, b), 2); // B is active

        ProfileBook after = book.replaceProfile(1, bPrime);
        assertEquals(2, after.profiles().size());
        assertSame(bPrime, after.profiles().get(1));
        assertEquals(2, after.activeIndex()); // active slot still slot 2 (now B')
        assertSame(bPrime, after.activeStored());
    }

    @Test
    void removeProfile_removingActiveSlot_revertsToReal() {
        Profile a = p("A"), b = p("B");
        ProfileBook book = new ProfileBook(List.of(a, b), 1); // A active (storedIndex 0 → activeIndex 1)
        ProfileBook after = book.removeProfile(0);
        assertEquals(1, after.profiles().size());
        assertSame(b, after.profiles().get(0));
        assertEquals(0, after.activeIndex()); // reverted to real identity
        assertTrue(after.isRealActive());
    }

    @Test
    void removeProfile_removingEarlierSlot_shiftsActiveDown() {
        Profile a = p("A"), b = p("B"), c = p("C");
        ProfileBook book = new ProfileBook(List.of(a, b, c), 3); // C is active (storedIndex 2 → activeIndex 3)
        ProfileBook after = book.removeProfile(0); // remove A
        assertEquals(List.of(b, c), after.profiles());
        // C used to be at activeIndex 3; with A gone, C is now at activeIndex 2
        assertEquals(2, after.activeIndex());
        assertSame(c, after.activeStored());
    }

    @Test
    void removeProfile_removingLaterSlot_keepsActiveIndex() {
        Profile a = p("A"), b = p("B"), c = p("C");
        ProfileBook book = new ProfileBook(List.of(a, b, c), 1); // A is active
        ProfileBook after = book.removeProfile(2); // remove C
        assertEquals(List.of(a, b), after.profiles());
        assertEquals(1, after.activeIndex()); // unchanged: A still at slot 1
        assertSame(a, after.activeStored());
    }

    @Test
    void removeProfile_whileRealActive_keepsRealActive() {
        ProfileBook book = new ProfileBook(List.of(p("A"), p("B")), 0);
        ProfileBook after = book.removeProfile(0);
        assertEquals(0, after.activeIndex());
        assertTrue(after.isRealActive());
    }

    @Test
    void profile_hasSkin_basicChecks() {
        // Doesn't touch resolvedModel(), so PlayerSkin isn't loaded.
        assertFalse(p("Alice").hasSkin());
        assertFalse(new Profile("Alice", "", null, null).hasSkin());
        assertFalse(new Profile("Alice", "   ", null, null).hasSkin());
        assertTrue(pSkin("Alice", "alice.png").hasSkin());
    }
}
