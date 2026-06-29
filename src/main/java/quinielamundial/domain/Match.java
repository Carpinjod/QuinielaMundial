package quinielamundial.domain;

import java.io.Serializable;
import java.time.Instant;

public class Match implements Serializable {
    private static final long serialVersionUID = 3L;

    /** Round constants for knockout stages. */
    public static final int ROUND_GROUP = 0;
    public static final int ROUND_R32 = 1;
    public static final int ROUND_R16 = 2;
    public static final int ROUND_QF  = 3;
    public static final int ROUND_SF  = 4;
    public static final int ROUND_3RD = 5;
    public static final int ROUND_FIN = 6;

    private final int id;
    private final int jornada;
    private int round;   // 0=group, 1=R32, 2=R16, 3=QF, 4=SF, 5=3rd, 6=Final
    private String home;
    private String away;
    private Instant kickoff;
    /** @deprecated Kept only for backward-compat deserialization with old serialVersionUID=2 data. */
    @Deprecated
    private int groupRound;
    private Integer homeGoals;
    private Integer awayGoals;
    /** Who actually advanced (for KO matches decided in regular time, extra time, or penalties). */
    private String advancingTeam;
    private boolean finished;

    /** Live (in-progress) scores — NOT persisted, re-fetched from API on restart. */
    private transient boolean hasLiveScore;
    private transient int liveHomeGoals;
    private transient int liveAwayGoals;

    /** Constructor for group-stage matches (home & away known). */
    public Match(int id, int jornada, String home, String away, Instant kickoff) {
        this(id, jornada, ROUND_GROUP, home, away, kickoff);
    }

    /** Constructor for knockout matches — home/away may be null until bracket is resolved. */
    public Match(int id, int jornada, int round, String home, String away, Instant kickoff) {
        this.id = id; this.jornada = jornada; this.round = round;
        this.home = home; this.away = away; this.kickoff = kickoff;
    }

    public int id() { return id; }
    public int jornada() { return jornada; }
    public int round() { return round; }
    public String home() { return home; }
    public String away() { return away; }
    public Instant kickoff() { return kickoff; }
    public Integer homeGoals() { return homeGoals; }
    public Integer awayGoals() { return awayGoals; }
    public String advancingTeam() { return advancingTeam; }
    public void setAdvancingTeam(String team) { this.advancingTeam = team; }
    public boolean finished() { return finished; }
    public boolean knockout() { return round >= ROUND_R32; }

    /** Whether teams have been assigned to this slot. */
    public boolean teamsKnown() { return home != null && away != null; }

    /** Override kickoff time from API data (corrects hardcoded schedule errors). */
    public void setKickoff(Instant kickoff) {
        this.kickoff = kickoff;
    }

    /** Assign teams to a knockout match (used when bracket is resolved). */
    public void setTeams(String home, String away) {
        if (finished) throw new IllegalStateException("No se pueden cambiar los equipos de un partido ya finalizado.");
        this.home = home;
        this.away = away;
    }

    public boolean isStarted() { return finished || Instant.now().isAfter(kickoff); }

    /** Live score in progress (set from API poll, NOT persisted). */
    public boolean hasLiveScore() { return hasLiveScore; }
    public int liveHomeGoals() { return liveHomeGoals; }
    public int liveAwayGoals() { return liveAwayGoals; }

    /** Update the live (in-progress) score without marking the match as finished. */
    public void updateLiveScore(int homeGoals, int awayGoals) {
        this.liveHomeGoals = homeGoals;
        this.liveAwayGoals = awayGoals;
        this.hasLiveScore = true;
    }

    /** Revert a finished match back to in-progress (e.g. when a past bug recorded an incorrect result). */
    public void unfinish() {
        this.finished = false;
        this.homeGoals = null;
        this.awayGoals = null;
        this.hasLiveScore = false;
    }

    public void finish(int homeGoals, int awayGoals) {
        if (!teamsKnown() && round >= ROUND_R32)
            throw new IllegalStateException("No se puede finalizar un partido KO sin equipos asignados.");
        this.homeGoals = homeGoals; this.awayGoals = awayGoals; this.finished = true;
        this.hasLiveScore = false; // live score is superseded by final result
    }

    /** Winning team name, or null if not finished.
     *  For KO matches, returns the advancing team (regular time, extra time, or penalties).
     *  For group matches, returns null on draw (draw is a valid final result). */
    public String winner() {
        if (!finished) return null;
        if (advancingTeam != null) return advancingTeam;
        if (homeGoals > awayGoals) return home;
        if (awayGoals > homeGoals) return away;
        return null;
    }

    /** Returns "1" (home win), "X" (draw), or "2" (away win) if finished, null otherwise. */
    public String result() {
        if (!finished) return null;
        if (homeGoals > awayGoals) return "1";
        if (homeGoals < awayGoals) return "2";
        return "X";
    }
}
