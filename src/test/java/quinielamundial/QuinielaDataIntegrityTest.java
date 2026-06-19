package quinielamundial;

import org.junit.jupiter.api.Test;
import quinielamundial.domain.Member;
import quinielamundial.service.QuinielaService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Data integrity tests: verify that domain operations still work correctly
 * despite the AJAX/infrastructure changes. These tests ensure no data
 * is lost or corrupted during create/join/predict/score flows.
 */
class QuinielaDataIntegrityTest {

    private final QuinielaService service = new QuinielaService();

    // ── Group lifecycle ──

    @Test
    void createGroupStoresAllMembers() {
        var group = service.createGroup("Test", "Ana");
        assertEquals(1, group.members().size(), "Creator must be in members");
        assertTrue(group.members().containsKey("Ana"), "Creator must be findable by name");
    }

    @Test
    void joinGroupAddsMember() {
        var group = service.createGroup("Test", "Ana");
        var joined = group.join("Luis");
        assertNotNull(joined, "join() must return the new member");
        assertEquals(2, group.members().size(), "Group must have 2 members after join");
        assertTrue(group.members().containsKey("Luis"), "Joined member must exist");
    }

    @Test
    void joinGroupByCode() {
        var group = service.createGroup("Test", "Ana");
        var code = group.code();

        // Simulate what QuinielaApp.join handler does
        var found = service.group(code);
        assertNotNull(found, "Must find group by code");
        var joined = found.join("Luis");
        assertNotNull(joined);
        assertEquals(2, found.members().size());
    }

    @Test
    void invalidJoinCodeReturnsNull() {
        assertNull(service.joinGroup("INVALID", "Luis"),
            "Invalid code must return null (same as QuinielaApp logic)");
    }

    @Test
    void duplicateNameThrows() {
        var group = service.createGroup("Test", "Ana");
        assertThrows(IllegalArgumentException.class, () -> group.join("Ana"),
            "Duplicate member name must throw");
    }

    @Test
    void removeMemberPreservesOtherMembers() {
        var group = service.createGroup("Test", "Admin");
        group.join("Luis");
        group.join("Pedro");
        assertEquals(3, group.members().size());

        group.removeMember("Luis");
        assertEquals(2, group.members().size(), "Member must be removed");
        assertFalse(group.members().containsKey("Luis"), "Removed member must not exist");
        assertTrue(group.members().containsKey("Admin"), "Creator must be preserved");
        assertTrue(group.members().containsKey("Pedro"), "Other members must be preserved");
    }

    @Test
    void removeCreatorThrows() {
        var group = service.createGroup("Test", "Admin");
        assertThrows(IllegalArgumentException.class, () -> group.removeMember("Admin"),
            "Cannot remove the group creator");
    }

    // ── Champion betting (NO date dependency) ──

    @Test
    void setChampionBetUpdatesMember() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();

        group.setChampionBet(ana.token(), "Argentina");
        assertEquals("Argentina", ana.championBet(), "Champion bet must be stored on member");
    }

    @Test
    void championBetScoresTenPoints() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();

        group.setChampionBet(ana.token(), "Argentina");
        var lb = group.leaderboard("Argentina");
        var entry = lb.stream().filter(e -> e.member().name().equals("Ana")).findFirst().get();

        assertEquals(10, entry.score().totalPoints(), "Champion hit must give 10 points");
        assertTrue(entry.score().championHit(), "championHit flag must be true");
    }

    @Test
    void championBetWrongGivesZeroPoints() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();

        group.setChampionBet(ana.token(), "Brasil");
        var lb = group.leaderboard("Argentina");
        var entry = lb.stream().filter(e -> e.member().name().equals("Ana")).findFirst().get();

        assertEquals(0, entry.score().totalPoints(), "Wrong champion must give 0 points");
        assertFalse(entry.score().championHit(), "championHit flag must be false");
    }

    // ── Token management ──

    @Test
    void tokenIsUniquePerMember() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();
        var luis = group.join("Luis");

        assertNotNull(ana.token());
        assertNotNull(luis.token());
        assertNotEquals(ana.token(), luis.token(), "Each member must have unique token");
        assertEquals(16, ana.token().length(), "Token must be 16 chars");
    }

    @Test
    void requireByTokenFindsCorrectMember() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();
        var luis = group.join("Luis");

        assertSame(ana, group.requireByToken(ana.token()));
        assertSame(luis, group.requireByToken(luis.token()));
        assertThrows(IllegalArgumentException.class, () -> group.requireByToken("bogus"));
    }

    @Test
    void leaderboardDoesNotModifyMembers() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();
        var luis = group.join("Luis");

        var beforeSize = group.members().size();
        var lb = group.leaderboard(null);
        var afterSize = group.members().size();

        assertEquals(beforeSize, afterSize, "leaderboard() must not modify members");
        assertEquals(2, lb.size(), "leaderboard must return all members");
    }

    // ── /join/ code logic (data-free) ──

    @Test
    void groupLookupByCodeIsCaseInsensitive() {
        var group = service.createGroup("Test", "Ana");
        var code = group.code();

        // The QuinielaService.group() uppercases the code
        assertNotNull(service.group(code.toLowerCase()),
            "Group lookup must be case-insensitive (as QuinielaApp uppercases)");
        assertNotNull(service.group(code.toUpperCase()),
            "Group lookup must work with uppercase");
    }

    @Test
    void nonexistentGroupCodeReturnsNull() {
        assertNull(service.group("ZZZZZZZZ"),
            "Non-existent code must return null (as QuinielaApp checks)");
    }
}
