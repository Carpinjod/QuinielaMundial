package quinielamundial.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import quinielamundial.domain.Group;
import quinielamundial.domain.Match;
import quinielamundial.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class MatchUpdateService {
    private static final String API_URL = "https://api.openligadb.de/getmatchdata/wm26/2026";
    private static final long POLL_INTERVAL_SECONDS = 15; // 15 seconds

    private static final Logger LOG = new Logger("MatchUpdateService");

    private final List<Group> groups;
    private final Consumer<List<Group>> onScoresUpdated;
    private final Consumer<List<Group>> onLiveScoresUpdated;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Gson gson = new Gson();

    /** German → English team names from OpenLigaDB API */
    private static final Map<String, String> GERMAN_TO_ENGLISH = Map.ofEntries(
        Map.entry("Algerien", "Algeria"),
        Map.entry("Argentinien", "Argentina"),
        Map.entry("Australien", "Australia"),
        Map.entry("Belgien", "Belgium"),
        Map.entry("Bosnien und Herzegowina", "Bosnia and Herzegovina"),
        Map.entry("Brasilien", "Brazil"),
        Map.entry("Curaçao", "Curaçao"),
        Map.entry("DR Kongo", "DR Congo"),
        Map.entry("Deutschland", "Germany"),
        Map.entry("Ecuador", "Ecuador"),
        Map.entry("Elfenbeinküste", "Ivory Coast"),
        Map.entry("England", "England"),
        Map.entry("Frankreich", "France"),
        Map.entry("Ghana", "Ghana"),
        Map.entry("Haiti", "Haiti"),
        Map.entry("Irak", "Iraq"),
        Map.entry("Iran", "Iran"),
        Map.entry("Japan", "Japan"),
        Map.entry("Jordanien", "Jordan"),
        Map.entry("Kanada", "Canada"),
        Map.entry("Kap Verde", "Cape Verde"),
        Map.entry("Katar", "Qatar"),
        Map.entry("Kolumbien", "Colombia"),
        Map.entry("Kroatien", "Croatia"),
        Map.entry("Marokko", "Morocco"),
        Map.entry("Mexiko", "Mexico"),
        Map.entry("Neuseeland", "New Zealand"),
        Map.entry("Niederlande", "Netherlands"),
        Map.entry("Norwegen", "Norway"),
        Map.entry("Panama", "Panama"),
        Map.entry("Paraguay", "Paraguay"),
        Map.entry("Portugal", "Portugal"),
        Map.entry("Saudi Arabien", "Saudi Arabia"),
        Map.entry("Schottland", "Scotland"),
        Map.entry("Schweden", "Sweden"),
        Map.entry("Schweiz", "Switzerland"),
        Map.entry("Senegal", "Senegal"),
        Map.entry("Spanien", "Spain"),
        Map.entry("Südafrika", "South Africa"),
        Map.entry("Südkorea", "South Korea"),
        Map.entry("Tschechien", "Czechia"),
        Map.entry("Tunesien", "Tunisia"),
        Map.entry("Türkei", "Türkiye"),
        Map.entry("USA", "USA"),
        Map.entry("Uruguay", "Uruguay"),
        Map.entry("Usbekistan", "Uzbekistan"),
        Map.entry("Ägypten", "Egypt"),
        Map.entry("Österreich", "Austria")
    );

    public MatchUpdateService(List<Group> groups,
                              Consumer<List<Group>> onScoresUpdated,
                              Consumer<List<Group>> onLiveScoresUpdated) {
        this.groups = groups;
        this.onScoresUpdated = onScoresUpdated;
        this.onLiveScoresUpdated = onLiveScoresUpdated;
    }

    public void start() {
        LOG.info("Polling {} every {}s", API_URL, POLL_INTERVAL_SECONDS);
        // Initial poll immediately — avoids waiting 10s for first API data.
        // This is critical on startup: bracket resolution depends on current
        // standings, and the saved state may be stale.
        scheduler.execute(this::poll);
        scheduler.scheduleAtFixedRate(this::poll, POLL_INTERVAL_SECONDS, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdown();
    }

    private void poll() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOG.error("HTTP {}", response.statusCode());
                return;
            }

            var apiMatches = parseApiMatches(response.body());
            if (apiMatches.isEmpty()) return;

            var finalGroups = new ArrayList<Group>();
            var liveGroups = new ArrayList<Group>();

            // PASS 1: group-stage matches + any already-known KO matches
            for (var group : groups) {
                var result = updateGroup(group, apiMatches);
                if (result.finalCount > 0) finalGroups.add(group);
                if (result.liveCount > 0) liveGroups.add(group);
            }

            if (!finalGroups.isEmpty()) {
                onScoresUpdated.accept(finalGroups);
                LOG.info("Group final results: {} groups updated", finalGroups.size());
            }
            if (!liveGroups.isEmpty()) {
                onLiveScoresUpdated.accept(liveGroups);
                LOG.info("Live scores: {} groups updated", liveGroups.size());
            }

            // PASS 2: KO matches whose brackets were just resolved by the callback.
            // Without this pass, KO results only appear on the NEXT poll (15s later),
            // forcing users to wait for points on resolved KO matches.
            var koFinal = new ArrayList<Group>();
            for (var group : groups) {
                var result = updateKnockoutMatches(group, apiMatches);
                if (result.finalCount > 0) koFinal.add(group);
                if (result.liveCount > 0 && !liveGroups.contains(group)) liveGroups.add(group);
            }
            if (!koFinal.isEmpty()) {
                onScoresUpdated.accept(koFinal);
                LOG.info("KO final results: {} groups updated", koFinal.size());
            }
            if (!liveGroups.isEmpty()) {
                onLiveScoresUpdated.accept(liveGroups);
            }
        } catch (Exception e) {
            LOG.error("Poll failed: {}", e.getMessage());
        }
    }

    /** Parse the OpenLigaDB JSON array into a map keyed by normalized (team1, team2).
     *  Always includes every match — even those without scores — so that kickoff
     *  times can be synced for all matches, not just started/finished ones. */
    private Map<String, ApiMatch> parseApiMatches(String json) {
        var map = new HashMap<String, ApiMatch>();
        try {
            var arr = gson.fromJson(json, JsonArray.class);
            for (var elem : arr) {
                var obj = elem.getAsJsonObject();
                var dateTimeUtc = getString(obj, "matchDateTimeUTC");
                var team1 = getString(obj.getAsJsonObject("team1"), "teamName");
                var team2 = getString(obj.getAsJsonObject("team2"), "teamName");
                if (team1 == null || team2 == null) continue;
                team1 = GERMAN_TO_ENGLISH.getOrDefault(team1, team1);
                team2 = GERMAN_TO_ENGLISH.getOrDefault(team2, team2);

                var key = normalize(team1) + "|" + normalize(team2);

                var apiMatch = new ApiMatch();
                apiMatch.dateTimeUtc = dateTimeUtc;
                apiMatch.team1 = team1;
                apiMatch.team2 = team2;

                // 1. Final result: only when matchIsFinished=true
                var matchIsFinished = getBoolean(obj, "matchIsFinished");
                if (matchIsFinished) {
                    Integer finalHome = extractResult(obj, 2, "pointsTeam1");
                    Integer finalAway = extractResult(obj, 2, "pointsTeam2");
                    if (finalHome != null && finalAway != null) {
                        apiMatch.homeGoals = finalHome;
                        apiMatch.awayGoals = finalAway;
                        apiMatch.isFinal = true;

                        // Determine advancing team (for KO matches — who won on the day)
                        if (!finalHome.equals(finalAway)) {
                            // Decided in regular / extra time
                            apiMatch.advancingTeam = finalHome > finalAway ? team1 : team2;
                        } else {
                            // Draw → check penalty shootout (resultTypeID=3)
                            Integer penHome = extractResult(obj, 3, "pointsTeam1");
                            Integer penAway = extractResult(obj, 3, "pointsTeam2");
                            if (penHome != null && penAway != null && !penHome.equals(penAway)) {
                                apiMatch.advancingTeam = penHome > penAway ? team1 : team2;
                            }
                        }
                    }
                }

                // 2. Live / in-progress result: if not yet final, try to extract live score
                if (!matchIsFinished) {
                    Integer liveHome = extractResult(obj, 2, "pointsTeam1");
                    Integer liveAway = extractResult(obj, 2, "pointsTeam2");
                    if (liveHome == null || liveAway == null) {
                        liveHome = extractResult(obj, 1, "pointsTeam1");
                        liveAway = extractResult(obj, 1, "pointsTeam2");
                    }
                    if (liveHome != null && liveAway != null) {
                        apiMatch.homeGoals = liveHome;
                        apiMatch.awayGoals = liveAway;
                    }
                }

                map.put(key, apiMatch);
            }
        } catch (Exception e) {
            LOG.error("Parse error: {}", e.getMessage());
        }
        return map;
    }

    private UpdateResult updateGroup(Group group, Map<String, ApiMatch> apiMatches) {
        var finalCount = 0;
        var liveCount = 0;
        // Process group-stage matches
        for (var match : group.matches()) {
            var result = updateMatch(match, group, apiMatches);
            finalCount += result.finalCount();
            liveCount += result.liveCount();
        }
        // Process knockout matches (live scores + auto-results)
        for (var match : group.knockoutMatches()) {
            if (!match.teamsKnown()) continue; // skip unresolved bracket slots
            var result = updateMatch(match, group, apiMatches);
            finalCount += result.finalCount();
            liveCount += result.liveCount();
        }
        return new UpdateResult(finalCount, liveCount);
    }

    /** Process only knockout matches of a group (the current updateGroup also does this,
     *  but we need a separate pass AFTER bracket resolution to avoid the 15s delay). */
    private UpdateResult updateKnockoutMatches(Group group, Map<String, ApiMatch> apiMatches) {
        var finalCount = 0;
        var liveCount = 0;
        for (var match : group.knockoutMatches()) {
            if (!match.teamsKnown()) continue;
            var result = updateMatch(match, group, apiMatches);
            finalCount += result.finalCount();
            liveCount += result.liveCount();
        }
        return new UpdateResult(finalCount, liveCount);
    }

    /** Update a single match from API data. Returns (finalCount, liveCount) for this match. */
    private UpdateResult updateMatch(Match match, Group group, Map<String, ApiMatch> apiMatches) {
        var key = normalize(match.home()) + "|" + normalize(match.away());
        var apiMatch = apiMatches.get(key);
        if (apiMatch == null) return new UpdateResult(0, 0);

        // Sync kickoff from API data — API is the source of truth for dates
        if (apiMatch.dateTimeUtc != null) {
            try {
                var apiInstant = Instant.parse(apiMatch.dateTimeUtc);
                if (!apiInstant.equals(match.kickoff())) {
                    match.setKickoff(apiInstant);
                    LOG.info("📅 {} vs {} — kickoff corrected", match.home(), match.away());
                }
            } catch (Exception e) {
                // skip if parsing fails — not critical
            }
        }

        // Match hasn't started yet in the API — no scores to process (homeGoals stays null).
        // Kickoff was already synced above if available; nothing else to do.
        if (apiMatch.homeGoals == null) return new UpdateResult(0, 0);

        try {
            if (apiMatch.isFinal && !match.finished()) {
                group.registerResult(match.id(), apiMatch.homeGoals, apiMatch.awayGoals, apiMatch.advancingTeam);
                LOG.info("✓ {} {}–{} {}", match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away());
                return new UpdateResult(1, 0);
            } else if (apiMatch.isFinal && match.finished()) {
                var storedHome = match.homeGoals();
                var storedAway = match.awayGoals();
                if (storedHome == null || storedAway == null
                    || storedHome != apiMatch.homeGoals || storedAway != apiMatch.awayGoals) {
                    group.registerResult(match.id(), apiMatch.homeGoals, apiMatch.awayGoals, apiMatch.advancingTeam);
                    LOG.info("🔄 {} {}–{} {} (corrected from {}-{})",
                        match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away(),
                        storedHome == null ? "?" : storedHome,
                        storedAway == null ? "?" : storedAway);
                    return new UpdateResult(1, 0);
                }
                return new UpdateResult(0, 0);
            } else if (!apiMatch.isFinal && !match.finished()) {
                group.updateLiveScore(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                LOG.info("🔴 {} {}–{} {} (live)", match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away());
                return new UpdateResult(0, 1);
            } else if (!apiMatch.isFinal && match.finished()) {
                group.revertFinished(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                LOG.info("🔄 {} {}–{} {} (live, was incorrectly finished)", match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away());
                return new UpdateResult(0, 1);
            }
        } catch (Exception e) {
            LOG.error("Update failed for match {}: {}", match.id(), e.getMessage());
        }
        return new UpdateResult(0, 0);
    }

    /** Extract a specific result type from matchResults array, e.g. resultTypeID=2 (final) or =1 (live). */
    private Integer extractResult(JsonObject obj, int resultTypeId, String pointKey) {
        var results = obj.getAsJsonArray("matchResults");
        if (results == null) return null;
        for (var r : results) {
            var ro = r.getAsJsonObject();
            if (getInt(ro, "resultTypeID") == resultTypeId) {
                return getInt(ro, pointKey);
            }
        }
        return null;
    }

    private static String getString(JsonObject obj, String key) {
        var el = obj.get(key);
        return el == null || el.isJsonNull() ? null : el.getAsString();
    }

    private static int getInt(JsonObject obj, String key) {
        var el = obj.get(key);
        return el == null || el.isJsonNull() ? 0 : el.getAsInt();
    }

    private static boolean getBoolean(JsonObject obj, String key) {
        var el = obj.get(key);
        return el != null && !el.isJsonNull() && el.getAsBoolean();
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss").replace(" ", "").replace("-", "");
    }

    /** Result of a single group update pass. */
    private record UpdateResult(int finalCount, int liveCount) {}

    private static class ApiMatch {
        String dateTimeUtc;
        String team1;
        String team2;
        Integer homeGoals;
        Integer awayGoals;
        boolean isFinal;
        /** Who actually advanced (determined from regular-time winner or penalty shootout). */
        String advancingTeam;
    }
}
