package quinielamundial.domain;

public enum Outcome { HOME, AWAY, DRAW; public static Outcome fromScore(int homeGoals, int awayGoals) { if (homeGoals > awayGoals) return HOME; if (homeGoals < awayGoals) return AWAY; return DRAW; } }
