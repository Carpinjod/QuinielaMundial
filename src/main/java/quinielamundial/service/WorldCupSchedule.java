package quinielamundial.service;

import quinielamundial.domain.Match;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Full 2026 World Cup schedule: 72 group-stage matches + 32 knockout matches.
 *
 * 12 groups (A-L), each playing 6 matches over 3 jornadas.
 * Knockout: R32 (16 matches), R16 (8), QF (4), SF (2), 3rd place, Final.
 *
 * Data sourced from FIFA.com, verified via MARCA and Olympics.com.
 * All kickoff times in UTC.
 */
public class WorldCupSchedule {
    private WorldCupSchedule() {}

    // ═══════════════════════════════════════════
    //  GROUP STAGE (matches 1–72)
    // ═══════════════════════════════════════════

    // ── Group A: Mexico, South Africa, South Korea, Czechia ──
    public static List<Match> groupAMatches() {
        return List.of(
            new Match(1, 1, "Mexico", "South Africa", Instant.parse("2026-06-11T19:00:00Z")),
            new Match(2, 1, "South Korea", "Czechia", Instant.parse("2026-06-12T02:00:00Z")),
            new Match(3, 2, "Czechia", "South Africa", Instant.parse("2026-06-18T16:00:00Z")),
            new Match(4, 2, "Mexico", "South Korea", Instant.parse("2026-06-19T01:00:00Z")),
            new Match(5, 3, "Czechia", "Mexico", Instant.parse("2026-06-25T01:00:00Z")),
            new Match(6, 3, "South Africa", "South Korea", Instant.parse("2026-06-25T01:00:00Z"))
        );
    }

    // ── Group B: Canada, Bosnia and Herzegovina, Qatar, Switzerland ──
    public static List<Match> groupBMatches() {
        return List.of(
            new Match(7, 1, "Canada", "Bosnia and Herzegovina", Instant.parse("2026-06-12T19:00:00Z")),
            new Match(8, 1, "Qatar", "Switzerland", Instant.parse("2026-06-13T19:00:00Z")),
            new Match(9, 2, "Switzerland", "Bosnia and Herzegovina", Instant.parse("2026-06-18T19:00:00Z")),
            new Match(10, 2, "Canada", "Qatar", Instant.parse("2026-06-18T22:00:00Z")),
            new Match(11, 3, "Switzerland", "Canada", Instant.parse("2026-06-24T19:00:00Z")),
            new Match(12, 3, "Bosnia and Herzegovina", "Qatar", Instant.parse("2026-06-24T19:00:00Z"))
        );
    }

    // ── Group C: Brazil, Morocco, Haiti, Scotland ──
    public static List<Match> groupCMatches() {
        return List.of(
            new Match(13, 1, "Brazil", "Morocco", Instant.parse("2026-06-13T22:00:00Z")),
            new Match(14, 1, "Haiti", "Scotland", Instant.parse("2026-06-14T01:00:00Z")),
            new Match(15, 2, "Scotland", "Morocco", Instant.parse("2026-06-19T22:00:00Z")),
            new Match(16, 2, "Brazil", "Haiti", Instant.parse("2026-06-20T00:30:00Z")),
            new Match(17, 3, "Scotland", "Brazil", Instant.parse("2026-06-24T22:00:00Z")),
            new Match(18, 3, "Morocco", "Haiti", Instant.parse("2026-06-24T22:00:00Z"))
        );
    }

    // ── Group D: USA, Paraguay, Australia, Türkiye ──
    public static List<Match> groupDMatches() {
        return List.of(
            new Match(19, 1, "USA", "Paraguay", Instant.parse("2026-06-13T01:00:00Z")),
            new Match(20, 1, "Australia", "Türkiye", Instant.parse("2026-06-14T04:00:00Z")),
            new Match(21, 2, "USA", "Australia", Instant.parse("2026-06-19T19:00:00Z")),
            new Match(22, 2, "Türkiye", "Paraguay", Instant.parse("2026-06-20T04:00:00Z")),
            new Match(23, 3, "Türkiye", "USA", Instant.parse("2026-06-26T02:00:00Z")),
            new Match(24, 3, "Paraguay", "Australia", Instant.parse("2026-06-26T02:00:00Z"))
        );
    }

