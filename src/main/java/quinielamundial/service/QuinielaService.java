package quinielamundial.service;

import quinielamundial.domain.Group;
import quinielamundial.domain.Match;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.TreeMap;

public class QuinielaService {
    private final Map<String, Group> groups = new TreeMap<>();
    private final List<String> candidates = List.of(
        "Mexico", "South Africa", "South Korea", "Czechia",
        "Canada", "Bosnia and Herzegovina", "Qatar", "Switzerland",
        "Brazil", "Morocco", "Haiti", "Scotland",
        "USA", "Paraguay", "Australia", "Türkiye",
        "Germany", "Curaçao", "Ivory Coast", "Ecuador",
        "Netherlands", "Japan", "Sweden", "Tunisia",
        "Belgium", "Egypt", "Iran", "New Zealand",
        "Spain", "Cape Verde", "Saudi Arabia", "Uruguay",
        "France", "Senegal", "Iraq", "Norway",
        "Argentina", "Algeria", "Austria", "Jordan",
        "Portugal", "DR Congo", "Uzbekistan", "Colombia",
        "England", "Croatia", "Ghana", "Panama"
    );
    private String tournamentChampion = "Argentina";
    private final Random random = new Random();

    public Collection<Group> groups() { return groups.values(); }
    public void restoreState(Collection<Group> restoredGroups, String champion) { groups.clear(); for (var group : restoredGroups) groups.put(group.code(), group); if (champion != null && !champion.isBlank()) tournamentChampion = champion; }
    public List<String> candidates() { return candidates; }
    public String tournamentChampion() { return tournamentChampion; }
    public void setTournamentChampion(String tournamentChampion) { if (tournamentChampion == null || tournamentChampion.isBlank()) throw new IllegalArgumentException("El campeón del torneo es obligatorio."); this.tournamentChampion = tournamentChampion; }
    public boolean tournamentStarted() { return groups.values().stream().flatMap(group -> group.matches().stream()).anyMatch(match -> Instant.now().isAfter(match.kickoff())); }
    public Group createGroup(String name, String creator) { var group = new Group(uniqueCode(), name, defaultMatches()); group.join(creator); groups.put(group.code(), group); return group; }
    public Group joinGroup(String code, String member) { var group = group(code); if (group == null) return null; if (member == null || member.isBlank()) throw new IllegalArgumentException("El nombre del usuario es obligatorio."); group.join(member); return group; }
    public Group group(String code) { return groups.get(code == null ? "" : code.trim().toUpperCase()); }
    public Map.Entry<String, Group> findGroupByToken(String token) {
        if (token == null || token.isBlank()) return null;
        for (var entry : groups.entrySet()) {
            try { entry.getValue().requireByToken(token); return entry; } catch (Exception ignored) {}
        }
        return null;
    }
    private String uniqueCode() { while (true) { var code = randomCode(); if (!groups.containsKey(code)) return code; } }
    private String randomCode() { var alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; var code = new StringBuilder(); for (int i = 0; i < 8; i++) code.append(alphabet.charAt(random.nextInt(alphabet.length()))); return code.toString(); }

    /** All 72 group-stage matches from the real World Cup 2026 schedule.
     *  Creates NEW Match instances so each group gets independent copies
     *  (Match state like homeGoals/awayGoals/finished is mutable). */
    private List<Match> defaultMatches() {
        var all = new ArrayList<Match>();
        for (var entry : WorldCupSchedule.allGroupMatches().entrySet()) {
            for (var original : entry.getValue()) {
                all.add(new Match(original.id(), original.jornada(), original.home(), original.away(), original.kickoff()));
            }
        }
        return List.copyOf(all);
    }

    /**
     * Recalculate the knockout bracket for a given group.
     * Call after any group-stage result changes to populate R32 teams.
     */
    public void resolveBracket(Group group) {
        BracketResolver.resolveBracket(group);
    }

    /**
     * Propagate winners to later rounds after a KO match result.
     * Call after any KO result is registered.
     */
    public void propagateWinners(Group group) {
        BracketResolver.propagateWinners(group);
    }

    /**
     * Resolve bracket for ALL groups. Called on app startup.
     */
    public void resolveAllBrackets() {
        for (var group : groups.values()) {
            BracketResolver.resolveBracket(group);
        }
    }
}
