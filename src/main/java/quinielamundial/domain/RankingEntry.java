package quinielamundial.domain;

import java.io.Serializable;

public record RankingEntry(Member member, ScoreBreakdown score, int rank) implements Serializable { private static final long serialVersionUID = 1L; }