    // ── Group E: Germany, Curaçao, Ivory Coast, Ecuador ──
    public static List<Match> groupEMatches() {
        return List.of(
            new Match(25, 1, "Germany", "Curaçao", Instant.parse("2026-06-14T17:00:00Z")),
            new Match(26, 1, "Ivory Coast", "Ecuador", Instant.parse("2026-06-14T23:00:00Z")),
            new Match(27, 2, "Germany", "Ivory Coast", Instant.parse("2026-06-20T20:00:00Z")),
            new Match(28, 2, "Ecuador", "Curaçao", Instant.parse("2026-06-21T00:00:00Z")),
            new Match(29, 3, "Curaçao", "Ivory Coast", Instant.parse("2026-06-25T20:00:00Z")),
            new Match(30, 3, "Ecuador", "Germany", Instant.parse("2026-06-25T20:00:00Z"))
        );
    }

    // ── Group F: Netherlands, Japan, Sweden, Tunisia ──
    public static List<Match> groupFMatches() {
        return List.of(
            new Match(31, 1, "Netherlands", "Japan", Instant.parse("2026-06-14T20:00:00Z")),
            new Match(32, 1, "Sweden", "Tunisia", Instant.parse("2026-06-15T02:00:00Z")),
            new Match(33, 2, "Netherlands", "Sweden", Instant.parse("2026-06-20T17:00:00Z")),
            new Match(34, 2, "Tunisia", "Japan", Instant.parse("2026-06-21T04:00:00Z")),
            new Match(35, 3, "Japan", "Sweden", Instant.parse("2026-06-25T23:00:00Z")),
            new Match(36, 3, "Tunisia", "Netherlands", Instant.parse("2026-06-25T23:00:00Z"))
        );
    }

    // ── Group G: Belgium, Egypt, Iran, New Zealand ──
    public static List<Match> groupGMatches() {
        return List.of(
            new Match(37, 1, "Belgium", "Egypt", Instant.parse("2026-06-15T19:00:00Z")),
            new Match(38, 1, "Iran", "New Zealand", Instant.parse("2026-06-16T01:00:00Z")),
            new Match(39, 2, "Belgium", "Iran", Instant.parse("2026-06-21T19:00:00Z")),
            new Match(40, 2, "New Zealand", "Egypt", Instant.parse("2026-06-22T01:00:00Z")),
            new Match(41, 3, "Egypt", "Iran", Instant.parse("2026-06-27T03:00:00Z")),
            new Match(42, 3, "New Zealand", "Belgium", Instant.parse("2026-06-27T03:00:00Z"))
        );
    }

    // ── Group H: Spain, Cape Verde, Saudi Arabia, Uruguay ──
    public static List<Match> groupHMatches() {
        return List.of(
            new Match(43, 1, "Spain", "Cape Verde", Instant.parse("2026-06-15T16:00:00Z")),
            new Match(44, 1, "Saudi Arabia", "Uruguay", Instant.parse("2026-06-15T22:00:00Z")),
            new Match(45, 2, "Spain", "Saudi Arabia", Instant.parse("2026-06-21T16:00:00Z")),
            new Match(46, 2, "Uruguay", "Cape Verde", Instant.parse("2026-06-21T22:00:00Z")),
            new Match(47, 3, "Cape Verde", "Saudi Arabia", Instant.parse("2026-06-27T00:00:00Z")),
            new Match(48, 3, "Uruguay", "Spain", Instant.parse("2026-06-27T00:00:00Z"))
        );
    }

