package quinielamundial.domain;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Member implements Serializable {
    private static final long serialVersionUID = 2L;

    private final String name;
    private final Map<Integer, Prediction> predictions = new HashMap<>();
    private final Map<Integer, Integer> starByJornada = new HashMap<>();
    private String championBet;
    private String token;
    private String email; // nullable — for email notifications

    /**
     * Method predictions for knockout matches (matchId → "REGULAR", "EXTRA_TIME",
     * or "PENALTIES"). Nullable — null means "not predicted" (no bonus).
     * Backward-compat: old serialized data won't have this field (lazy init).
     */
    private Map<Integer, String> knockoutMethods;

    public Member(String name) { this.name = name; }
    public String name() { return name; }
    public Map<Integer, Prediction> predictions() { return predictions; }
    public Map<Integer, Integer> starByJornada() { return starByJornada; }
    public String championBet() { return championBet; }
    public void championBet(String championBet) { this.championBet = championBet; }
    public String token() { return token; }
    public void generateToken() { this.token = UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    public String email() { return email; }
    public void email(String email) { this.email = email; }

    /** Get method prediction for a knockout match, or null if not predicted. */
    public String knockoutMethod(int matchId) {
        if (knockoutMethods == null) knockoutMethods = new HashMap<>();
        return knockoutMethods.get(matchId);
    }

    /** Set method prediction for a knockout match. Pass null to clear. */
    public void knockoutMethod(int matchId, String method) {
        if (knockoutMethods == null) knockoutMethods = new HashMap<>();
        if (method == null) knockoutMethods.remove(matchId);
        else knockoutMethods.put(matchId, method);
    }

    /** All method predictions (matchId → method). Backed by lazy-init map. */
    public Map<Integer, String> knockoutMethods() {
        if (knockoutMethods == null) knockoutMethods = new HashMap<>();
        return knockoutMethods;
    }
}
