package quinielamundial.service;

import quinielamundial.domain.Group;
import quinielamundial.domain.Match;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Resolves the 2026 World Cup knockout bracket for a single Group (of friends).
 *
 * After all 72 group-stage matches finish:
 * 1. Calculates the standings for each of the 12 groups (A–L).
 * 2. Determines the 8 best third-placed teams across all groups.
 * 3. Populates the 16 R32 matches (M73–M88) with actual team names.
 * 4. Propagates winners through R16, QF, SF, 3rd, Final as results come in.
 */
public class BracketResolver {
    private BracketResolver() {}

    /** Group composition: each World Cup group code → its 4 teams. */
    private static final Map<String, List<String>> GROUP_TEAMS = new LinkedHashMap<>();
    static {
        GROUP_TEAMS.put("A", List.of("Mexico", "South Africa", "South Korea", "Czechia"));
        GROUP_TEAMS.put("B", List.of("Canada", "Bosnia and Herzegovina", "Qatar", "Switzerland"));
        GROUP_TEAMS.put("C", List.of("Brazil", "Morocco", "Haiti", "Scotland"));
        GROUP_TEAMS.put("D", List.of("USA", "Paraguay", "Australia", "Türkiye"));
        GROUP_TEAMS.put("E", List.of("Germany", "Curaçao", "Ivory Coast", "Ecuador"));
        GROUP_TEAMS.put("F", List.of("Netherlands", "Japan", "Sweden", "Tunisia"));
        GROUP_TEAMS.put("G", List.of("Belgium", "Egypt", "Iran", "New Zealand"));
        GROUP_TEAMS.put("H", List.of("Spain", "Cape Verde", "Saudi Arabia", "Uruguay"));
        GROUP_TEAMS.put("I", List.of("France", "Senegal", "Iraq", "Norway"));
        GROUP_TEAMS.put("J", List.of("Argentina", "Algeria", "Austria", "Jordan"));
        GROUP_TEAMS.put("K", List.of("Portugal", "DR Congo", "Uzbekistan", "Colombia"));
        GROUP_TEAMS.put("L", List.of("England", "Croatia", "Ghana", "Panama"));
    }

    /** Team standings within a single World Cup group. */
    public record Standing(String team, int points, int goalDiff, int goalsFor) {}

    /**
     * FULL bracket resolution: recalculate standings and populate all KNOWN
     * matches. Should be called after any group-stage result changes.
     */
    public static void resolveBracket(Group group) {
        var standings = calculateAllStandings(group);
        var advancingThirds = bestThirdPlaced(standings);
        var koMatches = group.knockoutMatches();
        var bracketSources = WorldCupSchedule.bracketSources();

        for (var match : koMatches) {
            if (match.finished()) continue;
            // For R32, always re-populate from current standings (groups may have been
            // updated). For later rounds, only populate if teams not yet known.
            if (match.id() > 88 && match.teamsKnown()) continue;

            var sources = bracketSources.get(match.id());
            if (sources == null) continue;

            String home = resolve(sources[0], standings, advancingThirds, koMatches);
            String away = resolve(sources[1], standings, advancingThirds, koMatches);
            if (home != null && away != null) {
                // Only update if teams changed (avoid unnecessary overwrites)
                if (!home.equals(match.home()) || !away.equals(match.away())) {
                    match.setTeams(home, away);
                }
            }
        }
    }

    /**
     * LIGHTWEIGHT propagation: only updates matches whose dependencies
     * (previous round winners/losers) have resolved. Does NOT recalculate
     * standings. Call after any KO result is registered.
     */
    public static void propagateWinners(Group group) {
        var koMatches = group.knockoutMatches();
        var bracketSources = WorldCupSchedule.bracketSources();

        for (var match : koMatches) {
            if (match.finished() || match.teamsKnown()) continue;
            if (match.id() < 89) continue; // R32 handled by resolveBracket only
            var sources = bracketSources.get(match.id());
            if (sources == null) continue;

            String home = resolve(sources[0], null, null, koMatches);
            String away = resolve(sources[1], null, null, koMatches);
            if (home != null && away != null) {
                match.setTeams(home, away);
            }
        }
    }

    // ═══════════════════════════════════════════
    //  SOURCE RESOLUTION
    // ═══════════════════════════════════════════

