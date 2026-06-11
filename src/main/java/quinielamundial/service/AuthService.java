package quinielamundial.service;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthService {
    private final Map<String, String> users = new HashMap<>();      // username → sha256(password)
    private final Map<String, String> sessions = new HashMap<>();   // sessionId → username
    private final Path authFile;

    public AuthService(Path dataDir) {
        this.authFile = dataDir.resolve("quiniela-auth.txt");
        load();
    }

    // ── Public API ──

    public boolean register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) return false;
        if (users.containsKey(username)) return false;
        users.put(username, hash(password));
        save();
        return true;
    }

    /** Returns sessionId on success, null on failure. */
    public String login(String username, String password) {
        var expected = users.get(username);
        if (expected == null || !expected.equals(hash(password))) return null;
        var sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, username);
        save();
        return sessionId;
    }

    /** Returns the username if session is valid, null otherwise. */
    public String validateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return null;
        return sessions.get(sessionId);
    }

    public void logout(String sessionId) {
        if (sessionId != null) sessions.remove(sessionId);
        save();
    }

    public boolean userExists(String username) {
        return users.containsKey(username);
    }

    public boolean changePassword(String username, String currentPassword, String newPassword) {
        var expected = users.get(username);
        if (expected == null || !expected.equals(hash(currentPassword))) return false;
        if (newPassword == null || newPassword.length() < 4) return false;
        users.put(username, hash(newPassword));
        save();
        return true;
    }

    // ── Internal ──

    private static String hash(String password) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (var b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ── Persistence (separate file, independent from game state) ──

    private void save() {
        try {
            Files.createDirectories(authFile.getParent());
            try (var out = new ObjectOutputStream(Files.newOutputStream(authFile))) {
                out.writeObject(new AuthSnapshot(new HashMap<>(users), new HashMap<>(sessions)));
            }
        } catch (IOException e) {
            // Auth state is not critical — fail silently
        }
    }

    private void load() {
        if (!Files.exists(authFile)) return;
        try (var in = new ObjectInputStream(Files.newInputStream(authFile))) {
            var snap = (AuthSnapshot) in.readObject();
            users.putAll(snap.users);
            sessions.putAll(snap.sessions);
        } catch (Exception e) {
            // Corrupted or old format — start fresh
        }
    }

    private record AuthSnapshot(Map<String, String> users, Map<String, String> sessions) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
