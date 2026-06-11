package quinielamundial.domain;

import java.io.Serializable;

public record ScoreBreakdown(int totalPoints, int exactHits, int outcomeHits, boolean championHit) implements Serializable {
    private static final long serialVersionUID = 3L;
    public boolean sameTieKey(ScoreBreakdown other) {
        return totalPoints == other.totalPoints && exactHits == other.exactHits && championHit == other.championHit;
    }
}
