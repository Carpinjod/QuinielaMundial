package quinielamundial.domain;

/**
 * Momentum highlights for a knockout round.
 *
 * Shows the biggest climber and faller in the ranking (by positions gained/lost
 * during the round), the most unique correct prediction (the match that fewest
 * members predicted exactly), and who had the most exact score predictions.
 */
public record RoundMomentum(
    String roundName,
    boolean complete,
    boolean anyFinished,
    Climber climber,
    Faller faller,
    UniqueHit uniqueHit,
    MostExact mostExact
) {
    /** Member who gained the most ranking positions during this round. */
    public record Climber(String memberName, int positionsClimbed, int roundPoints) {}

    /** Member who lost the most ranking positions during this round. */
    public record Faller(String memberName, int positionsFell, int roundPoints) {}

    /** The match that fewest members predicted exactly, and who got it right. */
    public record UniqueHit(String memberName, String homeTeam, String awayTeam, int homeGoals, int awayGoals, int predictorsCount) {}

    /** Member with the most exact-score predictions in this round. */
    public record MostExact(String memberName, int exactCount) {}
}
