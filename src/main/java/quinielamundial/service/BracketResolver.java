package quinielamundial.service;

import quinielamundial.domain.Group;
import quinielamundial.domain.Match;
import quinielamundial.logging.Logger;

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

    private static final Logger LOG = new Logger("BracketResolver");

    /**
     * FULL bracket resolution: recalculate standings and populate all KNOWN
     * matches. Should be called after any group-stage result changes.
     *
     * @param group     the group (of friends) whose KO matches to resolve
     * @param allGroups ALL groups (each has a copy of all 72 group-stage
     *                  matches); standings are computed from ALL of them so
     *                  that every World Cup group's results are included
     *                  even if some groups haven't been polled yet.
     */
    public static void resolveBracket(Group group, List<Group> allGroups) {
        var standings = calculateAllStandings(allGroups);
        var advancingThirds = bestThirdPlaced(standings);
        var koMatches = group.knockoutMatches();
        var bracketSources = WorldCupSchedule.bracketSources();

        // ── Debug: log advancing thirds and M79 resolution ──
        {
            var sb = new StringBuilder("Advancing 3rds: ");
            for (var t : advancingThirds) sb.append(t).append(" ");
            LOG.info(sb.toString());
            // Log EVERY 3rd place team with their points/GD/GF
            var dbg = new StringBuilder("All 3rds: ");
            for (var entry : standings.entrySet()) {
                var list = entry.getValue();
                if (list.size() >= 3) {
                    var s = list.get(2);
                    dbg.append(entry.getKey()).append(":").append(s.team())
                       .append("(").append(s.points()).append("p,")
                       .append(s.goalDiff()).append("gd,").append(s.goalsFor()).append("gf) ");
                } else {
                    dbg.append(entry.getKey()).append(":<3teams ");
                }
            }
            LOG.info(dbg.toString());
            var m79Src = bracketSources.get(79);
            if (m79Src != null) {
                LOG.info("M79 sources: {} vs {}", m79Src[0], m79Src[1]);
            }
        }

        // Pre-compute 3rd-place assignments for R32 so the same team is NOT
        // assigned to multiple matches (the old findFirst() approach was buggy
        // when candidate-group lists overlapped).
        var thirdAssignment = assignThirdPlacedMatches(standings, advancingThirds, bracketSources);

        // ── Debug: log what M79 gets ──
        var m79Team = thirdAssignment.get(79);
        if (m79Team != null) {
            LOG.info("M79 3rd-place assigned: " + m79Team);
        } else {
            LOG.info("M79 3rd-place NOT ASSIGNED (unresolved)");
        }

        for (var match : koMatches) {
            if (match.finished()) continue;
            // For R32, always re-populate from current standings (groups may have been
            // updated). For later rounds, only populate if teams not yet known.
            if (match.id() > 88 && match.teamsKnown()) continue;

            var sources = bracketSources.get(match.id());
            if (sources == null) continue;

            String home = resolve(sources[0], match.id(), standings, advancingThirds, koMatches, thirdAssignment);
            String away = resolve(sources[1], match.id(), standings, advancingThirds, koMatches, thirdAssignment);
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

            String home = resolve(sources[0], match.id(), null, null, koMatches, null);
            String away = resolve(sources[1], match.id(), null, null, koMatches, null);
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
                                  int currentMatchId,
                                  Map<String, List<Standing>> standings,
                                  Set<String> advancingThirds,
                                  List<Match> koMatches,
                                  Map<Integer, String> thirdAssignment) {
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
        // Use the pre-computed assignment to avoid assigning the same team
        // to multiple matches.
        if (source.startsWith("3rd(")) {
            if (thirdAssignment == null) return null;
            return thirdAssignment.get(currentMatchId);
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

    /**
     * Computes standings for all 12 World Cup groups (A–L) by collecting
     * matches from ALL Group objects (each group of friends has a copy of
     * all 72 group-stage matches).  This ensures we have every World Cup
     * group's results, even if some groups' matches haven't been polled yet.
     *
     * @param allGroups all groups of friends (each has the full 72-match list)
     * @return group code → ranked list of {@link Standing}
     */
    public static Map<String, List<Standing>> calculateAllStandings(List<Group> allGroups) {
        // Collect every match from every group into a single list.
        var combined = new ArrayList<Match>();
        for (var g : allGroups) {
            for (var m : g.matches()) {
                // Deduplicate by match ID (groups share copy-constructed matches)
                if (combined.stream().noneMatch(x -> x.id() == m.id())) {
                    combined.add(m);
                }
            }
        }
        // Wrap in a synthetic group so calculateGroupStandings can iterate it
        var synthetic = new Group("__all__", "__all__", combined);

        var result = new LinkedHashMap<String, List<Standing>>();
        for (var entry : GROUP_TEAMS.entrySet()) {
            result.put(entry.getKey(), calculateGroupStandings(synthetic, entry.getValue()));
        }
        return result;
    }

    /**
     * Computes group standings per FIFA 2026 regulations.
     *
     * Tiebreaker order (FIFA 2026):
     * 1. Points (3/1/0)
     * 2. Head-to-head points (among tied teams only)
     * 3. Head-to-head goal difference
     * 4. Head-to-head goals scored
     * 5. Overall goal difference
     * 6. Overall goals scored
     */
    private static List<Standing> calculateGroupStandings(Group group, List<String> teams) {
        // ── 1st pass: compute overall pts, GF, GA for every team ──
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

        // ── 2nd pass: group by points and resolve H2H within each tier ──
        var byPoints = teams.stream().collect(Collectors.groupingBy(t -> pts.getOrDefault(t, 0)));
        var sortedKeys = byPoints.keySet().stream().sorted(Comparator.reverseOrder()).toList();

        var result = new ArrayList<Standing>();
        for (int key : sortedKeys) {
            result.addAll(resolveTier(group, byPoints.get(key), pts, gf, ga));
        }
        return result;
    }

    /**
     * Resolves a group of teams all tied on the same points, using the FIFA
     * recursive H2H procedure:
     *
     * 1. Apply H2H points → H2H GD → H2H GF among the current set.
     * 2. If some teams separate, place them and recurse on the rest.
     * 3. If ALL remain tied through step 1, use overall GD → overall GF.
     * 4. If a SUBSET remains tied, recurse on that subset (re-applies H2H).
     */
    private static List<Standing> resolveTier(Group group,
                                              List<String> tied,
                                              Map<String, Integer> pts,
                                              Map<String, Integer> gf,
                                              Map<String, Integer> ga) {
        var remaining = new ArrayList<>(tied);
        var result = new ArrayList<Standing>();

        while (remaining.size() > 1) {
            var h2hPts = new HashMap<String, Integer>();
            var h2hGf = new HashMap<String, Integer>();
            var h2hGa = new HashMap<String, Integer>();
            for (var t : remaining) { h2hPts.put(t, 0); h2hGf.put(t, 0); h2hGa.put(t, 0); }

            for (var match : group.matches()) {
                if (!match.finished()) continue;
                var h = match.home(); var a = match.away();
                if (!remaining.contains(h) || !remaining.contains(a)) continue;
                int hg = match.homeGoals(), ag = match.awayGoals();
                h2hGf.merge(h, hg, Integer::sum);
                h2hGf.merge(a, ag, Integer::sum);
                h2hGa.merge(h, ag, Integer::sum);
                h2hGa.merge(a, hg, Integer::sum);
                if (hg > ag) h2hPts.merge(h, 3, Integer::sum);
                else if (ag > hg) h2hPts.merge(a, 3, Integer::sum);
                else { h2hPts.merge(h, 1, Integer::sum); h2hPts.merge(a, 1, Integer::sum); }
            }

            // Sort remaining by H2H criteria (descending: higher is better)
            var sorted = remaining.stream()
                .sorted(Comparator.<String>comparingInt(t -> -h2hPts.getOrDefault(t, 0))
                    .thenComparingInt(t -> -(h2hGf.getOrDefault(t, 0) - h2hGa.getOrDefault(t, 0)))
                    .thenComparingInt(t -> -h2hGf.getOrDefault(t, 0)))
                .toList();

            // Find split: where does the H2H key change?
            var firstKey = h2hKey(sorted.get(0), h2hPts, h2hGf, h2hGa);
            int split = 1;
            while (split < sorted.size() && h2hKey(sorted.get(split), h2hPts, h2hGf, h2hGa).equals(firstKey)) {
                split++;
            }

            if (split == sorted.size()) {
                // H2H failed to separate ANY team → fall through to overall GD/GF
                sorted = remaining.stream()
                    .sorted(Comparator.<String>comparingInt(t -> -(gf.get(t) - ga.get(t)))
                        .thenComparingInt(t -> -gf.get(t)))
                    .toList();
                for (var t : sorted) {
                    result.add(new Standing(t, pts.get(t), gf.get(t) - ga.get(t), gf.get(t)));
                }
                return result;
            }

            // Place the separated top teams
            for (int i = 0; i < split; i++) {
                var t = sorted.get(i);
                result.add(new Standing(t, pts.get(t), gf.get(t) - ga.get(t), gf.get(t)));
            }

            // Continue with the rest (still tied) — recursive re-application
            remaining = new ArrayList<>(sorted.subList(split, sorted.size()));
        }

        if (!remaining.isEmpty()) {
            var t = remaining.get(0);
            result.add(new Standing(t, pts.get(t), gf.get(t) - ga.get(t), gf.get(t)));
        }
        return result;
    }

    /** Composite H2H key: [points, goalDiff, goalsFor]. */
    private static List<Integer> h2hKey(String team,
                                        Map<String, Integer> h2hPts,
                                        Map<String, Integer> h2hGf,
                                        Map<String, Integer> h2hGa) {
        return List.of(
            h2hPts.getOrDefault(team, 0),
            h2hGf.getOrDefault(team, 0) - h2hGa.getOrDefault(team, 0),
            h2hGf.getOrDefault(team, 0)
        );
    }

    /** Get the 8 best third-placed teams (ordered by points → GD → GF). */
    public static Set<String> bestThirdPlaced(Map<String, List<Standing>> standings) {
        return standings.values().stream()
            .map(s -> s.size() >= 3 ? s.get(2) : null)
            .filter(Objects::nonNull)
            .sorted(Comparator.<Standing>comparingInt(Standing::points)
                .thenComparingInt(Standing::goalDiff)
                .thenComparingInt(Standing::goalsFor)
                .reversed())
            .limit(8)
            .map(Standing::team)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ═══════════════════════════════════════════
    //  THIRD-PLACE ASSIGNMENT (constraint-satisfaction)
    // ═══════════════════════════════════════════

    /**
     * Pre-compute a map of R32 match ID → third-place team, ensuring each
     * advancing third-place team is assigned to exactly one match.
     *
     * Uses backtracking (constraint satisfaction) since FIFA pre-maps 495
     * possible combinations — a simple "first match" approach fails when
     * candidate-group lists overlap.
     */
    private static Map<Integer, String> assignThirdPlacedMatches(
            Map<String, List<Standing>> standings,
            Set<String> advancingThirds,
            Map<Integer, String[]> bracketSources) {
        if (advancingThirds == null || advancingThirds.isEmpty()) return Map.of();

        // Find R32 match IDs that have a 3rd-place source and determine
        // which advancing teams are eligible for each.
        var eligible = new LinkedHashMap<Integer, List<String>>();
        for (var entry : bracketSources.entrySet()) {
            int matchId = entry.getKey();
            if (matchId < 73 || matchId > 88) continue;
            String[] sources = entry.getValue();
            for (String src : sources) {
                if (src.startsWith("3rd(")) {
                    var candidates = parseCandidateGroups(src);
                    var matched = advancingThirds.stream()
                        .filter(t -> {
                            var g = groupForTeam(t, standings);
                            return g != null && candidates.contains(g);
                        })
                        .collect(Collectors.toList());
                    eligible.put(matchId, matched);
                    break;
                }
            }
        }

        // Backtracking: try to assign each match a unique advancing third
        var matchIds = List.copyOf(eligible.keySet());
        var used = new HashSet<String>();
        var assignment = new LinkedHashMap<Integer, String>();

        if (backtrackAssign(matchIds, 0, eligible, used, assignment)) {
            return assignment;
        }
        return Map.of(); // fallback — should not happen with valid standings
    }

    /** Parse "3rd(A,B,C,D,F)" → ["A","B","C","D","F"]. */
    private static List<String> parseCandidateGroups(String source) {
        var inner = source.substring(4, source.length() - 1);
        return Arrays.asList(inner.split(","));
    }

    /** Recursive backtracking for third-place assignment. */
    private static boolean backtrackAssign(
            List<Integer> matchIds,
            int idx,
            Map<Integer, List<String>> eligible,
            HashSet<String> used,
            Map<Integer, String> assignment) {
        if (idx == matchIds.size()) return true;
        int matchId = matchIds.get(idx);
        for (String team : eligible.getOrDefault(matchId, List.of())) {
            if (used.contains(team)) continue;
            used.add(team);
            assignment.put(matchId, team);
            if (backtrackAssign(matchIds, idx + 1, eligible, used, assignment)) {
                return true;
            }
            used.remove(team);
            assignment.remove(matchId);
        }
        return false;
    }
}
