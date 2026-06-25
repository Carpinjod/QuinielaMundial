package quinielamundial.notification;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import quinielamundial.logging.Logger;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Email sender using the Gmail REST API with OAuth 2.0.
 * <p>
 * Avoids SMTP IP-based blocks by authenticating via OAuth2 tokens instead.
 * Configured via env vars: GMAIL_CLIENT_ID, GMAIL_CLIENT_SECRET, GMAIL_REFRESH_TOKEN.
 * <p>
 * Falls back to {@link MailSender} if OAuth2 env vars are not set.
 */
public class GoogleMailSender implements Sender {

    private static final Logger LOG = new Logger("GoogleMailSender");
    private static final Gson GSON = new Gson();
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_API_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final String clientId;
    private final String clientSecret;
    private final String refreshToken;
    private final String fromAddress;

    private final HttpClient http = HttpClient.newHttpClient();
    private final ReentrantLock tokenLock = new ReentrantLock();

    private String accessToken;
    private long expiresAt; // epoch millis when the current token expires

    public GoogleMailSender() {
        this(
            System.getenv("GMAIL_CLIENT_ID"),
            System.getenv("GMAIL_CLIENT_SECRET"),
            System.getenv("GMAIL_REFRESH_TOKEN"),
            System.getenv().getOrDefault("SMTP_FROM", System.getenv("GMAIL_CLIENT_ID") != null ? "me" : "")
        );
    }

    public GoogleMailSender(String clientId, String clientSecret, String refreshToken, String fromAddress) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.refreshToken = refreshToken;
        this.fromAddress = fromAddress;
    }

    /** True if OAuth2 credentials are configured. */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
            && clientSecret != null && !clientSecret.isBlank()
            && refreshToken != null && !refreshToken.isBlank();
    }

    /** Send an email via Gmail API. Returns silently on success; logs errors. */
    public void send(String to, String subject, String body) {
        if (!isConfigured()) {
            LOG.error("Gmail OAuth2 not configured — cannot send email to {}", to);
            return;
        }

        try {
            var token = getAccessToken();
            var mimeMessage = buildMimeMessage(to, subject, body);
            var encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mimeMessage.getBytes(StandardCharsets.UTF_8));
            var payload = GSON.toJson(java.util.Map.of("raw", encoded));

            var request = HttpRequest.newBuilder()
                .uri(URI.create(GMAIL_API_URL))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                LOG.info("Email sent to {} via Gmail API", to);
            } else {
                LOG.error("Gmail API returned {}: {}", response.statusCode(), truncate(response.body(), 200));
                // If 401, force token refresh on next attempt
                if (response.statusCode() == 401) {
                    tokenLock.lock();
                    try {
                        accessToken = null;
                    } finally {
                        tokenLock.unlock();
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Failed to send email via Gmail API to {}: {}", to, e.getMessage());
        }
    }

    // ── Private helpers ──

    /** Get a valid access token, refreshing if necessary. */
    private String getAccessToken() {
        tokenLock.lock();
        try {
            if (accessToken != null && System.currentTimeMillis() < expiresAt - 60_000) {
                return accessToken;
            }
            // Refresh the token
            var body = "client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&refresh_token=" + urlEncode(refreshToken)
                + "&grant_type=refresh_token";

            var request = HttpRequest.newBuilder()
                .uri(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Token refresh failed: " + response.statusCode()
                    + " " + truncate(response.body(), 200));
            }

            var json = GSON.fromJson(response.body(), JsonObject.class);
            accessToken = json.get("access_token").getAsString();
            var expiresIn = json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600;
            expiresAt = System.currentTimeMillis() + expiresIn * 1000L;
            return accessToken;

        } catch (Exception e) {
            LOG.error("Failed to refresh access token: {}", e.getMessage());
            throw new RuntimeException("Cannot obtain access token", e);
        } finally {
            tokenLock.unlock();
        }
    }

    /** Build a RFC 2822 MIME message. Uses Jakarta Mail for construction. */
    private String buildMimeMessage(String to, String subject, String body) throws Exception {
        var props = new Properties();
        var session = Session.getInstance(props);
        var message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromAddress));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
        message.setSubject(subject);
        message.setText(body);
        // Write to a byte array, return as UTF-8 string
        var baos = new java.io.ByteArrayOutputStream();
        message.writeTo(baos);
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
