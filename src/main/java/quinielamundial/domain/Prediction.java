package quinielamundial.domain;

import java.io.Serializable;

public record Prediction(int homeGoals, int awayGoals, boolean star) implements Serializable {
    private static final long serialVersionUID = 3L;
    public Prediction {
        if (homeGoals < 0) throw new IllegalArgumentException("Los goles locales no pueden ser negativos.");
        if (awayGoals < 0) throw new IllegalArgumentException("Los goles visitantes no pueden ser negativos.");
    }

    /** Returns "1" (home win), "X" (draw), or "2" (away win) based on predicted scores. */
    public String outcome() {
        if (homeGoals > awayGoals) return "1";
        if (homeGoals < awayGoals) return "2";
        return "X";
    }
}
