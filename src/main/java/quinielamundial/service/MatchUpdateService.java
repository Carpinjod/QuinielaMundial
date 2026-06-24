package quinielamundial.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import quinielamundial.domain.Group;
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
    private static final long POLL_INTERVAL_SECONDS = 60; // 1 minute

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
        scheduler.scheduleAtFixedRate(this::poll, 10, POLL_INTERVAL_SECONDS, TimeUnit.SECONDS);
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

            for (var group : groups) {
                var result = updateGroup(group, apiMatches);
                if (result.finalCount > 0) finalGroups.add(group);
                if (result.liveCount > 0) liveGroups.add(group);
            }

            if (!finalGroups.isEmpty()) {
                onScoresUpdated.accept(finalGroups);
                LOG.info("Final results: {} groups updated", finalGroups.size());
            }
            if (!liveGroups.isEmpty()) {
                onLiveScoresUpdated.accept(liveGroups);
                LOG.info("Live scores: {} groups updated", liveGroups.size());
            }
        } catch (Exception e) {
            LOG.error("Poll failed: {}", e.getMessage());
        }
    }

    /** Parse the OpenLigaDB JSON array into a map keyed by (team1, team2, date).
     *  Extracts both final results (resultTypeID=2) and live scores (resultTypeID=1). */
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

                var key = normalize(team1) + "|" + normalize(team2) + "|" + dateOnly(dateTimeUtc);

                // 1. Final result: only when matchIsFinished=true (resultTypeID=2 = "Endergebnis")
                var matchIsFinished = getBoolean(obj, "matchIsFinished");
                if (matchIsFinished) {
                    Integer finalHome = extractResult(obj, 2, "pointsTeam1");
                    Integer finalAway = extractResult(obj, 2, "pointsTeam2");
                    if (finalHome != null && finalAway != null) {
                        var apiMatch = new ApiMatch();
                        apiMatch.dateTimeUtc = dateTimeUtc;
                        apiMatch.team1 = team1;
                        apiMatch.team2 = team2;
                        apiMatch.homeGoals = finalHome;
                        apiMatch.awayGoals = finalAway;
                        apiMatch.isFinal = true;
                        map.put(key, apiMatch);
                        continue;
                    }
                }

                // 2. Live / in-progress result: resultTypeID=1 ("Halbzeit") as current score
                if (!matchIsFinished) {
                    Integer liveHome = extractResult(obj, 1, "pointsTeam1");
                    Integer liveAway = extractResult(obj, 1, "pointsTeam2");
                    if (liveHome != null && liveAway != null) {
                        var apiMatch = new ApiMatch();
                        apiMatch.dateTimeUtc = dateTimeUtc;
                        apiMatch.team1 = team1;
                        apiMatch.team2 = team2;
                        apiMatch.homeGoals = liveHome;
                        apiMatch.awayGoals = liveAway;
                        apiMatch.isFinal = false;
                        map.put(key, apiMatch);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Parse error: {}", e.getMessage());
        }
        return map;
    }

    private UpdateResult updateGroup(Group group, Map<String, ApiMatch> apiMatches) {
        var finalCount = 0;
        var liveCount = 0;
        for (var match : group.matches()) {
            var key = normalize(match.home()) + "|" + normalize(match.away()) + "|" + dateOnly(match.kickoff().toString());
            var apiMatch = apiMatches.get(key);
            if (apiMatch == null) continue;

            try {
                if (apiMatch.isFinal && !match.finished()) {
                    group.registerResult(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                    finalCount++;
                    LOG.info("✓ {} {}–{} {}", match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away());
                } else if (apiMatch.isFinal && match.finished()) {
                    // Match already marked as finished — verify scores match (old bug may have
                    // recorded wrong result, e.g. halftime 0-0 as final). Re-register if different.
                    var storedHome = match.homeGoals();
                    var storedAway = match.awayGoals();
                    if (storedHome == null || storedAway == null
                        || storedHome != apiMatch.homeGoals || storedAway != apiMatch.awayGoals) {
                        group.registerResult(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                        finalCount++;
                        LOG.info("🔄 {} {}–{} {} (corrected from {}-{})",
                            match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away(),
                            storedHome == null ? "?" : storedHome,
                            storedAway == null ? "?" : storedAway);
                    }
                } else if (!apiMatch.isFinal && !match.finished()) {
                    group.updateLiveScore(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                    liveCount++;
                    LOG.info("🔴 {} {}–{} {} (live)", match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away());
                } else if (!apiMatch.isFinal && match.finished()) {
                    // Match was incorrectly marked as finished by previous bug (old parsing treated
                    // resultTypeID=2 as final even when matchIsFinished=false). Revert and apply live score.
                    group.revertFinished(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                    liveCount++;
                    LOG.info("🔄 {} {}–{} {} (live, was incorrectly finished)", match.home(), apiMatch.homeGoals, apiMatch.awayGoals, match.away());
                }
            } catch (Exception e) {
                LOG.error("Update failed for match {}: {}", match.id(), e.getMessage());
            }
        }
        return new UpdateResult(finalCount, liveCount);
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

    /** Extract just YYYY-MM-DD from an ISO datetime string. */
    private static String dateOnly(String iso) {
        if (iso == null) return "";
        return iso.length() >= 10 ? iso.substring(0, 10) : iso;
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
    }
}
