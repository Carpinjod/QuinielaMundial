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
    private Map<Integer, Integer> advancingPicks = new HashMap<>();
    private String championBet;
    private String token;
    private String email; // nullable — for email notifications

    public Member(String name) { this.name = name; }
    public String name() { return name; }
    public Map<Integer, Prediction> predictions() { return predictions; }
    public Map<Integer, Integer> starByJornada() { return starByJornada; }
    public Map<Integer, Integer> advancingPicks() {
        if (advancingPicks == null) advancingPicks = new HashMap<>();
        return advancingPicks;
    }
    public String championBet() { return championBet; }
    public void championBet(String championBet) { this.championBet = championBet; }
    public String token() { return token; }
    public void generateToken() { this.token = UUID.randomUUID().toString().replace("-", "").substring(0, 16); }
    public String email() { return email; }
    public void email(String email) { this.email = email; }
}