    // ── Group I: France, Senegal, Iraq, Norway ──
    public static List<Match> groupIMatches() {
        return List.of(
            new Match(49, 1, "France", "Senegal", Instant.parse("2026-06-16T19:00:00Z")),
            new Match(50, 1, "Iraq", "Norway", Instant.parse("2026-06-16T22:00:00Z")),
            new Match(51, 2, "France", "Iraq", Instant.parse("2026-06-22T21:00:00Z")),
            new Match(52, 2, "Norway", "Senegal", Instant.parse("2026-06-23T00:00:00Z")),
            new Match(53, 3, "Norway", "France", Instant.parse("2026-06-26T19:00:00Z")),
            new Match(54, 3, "Senegal", "Iraq", Instant.parse("2026-06-26T19:00:00Z"))
        );
    }

    // ── Group J: Argentina, Algeria, Austria, Jordan ──
    public static List<Match> groupJMatches() {
        return List.of(
            new Match(55, 1, "Argentina", "Algeria", Instant.parse("2026-06-17T01:00:00Z")),
            new Match(56, 1, "Austria", "Jordan", Instant.parse("2026-06-17T04:00:00Z")),
            new Match(57, 2, "Argentina", "Austria", Instant.parse("2026-06-22T17:00:00Z")),
            new Match(58, 2, "Jordan", "Algeria", Instant.parse("2026-06-23T03:00:00Z")),
            new Match(59, 3, "Algeria", "Austria", Instant.parse("2026-06-28T02:00:00Z")),
            new Match(60, 3, "Jordan", "Argentina", Instant.parse("2026-06-28T02:00:00Z"))
        );
    }

    // ── Group K: Portugal, DR Congo, Uzbekistan, Colombia ──
    public static List<Match> groupKMatches() {
        return List.of(
            new Match(61, 1, "Portugal", "DR Congo", Instant.parse("2026-06-17T17:00:00Z")),
            new Match(62, 1, "Uzbekistan", "Colombia", Instant.parse("2026-06-18T02:00:00Z")),
            new Match(63, 2, "Portugal", "Uzbekistan", Instant.parse("2026-06-23T17:00:00Z")),
            new Match(64, 2, "Colombia", "DR Congo", Instant.parse("2026-06-24T02:00:00Z")),
            new Match(65, 3, "Colombia", "Portugal", Instant.parse("2026-06-27T23:30:00Z")),
            new Match(66, 3, "DR Congo", "Uzbekistan", Instant.parse("2026-06-27T23:30:00Z"))
        );
    }

    // ── Group L: England, Croatia, Ghana, Panama ──
    public static List<Match> groupLMatches() {
        return List.of(
            new Match(67, 1, "England", "Croatia", Instant.parse("2026-06-17T20:00:00Z")),
            new Match(68, 1, "Ghana", "Panama", Instant.parse("2026-06-17T23:00:00Z")),
            new Match(69, 2, "England", "Ghana", Instant.parse("2026-06-23T20:00:00Z")),
            new Match(70, 2, "Panama", "Croatia", Instant.parse("2026-06-23T23:00:00Z")),
            new Match(71, 3, "Panama", "England", Instant.parse("2026-06-27T21:00:00Z")),
            new Match(72, 3, "Croatia", "Ghana", Instant.parse("2026-06-27T21:00:00Z"))
        );
    }

    // ═══════════════════════════════════════════
    //  KNOCKOUT STAGE (matches 73–104)
    // ═══════════════════════════════════════════

