package quinielamundial.domain;

import quinielamundial.service.WorldCupSchedule;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Group implements Serializable {
    private static final long serialVersionUID = 4L;

    private final String code;
    private final String name;
    private final Map<String, Member> members = new LinkedHashMap<>();
    private final List<Match> matches;
    private List<Match> knockoutMatches;
    private transient Runnable onChange;

    public Group(String code, String name, List<Match> matches) {
        this.code = code;
        this.name = name;
        this.matches = new ArrayList<>(matches);
    }

    public String code() { return code; }
    public String name() { return name; }
    public Map<String, Member> members() { return members; }

    /** 72 group-stage matches (IDs 1–72). */
    public List<Match> matches() { return matches; }

    /** 32 knockout matches (IDs 73–104). Lazy-initialised for backward compat with serialized data. */
    public List<Match> knockoutMatches() {
        if (knockoutMatches == null) {
            knockoutMatches = new ArrayList<>(WorldCupSchedule.allKnockoutMatches());
        }
        return knockoutMatches;
    }

    /** All matches: group stage + knockout combined (for scoring & lookup). */
    public List<Match> allMatches() {
        return Stream.concat(matches.stream(), knockoutMatches().stream())
            .collect(Collectors.toList());
    }

    /** Find a match by ID across both group and knockout matches. */
    public Match matchById(int matchId) {
        Objects.requireNonNull(knockoutMatches(), "knockoutMatches cannot be null");
        for (var m : matches) if (m.id() == matchId) return m;
        for (var m : knockoutMatches()) if (m.id() == matchId) return m;
        throw new IllegalArgumentException("Partido " + matchId + " no encontrado.");
    }

    public void onChange(Runnable onChange) { this.onChange = onChange; }
    public Member creator() { return members().values().stream().findFirst().orElseThrow(); }

    /** Removes a member by name. Throws if member doesn't exist or is the creator. */
    public void removeMember(String memberName) {
        if (!members.containsKey(memberName)) throw new IllegalArgumentException("El miembro '" + memberName + "' no existe en el grupo.");
        if (memberName.equals(creator().name())) throw new IllegalArgumentException("No puedes eliminar al creador del grupo.");
        members.remove(memberName);
        changed();
    }

    public Member join(String memberName) {
        if (memberName == null || memberName.isBlank()) throw new IllegalArgumentException("El nombre del usuario es obligatorio.");
        if (members.containsKey(memberName)) throw new IllegalArgumentException("El nombre '" + memberName + "' ya está en uso en este grupo.");
        var member = new Member(memberName);
        member.generateToken();
        members.put(memberName, member);
        changed();
        return member;
    }

    public Member requireByToken(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Token requerido.");
        return members.values().stream()
            .filter(m -> token.equals(m.token()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Token inválido. Necesitas unirte al grupo con el código de invitación."));
    }

    public void submitPrediction(String token, int matchId, int homeGoals, int awayGoals) {
        var member = requireByToken(token);
        var match = matchById(matchId);
        if (match.isStarted()) throw new IllegalStateException("El partido ya comenzó.");
        if (match.knockout() && !match.teamsKnown())
            throw new IllegalStateException("No se puede pronosticar un partido sin equipos asignados.");
        var star = !match.knockout() && member.starByJornada().getOrDefault(match.jornada(), -1) == matchId;
        member.predictions().put(matchId, new Prediction(homeGoals, awayGoals, star));
        changed();
    }

    public void setChampionBet(String token, String team) {
        var member = requireByToken(token);
        if (groupStageFinished()) throw new IllegalStateException("La fase de grupos ya terminó, no puedes cambiar la apuesta al campeón.");
        member.championBet(team);
        changed();
    }

    public void setStarMatch(String token, int jornada, int matchId) {
        var member = requireByToken(token);
        var match = matchById(matchId);
        if (match.jornada() != jornada) throw new IllegalArgumentException("La jornada no coincide.");
        if (match.isStarted()) throw new IllegalStateException("El Partido Estrella ya quedó bloqueado.");
        // Only locked if the user already has a star for this jornada AND that specific match has started
        var previousStarId = member.starByJornada().get(jornada);
        if (previousStarId != null && matchById(previousStarId).isStarted())
            throw new IllegalStateException("Tu Partido Estrella ya comenzó, no puedes cambiarlo.");
        var previousMatchId = member.starByJornada().get(jornada);
        if (previousMatchId != null && member.predictions().containsKey(previousMatchId)) {
            var previous = member.predictions().get(previousMatchId);
            member.predictions().put(previousMatchId, new Prediction(previous.homeGoals(), previous.awayGoals(), false));
        }
        member.starByJornada().put(jornada, matchId);
        var existing = member.predictions().get(matchId);
        if (existing != null) member.predictions().put(matchId, new Prediction(existing.homeGoals(), existing.awayGoals(), true));
        changed();
    }

    public void registerResult(int matchId, int homeGoals, int awayGoals) {
        if (homeGoals < 0 || awayGoals < 0) throw new IllegalArgumentException("El resultado no puede ser negativo.");
        matchById(matchId).finish(homeGoals, awayGoals);
        changed();
    }

    /** Update live (in-progress) score. Does NOT trigger persistence. */
    public void updateLiveScore(int matchId, int homeGoals, int awayGoals) {
        matchById(matchId).updateLiveScore(homeGoals, awayGoals);
    }

    /**
     * Revert a finished match back to in-progress and set live score.
     * Used when a match was incorrectly marked as finished (e.g. by old parsing bug).
     * Triggers persistence so the fixed state survives restarts.
     */
    public void revertFinished(int matchId, int liveHomeGoals, int liveAwayGoals) {
        var match = matchById(matchId);
        match.unfinish();
        match.updateLiveScore(liveHomeGoals, liveAwayGoals);
        changed();
    }

    public List<RankingEntry> leaderboard(String championTeam) {
        var sorted = members.values().stream()
            .map(member -> new RankingEntry(member, computeScore(member, championTeam), 0))
            .sorted(Comparator.comparing((RankingEntry e) -> e.score().totalPoints()).reversed()
                .thenComparing(e -> e.score().exactHits(), Comparator.reverseOrder())
                .thenComparing(e -> e.score().outcomeHits(), Comparator.reverseOrder())
                .thenComparing(e -> e.score().championHit(), Comparator.reverseOrder())
                .thenComparing(e -> e.member().name()))
            .collect(Collectors.toList());

        var ranked = new ArrayList<RankingEntry>();
        int rank = 0;
        ScoreBreakdown previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            if (previous == null || !previous.sameTieKey(entry.score())) rank = i + 1;
            ranked.add(new RankingEntry(entry.member(), entry.score(), rank));
            previous = entry.score();
        }
        return ranked;
    }

    public ScoreBreakdown score(Member member, String championTeam) { return computeScore(member, championTeam); }

    private ScoreBreakdown computeScore(Member member, String championTeam) {
        var total = 0;
        var exactHits = 0;
        var outcomeHits = 0;
        for (var match : allMatches()) {
            if (!match.finished()) continue;
            var prediction = member.predictions().get(match.id());
            if (prediction == null) continue;
            var points = scoreMatch(match, prediction);
            if (points == 3) exactHits++;
            else if (points == 1) outcomeHits++;
            // Star match only applies to group stage (not KO)
            if (!match.knockout() && member.starByJornada().getOrDefault(match.jornada(), -1) == match.id())
                points *= 2;
            total += points;
        }
        var championHit = member.championBet() != null && member.championBet().equals(championTeam);
        if (championHit) total += 10;
        return new ScoreBreakdown(total, exactHits, outcomeHits, championHit);
    }

    private int scoreMatch(Match match, Prediction prediction) {
        if (!match.finished()) return 0;
        if (prediction.homeGoals() == match.homeGoals() && prediction.awayGoals() == match.awayGoals()) return 3;
        var actual = match.result();
        if (actual != null && prediction.outcome().equals(actual)) return 1;
        return 0;
    }

    /** Whether all 72 group-stage matches have finished. */
    public boolean groupStageFinished() {
        return matches.stream().allMatch(Match::finished);
    }

    /** Get all knockout matches for a specific round constant (ROUND_R32 … ROUND_FIN). */
    public List<Match> matchesForRound(int round) {
        return knockoutMatches().stream()
            .filter(m -> m.round() == round)
            .collect(Collectors.toList());
    }

    /**
     * Compute score for a member excluding finished matches whose IDs
     * are in {@code excludeIds}. Champion bet is still included.
     */
    private ScoreBreakdown computeScoreExcluding(Member member, String championTeam, java.util.Set<Integer> excludeIds) {
        var total = 0;
        var exactHits = 0;
        var outcomeHits = 0;
        for (var match : allMatches()) {
            if (!match.finished() || excludeIds.contains(match.id())) continue;
            var prediction = member.predictions().get(match.id());
            if (prediction == null) continue;
            var points = scoreMatch(match, prediction);
            if (points == 3) exactHits++;
            else if (points == 1) outcomeHits++;
            if (!match.knockout() && member.starByJornada().getOrDefault(match.jornada(), -1) == match.id())
                points *= 2;
            total += points;
        }
        var championHit = member.championBet() != null && member.championBet().equals(championTeam);
        if (championHit) total += 10;
        return new ScoreBreakdown(total, exactHits, outcomeHits, championHit);
    }

    /** Leaderboard excluding finished matches with the given IDs. */
    private List<RankingEntry> leaderboardExcluding(java.util.Set<Integer> excludeMatchIds, String championTeam) {
        var sorted = members.values().stream()
            .map(m -> new RankingEntry(m, computeScoreExcluding(m, championTeam, excludeMatchIds), 0))
            .sorted(Comparator.comparing((RankingEntry e) -> e.score().totalPoints()).reversed()
                .thenComparing(e -> e.score().exactHits(), Comparator.reverseOrder())
                .thenComparing(e -> e.score().outcomeHits(), Comparator.reverseOrder())
                .thenComparing(e -> e.score().championHit(), Comparator.reverseOrder())
                .thenComparing(e -> e.member().name()))
            .collect(Collectors.toList());

        var ranked = new ArrayList<RankingEntry>();
        int rank = 0;
        ScoreBreakdown previous = null;
        for (int i = 0; i < sorted.size(); i++) {
            var entry = sorted.get(i);
            if (previous == null || !previous.sameTieKey(entry.score())) rank = i + 1;
            ranked.add(new RankingEntry(entry.member(), entry.score(), rank));
            previous = entry.score();
        }
        return ranked;
    }

    /**
     * Compute momentum highlights for a knockout round.
     *
     * @param round       One of {@link Match#ROUND_R32} … {@link Match#ROUND_FIN}
     * @param championTeam Current tournament champion (may be null)
     * @return RoundMomentum with highlights, or placeholders if no matches finished yet
     */
    public RoundMomentum computeRoundMomentum(int round, String championTeam) {
        var roundMatches = matchesForRound(round);
        var roundMatchIds = roundMatches.stream().map(Match::id).collect(Collectors.toSet());
        var complete = roundMatches.stream().allMatch(Match::finished);
        var anyFinished = roundMatches.stream().anyMatch(Match::finished);

        String roundName = switch (round) {
            case Match.ROUND_R32 -> "Dieciseisavos";
            case Match.ROUND_R16 -> "Octavos";
            case Match.ROUND_QF  -> "Cuartos";
            case Match.ROUND_SF  -> "Semifinales";
            case Match.ROUND_3RD -> "Tercer puesto";
            case Match.ROUND_FIN -> "Final";
            default -> "";
        };

        if (!anyFinished) {
            return new RoundMomentum(roundName, false, false, null, null, null, null);
        }

        // Per-member: points and exact hits in this round
        var memberRoundStats = new java.util.LinkedHashMap<String, int[]>();
        for (var member : members.values()) {
            var roundPoints = 0;
            var exactHits = 0;
            for (var match : roundMatches) {
                if (!match.finished()) continue;
                var p = member.predictions().get(match.id());
                if (p == null) continue;
                var pts = scoreMatch(match, p);
                if (pts == 3) exactHits++;
                roundPoints += pts;
            }
            memberRoundStats.put(member.name(), new int[]{roundPoints, exactHits});
        }

        // Ranking before round (excluding round matches)
        var beforeLeaderboard = leaderboardExcluding(roundMatchIds, championTeam);
        var beforeRanks = new java.util.HashMap<String, Integer>();
        for (var entry : beforeLeaderboard) {
            beforeRanks.put(entry.member().name(), entry.rank());
        }

        // Ranking after round (full)
        var afterLeaderboard = leaderboard(championTeam);
        var afterRanks = new java.util.HashMap<String, Integer>();
        for (var entry : afterLeaderboard) {
            afterRanks.put(entry.member().name(), entry.rank());
        }

        // Find biggest climber and faller by position delta
        String climberName = null, fallerName = null;
        int maxClimb = 0, maxFall = 0;
        for (var name : members.keySet()) {
            var before = beforeRanks.getOrDefault(name, members.size());
            var after  = afterRanks.getOrDefault(name, members.size());
            var delta  = before - after; // positive = climbed
            if (delta > maxClimb) { maxClimb = delta; climberName = name; }
            if (delta < maxFall)  { maxFall  = delta; fallerName  = name; }
        }

        var climber = climberName != null
            ? new RoundMomentum.Climber(climberName, maxClimb, memberRoundStats.get(climberName)[0])
            : null;
        var faller = fallerName != null
            ? new RoundMomentum.Faller(fallerName, -maxFall, memberRoundStats.get(fallerName)[0])
            : null;

        // Most exact hits in round
        String mostExactName = null;
        int mostExactCount = 0;
        for (var entry : memberRoundStats.entrySet()) {
            if (entry.getValue()[1] > mostExactCount) {
                mostExactCount = entry.getValue()[1];
                mostExactName = entry.getKey();
            }
        }
        var mostExact = mostExactName != null && mostExactCount > 0
            ? new RoundMomentum.MostExact(mostExactName, mostExactCount)
            : null;

        // Unique hit: finished match with the fewest exact predictors
        String uniqueMember = null, uniqueHome = null, uniqueAway = null;
        int uniqueHomeGoals = 0, uniqueAwayGoals = 0, uniquePredictors = Integer.MAX_VALUE;

        for (var match : roundMatches) {
            if (!match.finished()) continue;
            var exactCount = 0;
            String lastExactName = null;
            for (var member : members.values()) {
                var p = member.predictions().get(match.id());
                if (p != null && p.homeGoals() == match.homeGoals() && p.awayGoals() == match.awayGoals()) {
                    exactCount++;
                    lastExactName = member.name();
                }
            }
            if (exactCount > 0 && exactCount < uniquePredictors) {
                uniquePredictors = exactCount;
                uniqueMember = lastExactName;
                uniqueHome = match.home();
                uniqueAway = match.away();
                uniqueHomeGoals = match.homeGoals();
                uniqueAwayGoals = match.awayGoals();
            }
        }

        var uniqueHit = uniqueMember != null
            ? new RoundMomentum.UniqueHit(uniqueMember, uniqueHome, uniqueAway, uniqueHomeGoals, uniqueAwayGoals, uniquePredictors)
            : null;

        return new RoundMomentum(roundName, complete, true, climber, faller, uniqueHit, mostExact);
    }

    private void changed() { if (onChange != null) onChange.run(); }
}
