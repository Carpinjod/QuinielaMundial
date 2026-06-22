package quinielamundial;

import org.junit.jupiter.api.Test;
import quinielamundial.service.BracketResolver;
import quinielamundial.service.QuinielaService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BracketResolver's FIFA-standard H2H group standing tiebreakers.
 *
 * FIFA tiebreaker order:
 * 1. Points
 * 2. Head-to-head points (among tied teams)
 * 3. Head-to-head goal difference
 * 4. Head-to-head goals scored
 * 5. If still tied after 1-4, re-apply 2-4 among remaining tied teams
 * 6. Overall goal difference
 * 7. Overall goals scored
 */
class BracketResolverTest {

    // Group A matches (from WorldCupSchedule):
    //   M1 (J1): Mexico vs South Africa
    //   M2 (J1): South Korea vs Czechia
    //   M3 (J2): Czechia vs South Africa
    //   M4 (J2): Mexico vs South Korea
    //   M5 (J3): Czechia vs Mexico
    //   M6 (J3): South Africa vs South Korea

    @Test
    void noTieByPoints() {
        var service = new QuinielaService();
        var group = service.createGroup("Test", "Admin");
        registerGroupResults(group,
            3, 0,   // M1: Mexico beats SA
            0, 1,   // M2: Czechia beats SK
            2, 0,   // M3: Czechia beats SA
            3, 0,   // M4: Mexico beats SK
            2, 1,   // M5: Czechia beats Mexico
            1, 0    // M6: SA beats SK
        );

        var standings = BracketResolver.calculateAllStandings(group);
        var groupA = standings.get("A");

        assertNotNull(groupA);
        assertEquals(4, groupA.size());
        assertEquals("Czechia", groupA.get(0).team());   // 9pts (3+3+3)
        assertEquals("Mexico",  groupA.get(1).team());    // 6pts (3+3+0)
        assertEquals("South Africa", groupA.get(2).team());// 3pts (0+0+3)   -- wait, SA: 0+0+3=3pts
        assertEquals("South Korea",  groupA.get(3).team());// 0pts

        assertEquals(9, groupA.get(0).points());
        assertEquals(6, groupA.get(1).points());
        assertEquals(3, groupA.get(2).points());
        assertEquals(0, groupA.get(3).points());
    }

    @Test
    void h2hReversesGoalDifference() {
        // Mexico and Czechia tied on 6pts. Mexico has better overall GD (+8 vs +1),
        // but Czechia won the H2H match (1-0). H2H should override GD.
        var service = new QuinielaService();
        var group = service.createGroup("Test", "Admin");
        registerGroupResults(group,
            5, 0,   // M1: Mexico 5-0 SA → MEX +5GD
            0, 1,   // M2: SK 0-1 Czechia → CZ +1GD
            0, 1,   // M3: Czechia 0-1 SA → CZ -1GD; SA +1GD
            4, 0,   // M4: Mexico 4-0 SK → MEX +4GD
            1, 0,   // M5: CZECHIA 1-0 MEXICO → H2H WINNER!
            0, 0    // M6: SA 0-0 SK
        );

        var standings = BracketResolver.calculateAllStandings(group);
        var groupA = standings.get("A");

        assertNotNull(groupA);
        assertEquals(4, groupA.size());

        // Points: MEX 6, CZ 6, SA 4, SK 1
        assertEquals(6, groupA.get(0).points());
        assertEquals(6, groupA.get(1).points());
        assertEquals(4, groupA.get(2).points());
        assertEquals(1, groupA.get(3).points());

        // H2H: Czechia beat Mexico 1-0 → CZ 1st, MEX 2nd
        assertEquals("Czechia", groupA.get(0).team());
        assertEquals("Mexico", groupA.get(1).team());
        assertEquals("South Africa", groupA.get(2).team());
        assertEquals("South Korea", groupA.get(3).team());
    }

