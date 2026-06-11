package quinielamundial.domain;

import java.io.Serializable;
import java.time.Instant;

public class Match implements Serializable {
    private static final long serialVersionUID = 2L;

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
    private boolean finished;

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
    public boolean finished() { return finished; }
    public boolean knockout() { return round >= ROUND_R32; }

    /** Whether teams have been assigned to this slot. */
    public boolean teamsKnown() { return home != null && away != null; }

    /** Assign teams to a knockout match (used when bracket is resolved). */
    public void setTeams(String home, String away) {
        if (finished) throw new IllegalStateException("No se pueden cambiar los equipos de un partido ya finalizado.");
        this.home = home;
        this.away = away;
    }

    public boolean isStarted() { return finished || Instant.now().isAfter(kickoff); }
    public void finish(int homeGoals, int awayGoals) {
        if (!teamsKnown() && round >= ROUND_R32)
            throw new IllegalStateException("No se puede finalizar un partido KO sin equipos asignados.");
        this.homeGoals = homeGoals; this.awayGoals = awayGoals; this.finished = true;
    }

    /** Winning team name, or null if not finished / draw (KO can't end in draw). */
    public String winner() {
        if (!finished) return null;
        if (homeGoals > awayGoals) return home;
        if (awayGoals > homeGoals) return away;
        return null; // KO matches go to extra time / pens, but we treat draw as no-winner
    }

    /** Returns "1" (home win), "X" (draw), or "2" (away win) if finished, null otherwise. */
    public String result() {
        if (!finished) return null;
        if (homeGoals > awayGoals) return "1";
        if (homeGoals < awayGoals) return "2";
        return "X";
    }
}
