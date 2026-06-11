package quinielamundial.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import quinielamundial.domain.Group;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MatchUpdateService {
    private static final String API_URL = "https://api.openligadb.de/getmatchdata/wm26/2026";
    private static final long POLL_INTERVAL_SECONDS = 300; // 5 minutes
    private static final long MATCH_BUFFER_HOURS = 3;

    private final List<Group> groups;
    private final Runnable onBracketUpdate;
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

    public MatchUpdateService(List<Group> groups, Runnable onBracketUpdate) {
        this.groups = groups;
        this.onBracketUpdate = onBracketUpdate;
    }

    public void start() {
        System.out.println("MatchUpdateService: polling " + API_URL + " every " + POLL_INTERVAL_SECONDS + "s");
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
                System.err.println("MatchUpdateService: HTTP " + response.statusCode());
                return;
            }

            var apiMatches = parseApiMatches(response.body());
            if (apiMatches.isEmpty()) return;

            var updated = 0;
            for (var group : groups) {
                updated += updateGroup(group, apiMatches);
            }
            if (updated > 0) {
                onBracketUpdate.run();
                System.out.println("MatchUpdateService: updated " + updated + " matches, brackets re-resolved");
            }
        } catch (Exception e) {
            System.err.println("MatchUpdateService: " + e.getMessage());
        }
    }

    /** Parse the OpenLigaDB JSON array into a map keyed by (team1, team2, date). */
    private Map<String, ApiMatch> parseApiMatches(String json) {
        var map = new HashMap<String, ApiMatch>();
        try {
            var arr = gson.fromJson(json, JsonArray.class);
            var now = Instant.now();
            for (var elem : arr) {
                var obj = elem.getAsJsonObject();
                if (!isFinishedWithResult(obj)) continue;

                var apiMatch = new ApiMatch();
                apiMatch.dateTimeUtc = getString(obj, "matchDateTimeUTC");
                apiMatch.team1 = getString(obj.getAsJsonObject("team1"), "teamName");
                apiMatch.team2 = getString(obj.getAsJsonObject("team2"), "teamName");
                apiMatch.homeGoals = extractHomeGoals(obj);
                apiMatch.awayGoals = extractAwayGoals(obj);

                if (apiMatch.team1 == null || apiMatch.team2 == null) continue;
                apiMatch.team1 = GERMAN_TO_ENGLISH.getOrDefault(apiMatch.team1, apiMatch.team1);
                apiMatch.team2 = GERMAN_TO_ENGLISH.getOrDefault(apiMatch.team2, apiMatch.team2);

                // Key: normalized team names + date
                var key = normalize(apiMatch.team1) + "|" + normalize(apiMatch.team2) + "|" + dateOnly(apiMatch.dateTimeUtc);
                map.put(key, apiMatch);
            }
        } catch (Exception e) {
            System.err.println("MatchUpdateService: parse error: " + e.getMessage());
        }
        return map;
    }

    private int updateGroup(Group group, Map<String, ApiMatch> apiMatches) {
        var count = 0;
        var now = Instant.now();
        for (var match : group.matches()) {
            if (match.finished()) continue;
            // Only check matches that kicked off at least MATCH_BUFFER_HOURS ago
            if (match.kickoff().plusSeconds(MATCH_BUFFER_HOURS * 3600).isAfter(now)) continue;

            var key = normalize(match.home()) + "|" + normalize(match.away()) + "|" + dateOnly(match.kickoff().toString());
            var apiMatch = apiMatches.get(key);
            if (apiMatch == null) continue;

            try {
                group.registerResult(match.id(), apiMatch.homeGoals, apiMatch.awayGoals);
                count++;
                System.out.println("  ✓ " + match.home() + " " + apiMatch.homeGoals + "–" + apiMatch.awayGoals + " " + match.away());
            } catch (Exception e) {
                System.err.println("MatchUpdateService: update failed for match " + match.id() + ": " + e.getMessage());
            }
        }
        return count;
    }

    private boolean isFinishedWithResult(JsonObject obj) {
        if (!getBoolean(obj, "matchIsFinished")) return false;
        var results = obj.getAsJsonArray("matchResults");
        if (results == null) return false;
        for (var r : results) {
            var ro = r.getAsJsonObject();
            if (getInt(ro, "resultTypeID") == 2) return true;
        }
        return false;
    }

    private Integer extractHomeGoals(JsonObject obj) {
        var results = obj.getAsJsonArray("matchResults");
        if (results == null) return null;
        for (var r : results) {
            var ro = r.getAsJsonObject();
            if (getInt(ro, "resultTypeID") == 2) return getInt(ro, "pointsTeam1");
        }
        return null;
    }

    private Integer extractAwayGoals(JsonObject obj) {
        var results = obj.getAsJsonArray("matchResults");
        if (results == null) return null;
        for (var r : results) {
            var ro = r.getAsJsonObject();
            if (getInt(ro, "resultTypeID") == 2) return getInt(ro, "pointsTeam2");
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

    private static class ApiMatch {
        String dateTimeUtc;
        String team1;
        String team2;
        Integer homeGoals;
        Integer awayGoals;
    }
}