    @Test
    void threeWayTieWithRecursiveH2H() {
        // Mexico, Czechia, and South Africa ALL tied on 6pts (SK last with 0).
        //
        // H2H mini-league:
        //   MEX: W vs SA (3pts, GF1 GA0), L vs CZ (0pts, GF0 GA1) → 3pts, GD0, GF1
        //   CZ:  L vs SA (0pts, GF1 GA2), W vs MEX (3pts, GF1 GA0) → 3pts, GD0, GF2
        //   SA:  L vs MEX (0pts, GF0 GA1), W vs CZ (3pts, GF2 GA1) → 3pts, GD0, GF2
        //
        // H2H GF: MEX (1) eliminated. CZ and SA still tied on GF (2 each).
        //
        // RECURSE on {CZ, SA}: SA beat CZ 2-1 → SA 1st, CZ 2nd.
        //
        // Final: SA 1st, CZ 2nd, MEX 3rd, SK 4th
        var service = new QuinielaService();
        var group = service.createGroup("Test", "Admin");
        registerGroupResults(group,
            1, 0,   // M1: Mexico 1-0 SA
            0, 2,   // M2: SK 0-2 Czechia
            1, 2,   // M3: CZECHIA 1-2 SOUTH AFRICA
            3, 0,   // M4: Mexico 3-0 SK
            1, 0,   // M5: Czechia 1-0 Mexico
            2, 1    // M6: SA 2-1 SK
        );

        var standings = BracketResolver.calculateAllStandings(group);
        var groupA = standings.get("A");

        assertNotNull(groupA);
        assertEquals(4, groupA.size());

        // All three tied on 6pts
        assertEquals(6, groupA.get(0).points());
        assertEquals(6, groupA.get(1).points());
        assertEquals(6, groupA.get(2).points());
        assertEquals(0, groupA.get(3).points());

        // SA won the recursive H2H, CZ 2nd, MEX 3rd
        assertEquals("South Africa", groupA.get(0).team());
        assertEquals("Czechia",      groupA.get(1).team());
        assertEquals("Mexico",       groupA.get(2).team());
        assertEquals("South Korea",  groupA.get(3).team());
    }

    @Test
    void twoWayTieNoH2hMatch_usesGoalDiff() {
        // Scenario: Two teams tied on points with NO H2H match yet.
        // Argentina beat Algeria 3-0 (3pts, +3 GD)
        // Austria beat Jordan 3-1 (3pts, +2 GD)
        // No Argentina vs Austria played yet → no H2H → overall GD decides.
        // Argentina (+3) should be 1st, Austria (+2) 2nd.
        var service = new QuinielaService();
        var group = service.createGroup("Test", "Admin");
        group.registerResult(55, 3, 0);  // Argentina 3-0 Algeria
        group.registerResult(56, 3, 1);  // Austria 3-1 Jordan

        var standings = BracketResolver.calculateAllStandings(group);
        var groupJ = standings.get("J");

        assertNotNull(groupJ);
        assertEquals(4, groupJ.size());

        // Both tied on 3pts
        assertEquals(3, groupJ.get(0).points());
        assertEquals(3, groupJ.get(1).points());

        // Argentina has better GD (+3 vs +2) → 1st
        assertEquals("Argentina", groupJ.get(0).team());
        assertEquals("Austria",   groupJ.get(1).team());
        // Jordan (GD=-2) beats Algeria (GD=-3) on GD
        assertEquals("Jordan",    groupJ.get(2).team());
        assertEquals("Algeria",   groupJ.get(3).team());

        assertEquals(3, groupJ.get(0).goalDiff());
        assertEquals(2, groupJ.get(1).goalDiff());
        assertEquals(-2, groupJ.get(2).goalDiff());
        assertEquals(-3, groupJ.get(3).goalDiff());
    }

    /** Helper: register all 6 Group A results in order M1–M6. */
    private static void registerGroupResults(quinielamundial.domain.Group group,
                                             int... scores) {
        for (int i = 0; i < scores.length; i += 2) {
            group.registerResult(i / 2 + 1, scores[i], scores[i + 1]);
        }
    }
}
