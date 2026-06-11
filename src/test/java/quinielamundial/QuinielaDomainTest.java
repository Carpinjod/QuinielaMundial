package quinielamundial;

import org.junit.jupiter.api.Test;
import quinielamundial.domain.Prediction;
import quinielamundial.persistence.StateStore;
import quinielamundial.service.QuinielaService;
import quinielamundial.web.HtmlRenderer;

import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class QuinielaDomainTest {

    @Test
    void scoringRules() {
        var service = new QuinielaService();
        var group = service.createGroup("Grupo", "Ana");
        var ana = group.creator();
        var matches = group.matches();

        // ── Exact score hit: 3pts ──
        var m1 = matches.get(0);
        group.submitPrediction(ana.token(), m1.id(), 2, 1);
        group.setStarMatch(ana.token(), m1.jornada(), m1.id());
        group.registerResult(m1.id(), 2, 1); // exact match

        // ── Outcome-only hit: 1pt ──
        var luis = group.join("Luis");
        var m2 = matches.get(1);
        group.submitPrediction(luis.token(), m2.id(), 1, 0); // predicts 1-0
        group.registerResult(m2.id(), 2, 1); // actual 2-1 → home win correct

        // ── Wrong prediction: 0pts ──
        var pedro = group.join("Pedro");
        var m3 = matches.get(2);
        group.submitPrediction(pedro.token(), m3.id(), 0, 3); // predicts 0-3 (away win)
        group.registerResult(m3.id(), 2, 1); // actual 2-1 (home win) → wrong

        // Ana: exact hit + star + champion
        group.setChampionBet(ana.token(), "Argentina", false);
        var aEntry = group.leaderboard("Argentina").stream()
            .filter(e -> e.member().name().equals("Ana")).findFirst().get();
        assertEquals(16, aEntry.score().totalPoints()); // 3×2 (star) + 10 (champion)
        assertEquals(1, aEntry.score().exactHits());
        assertEquals(0, aEntry.score().outcomeHits());

        // Luis: outcome only
        var lEntry = group.leaderboard("Argentina").stream()
            .filter(e -> e.member().name().equals("Luis")).findFirst().get();
        assertEquals(1, lEntry.score().totalPoints());
        assertEquals(0, lEntry.score().exactHits());
        assertEquals(1, lEntry.score().outcomeHits());

        // Pedro: wrong
        var pEntry = group.leaderboard("Argentina").stream()
            .filter(e -> e.member().name().equals("Pedro")).findFirst().get();
        assertEquals(0, pEntry.score().totalPoints());
        assertEquals(0, pEntry.score().exactHits());
        assertEquals(0, pEntry.score().outcomeHits());
    }

    @Test
    void rankingTiebreaks() {
        var service = new QuinielaService();
        var group = service.createGroup("Grupo", "Ana");
        var ana = group.creator();
        var luis = group.join("Luis");
        var match = group.matches().get(0);

        // Both predict the SAME exact score → same points → same rank
        group.submitPrediction(ana.token(), match.id(), 1, 0);
        group.submitPrediction(luis.token(), match.id(), 1, 0);
        group.registerResult(match.id(), 1, 0);

        var ranking = group.leaderboard("Argentina");
        assertEquals(1, ranking.get(0).rank());
        assertEquals(1, ranking.get(1).rank());
    }

    @Test
    void joinAndLockRules() {
        var service = new QuinielaService();
        var group = service.createGroup("Grupo", "Ana");
        var ana = group.creator();
        assertEquals(group.code(), service.joinGroup(group.code(), "Luis").code());

        // Can't join with same name
        assertThrows(IllegalArgumentException.class, () -> group.join("Ana"));

        // Invalid token rejected
        assertThrows(IllegalArgumentException.class, () -> group.submitPrediction("invalid-token", 1, 0, 0));

        var luis = group.members().get("Luis");
        var match = group.matches().get(0);
        group.submitPrediction(luis.token(), match.id(), 0, 2); // away win
        assertThrows(IllegalArgumentException.class, () -> group.submitPrediction(luis.token(), match.id(), -1, 0)); // negative goals
        group.registerResult(match.id(), 1, 0); // home wins

        assertThrows(IllegalStateException.class, () -> group.submitPrediction(luis.token(), match.id(), 1, 2)); // match finished
        assertThrows(IllegalStateException.class, () -> group.setChampionBet(luis.token(), "Brasil", true));
    }

    @Test
    void visibilityAndLockRules() {
        var service = new QuinielaService();
        var group = service.createGroup("Grupo", "Ana");
        var ana = group.creator();
        var luis = group.join("Luis");
        var match = group.matches().get(0);
        group.submitPrediction(ana.token(), match.id(), 2, 0);
        group.submitPrediction(luis.token(), match.id(), 0, 3);

        var html = new HtmlRenderer().groupPage(group, ana, service.candidates(), service.tournamentChampion(), service.tournamentStarted(), 1, null);
        assertTrue(html.contains("🔒 oculto"));

        assertFalse(group.members().get("Luis").predictions().get(match.id()).star());
        group.registerResult(match.id(), 1, 0);
        assertThrows(IllegalStateException.class, () -> group.submitPrediction(luis.token(), match.id(), 2, 0));
    }

    @Test
    void championManagement() {
        var service = new QuinielaService();
        service.setTournamentChampion("Brasil");
        assertEquals("Brasil", service.tournamentChampion());
        assertThrows(IllegalArgumentException.class, () -> service.setTournamentChampion(""));

        var group = service.createGroup("Grupo", "Ana");
        var ana = group.creator();
        var html = new HtmlRenderer().groupPage(group, ana, service.candidates(), service.tournamentChampion(), service.tournamentStarted(), 1, null);
        assertTrue(html.contains("Campeón actual:"));
    }

    @Test
    void persistenceRoundTrip() throws Exception {
        var temp = Files.createTempFile("quiniela-state", ".bin");
        var store = new StateStore(temp);
        var service = new QuinielaService();
        service.createGroup("Grupo", "Ana");
        service.setTournamentChampion("Brasil");

        store.save(service.groups(), service.tournamentChampion());
        var snapshot = store.load();

        assertEquals(1, snapshot.groups().size());
        assertEquals("Brasil", snapshot.champion());
    }

    @Test
    void predictionValidation() {
        assertDoesNotThrow(() -> new Prediction(0, 0, false));
        assertDoesNotThrow(() -> new Prediction(10, 5, false));
        assertThrows(IllegalArgumentException.class, () -> new Prediction(-1, 0, false));
        assertThrows(IllegalArgumentException.class, () -> new Prediction(0, -1, false));
    }

    @Test
    void outcomeMethod() {
        assertEquals("1", new Prediction(3, 1, false).outcome());
        assertEquals("X", new Prediction(2, 2, false).outcome());
        assertEquals("2", new Prediction(0, 4, false).outcome());
        assertEquals("X", new Prediction(0, 0, false).outcome());
    }

    @Test
    void tokenGeneration() {
        var group = new QuinielaService().createGroup("Grupo", "Ana");
        var ana = group.creator();
        assertNotNull(ana.token());
        assertFalse(ana.token().isBlank());
        assertEquals(16, ana.token().length());

        var luis = group.join("Luis");
        assertNotNull(luis.token());
        assertNotEquals(ana.token(), luis.token());

        // requireByToken works
        assertSame(ana, group.requireByToken(ana.token()));
        assertSame(luis, group.requireByToken(luis.token()));
        assertThrows(IllegalArgumentException.class, () -> group.requireByToken("bogus"));
    }
}