    /**
     * Resolve a source string to a team name.
     *
     * Source formats:
     *   "1A"       → 1st place of Group A
     *   "2B"       → 2nd place of Group B
     *   "3rd(A,B,C)" → best advancing 3rd from groups A,B,C
     *   "W73"      → winner of match 73
     *   "L101"     → loser of match 101 (for 3rd place)
     */
    private static String resolve(String source,
                                  Map<String, List<Standing>> standings,
                                  Set<String> advancingThirds,
                                  List<Match> koMatches) {
        if (source == null) return null;

        // Winner of a match: "W73"
        if (source.startsWith("W")) {
            int id = Integer.parseInt(source.substring(1));
            return koMatches.stream()
                .filter(m -> m.id() == id)
                .map(Match::winner)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }

        // Loser of a match: "L101"
        if (source.startsWith("L")) {
            int id = Integer.parseInt(source.substring(1));
            return koMatches.stream()
                .filter(m -> m.id() == id && m.finished())
                .map(m -> {
                    var w = m.winner();
                    return w == null ? null : w.equals(m.home()) ? m.away() : m.home();
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }

        // Third-place from candidate groups: "3rd(A,B,C,D,F)"
        if (source.startsWith("3rd(")) {
            if (advancingThirds == null || standings == null) return null;
            var inner = source.substring(4, source.length() - 1);
            var candidates = Arrays.asList(inner.split(","));
            // Find the highest-ranked advancing third among candidate groups
            return advancingThirds.stream()
                .filter(t -> {
                    var group = groupForTeam(t, standings);
                    return group != null && candidates.contains(group);
                })
                .findFirst()
                .orElse(null);
        }

        // Group position: "1A", "2B", "3C" etc
        if (standings != null && source.length() >= 2) {
            String group = source.substring(source.length() - 1);
            int pos;
            try {
                pos = Integer.parseInt(source.substring(0, source.length() - 1));
            } catch (NumberFormatException e) {
                return null;
            }
            var groupStandings = standings.get(group);
            if (groupStandings != null && pos >= 1 && pos <= groupStandings.size()) {
                return groupStandings.get(pos - 1).team();
            }
        }

        return null;
    }

    /** Find which group a team belongs to. */
    private static String groupForTeam(String team, Map<String, List<Standing>> standings) {
        for (var entry : standings.entrySet()) {
            for (var s : entry.getValue()) {
                if (s.team().equals(team)) return entry.getKey();
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════
    //  STANDINGS CALCULATION
    // ═══════════════════════════════════════════

    public static Map<String, List<Standing>> calculateAllStandings(Group group) {
        var result = new LinkedHashMap<String, List<Standing>>();
        for (var entry : GROUP_TEAMS.entrySet()) {
            result.put(entry.getKey(), calculateGroupStandings(group, entry.getValue()));
        }
        return result;
    }

    private static List<Standing> calculateGroupStandings(Group group, List<String> teams) {
        var pts = new HashMap<String, Integer>();
        var gf = new HashMap<String, Integer>();
        var ga = new HashMap<String, Integer>();
        for (var t : teams) { pts.put(t, 0); gf.put(t, 0); ga.put(t, 0); }

        for (var match : group.matches()) {
            if (!match.finished()) continue;
            var h = match.home(); var a = match.away();
            if (!teams.contains(h) || !teams.contains(a)) continue;
            int hg = match.homeGoals(), ag = match.awayGoals();
            gf.merge(h, hg, Integer::sum);
            gf.merge(a, ag, Integer::sum);
            ga.merge(h, ag, Integer::sum);
            ga.merge(a, hg, Integer::sum);
            if (hg > ag) pts.merge(h, 3, Integer::sum);
            else if (ag > hg) pts.merge(a, 3, Integer::sum);
            else { pts.merge(h, 1, Integer::sum); pts.merge(a, 1, Integer::sum); }
        }

        return teams.stream()
            .map(t -> new Standing(t, pts.getOrDefault(t, 0),
                gf.getOrDefault(t, 0) - ga.getOrDefault(t, 0),
                gf.getOrDefault(t, 0)))
            .sorted(Comparator.<Standing, Integer>comparing(Standing::points).reversed()
                .thenComparingInt(Standing::goalDiff).reversed()
                .thenComparingInt(Standing::goalsFor).reversed())
            .collect(Collectors.toList());
    }

    /** Get the 8 best third-placed teams (ordered by points → GD → GF). */
    public static Set<String> bestThirdPlaced(Map<String, List<Standing>> standings) {
        return standings.values().stream()
            .map(s -> s.size() >= 3 ? s.get(2) : null)
            .filter(Objects::nonNull)
            .sorted(Comparator.<Standing, Integer>comparing(Standing::points).reversed()
                .thenComparingInt(Standing::goalDiff).reversed()
                .thenComparingInt(Standing::goalsFor).reversed())
            .limit(8)
            .map(Standing::team)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