    /**
     * All 32 knockout matches. Teams are null initially — they get populated
     * when the group stage finishes and the bracket is resolved.
     *
     * R32 matchups are pre-determined by FIFA based on group position.
     * Later rounds depend on winners of previous rounds.
     */
    public static List<Match> allKnockoutMatches() {
        return List.of(
            // ── Round of 32 (M73–M88) ──
            new Match(73,  1, Match.ROUND_R32, null, null, Instant.parse("2026-06-28T19:00:00Z")),  // 2A vs 2B
            new Match(74,  1, Match.ROUND_R32, null, null, Instant.parse("2026-06-29T17:00:00Z")),  // 1E vs 3rd(A/B/C/D/F)
            new Match(75,  1, Match.ROUND_R32, null, null, Instant.parse("2026-06-30T01:00:00Z")),  // 1F vs 2C
            new Match(76,  1, Match.ROUND_R32, null, null, Instant.parse("2026-06-29T21:00:00Z")),  // 1C vs 2F
            new Match(77,  1, Match.ROUND_R32, null, null, Instant.parse("2026-06-30T21:00:00Z")),  // 1I vs 3rd(C/D/F/G/H)
            new Match(78,  1, Match.ROUND_R32, null, null, Instant.parse("2026-06-30T17:00:00Z")),  // 2E vs 2I
            new Match(79,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-01T01:00:00Z")),  // 1A vs 3rd(C/E/F/H/I)
            new Match(80,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-01T16:00:00Z")),  // 1L vs 3rd(E/H/I/J/K)
            new Match(81,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-02T00:00:00Z")),  // 1D vs 3rd(B/E/F/I/J)
            new Match(82,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-01T20:00:00Z")),  // 1G vs 3rd(A/E/H/I/J)
            new Match(83,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-02T23:00:00Z")),  // 2K vs 2L
            new Match(84,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-02T19:00:00Z")),  // 1H vs 2J
            new Match(85,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-03T03:00:00Z")),  // 1B vs 3rd(E/F/G/I/J)
            new Match(86,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-03T22:00:00Z")),  // 1J vs 2H
            new Match(87,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-03T01:30:00Z")),  // 1K vs 3rd(D/E/I/J/L)
            new Match(88,  1, Match.ROUND_R32, null, null, Instant.parse("2026-07-03T18:00:00Z")),  // 2D vs 2G

            // ── Round of 16 (M89–M96) ──
            new Match(89,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-04T17:00:00Z")),  // W73 vs W75
            new Match(90,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-04T21:00:00Z")),  // W74 vs W77
            new Match(91,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-05T20:00:00Z")),  // W76 vs W78
            new Match(92,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-06T00:00:00Z")),  // W79 vs W80
            new Match(93,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-05T00:00:00Z")),  // W83 vs W84
            new Match(94,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-07T00:00:00Z")),  // W81 vs W82
            new Match(95,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-06T19:00:00Z")),  // W86 vs W88
            new Match(96,  1, Match.ROUND_R16, null, null, Instant.parse("2026-07-07T16:00:00Z")),  // W85 vs W87

            // ── Quarter-finals (M97–M100) ──
            new Match(97,  1, Match.ROUND_QF,  null, null, Instant.parse("2026-07-09T20:00:00Z")),  // W89 vs W90
            new Match(98,  1, Match.ROUND_QF,  null, null, Instant.parse("2026-07-10T19:00:00Z")),  // W93 vs W94
            new Match(99,  1, Match.ROUND_QF,  null, null, Instant.parse("2026-07-11T21:00:00Z")),  // W91 vs W92
            new Match(100, 1, Match.ROUND_QF,  null, null, Instant.parse("2026-07-12T01:00:00Z")),  // W95 vs W96

            // ── Semi-finals (M101–M102) ──
            new Match(101, 1, Match.ROUND_SF,  null, null, Instant.parse("2026-07-14T19:00:00Z")),  // W97 vs W98
            new Match(102, 1, Match.ROUND_SF,  null, null, Instant.parse("2026-07-15T19:00:00Z")),  // W99 vs W100

            // ── Third place (M103) ──
            new Match(103, 1, Match.ROUND_3RD, null, null, Instant.parse("2026-07-18T21:00:00Z")),  // L101 vs L102

            // ── Final (M104) ──
            new Match(104, 1, Match.ROUND_FIN, null, null, Instant.parse("2026-07-19T19:00:00Z"))   // W101 vs W102
        );
    }

    /**
     * Bracket dependency map.
     * For each KO match, defines how home and away teams are determined.
     *
     * Source format:
     *   "1A"        → 1st place of Group A
     *   "2B"        → 2nd place of Group B
     *   "3rd(A,B,C)" → best 3rd among groups A,B,C
     *   "W73"       → winner of match 73
     *   "L101"      → loser of match 101 (for 3rd place)
     */
    public static Map<Integer, String[]> bracketSources() {
        var m = new LinkedHashMap<Integer, String[]>();

        // R32 — from group positions
        m.put(73,  new String[]{"2A",          "2B"});
        m.put(74,  new String[]{"1E",          "3rd(A,B,C,D,F)"});
        m.put(75,  new String[]{"1F",          "2C"});
        m.put(76,  new String[]{"1C",          "2F"});
        m.put(77,  new String[]{"1I",          "3rd(C,D,F,G,H)"});
        m.put(78,  new String[]{"2E",          "2I"});
        m.put(79,  new String[]{"1A",          "3rd(C,E,F,H,I)"});
        m.put(80,  new String[]{"1L",          "3rd(E,H,I,J,K)"});
        m.put(81,  new String[]{"1D",          "3rd(B,E,F,I,J)"});
        m.put(82,  new String[]{"1G",          "3rd(A,E,H,I,J)"});
        m.put(83,  new String[]{"2K",          "2L"});
        m.put(84,  new String[]{"1H",          "2J"});
        m.put(85,  new String[]{"1B",          "3rd(E,F,G,I,J)"});
        m.put(86,  new String[]{"1J",          "2H"});
        m.put(87,  new String[]{"1K",          "3rd(D,E,I,J,L)"});
        m.put(88,  new String[]{"2D",          "2G"});

        // R16 — from winners of R32
        m.put(89,  new String[]{"W73",         "W75"});
        m.put(90,  new String[]{"W74",         "W77"});
        m.put(91,  new String[]{"W76",         "W78"});
        m.put(92,  new String[]{"W79",         "W80"});
        m.put(93,  new String[]{"W83",         "W84"});
        m.put(94,  new String[]{"W81",         "W82"});
        m.put(95,  new String[]{"W86",         "W88"});
        m.put(96,  new String[]{"W85",         "W87"});

        // QF — from winners of R16
        m.put(97,  new String[]{"W89",         "W90"});
        m.put(98,  new String[]{"W93",         "W94"});
        m.put(99,  new String[]{"W91",         "W92"});
        m.put(100, new String[]{"W95",         "W96"});

        // SF — from winners of QF
        m.put(101, new String[]{"W97",         "W98"});
        m.put(102, new String[]{"W99",         "W100"});

        // 3rd place — from losers of SF
        m.put(103, new String[]{"L101",        "L102"});

        // Final — from winners of SF
        m.put(104, new String[]{"W101",        "W102"});

        return m;
    }

    // ═══════════════════════════════════════════
    //  GROUP LOOKUP
    // ═══════════════════════════════════════════

    private static final Map<String, List<Match>> ALL = new LinkedHashMap<>();
    static {
        ALL.put("A", groupAMatches());
        ALL.put("B", groupBMatches());
        ALL.put("C", groupCMatches());
        ALL.put("D", groupDMatches());
        ALL.put("E", groupEMatches());
        ALL.put("F", groupFMatches());
        ALL.put("G", groupGMatches());
        ALL.put("H", groupHMatches());
        ALL.put("I", groupIMatches());
        ALL.put("J", groupJMatches());
        ALL.put("K", groupKMatches());
        ALL.put("L", groupLMatches());
    }

    public static Map<String, List<Match>> allGroupMatches() {
        return ALL;
    }

    public static List<Match> matchesForGroup(String groupCode) {
        return ALL.get(groupCode.toUpperCase());
    }
}
