package quinielamundial.notification;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * One-time setup utility to obtain a Gmail API refresh token.
 * <p>
 * Usage: run once on a machine with a browser.
 * 1. Create a Google Cloud project and enable Gmail API
 * 2. Create OAuth 2.0 credentials (Desktop application type)
 * 3. Set env vars GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET
 * 4. Run this class: it will open your browser and catch the callback
 * 5. Copy the printed refresh token into GMAIL_REFRESH_TOKEN secret
 */
public class GmailAuthSetup {

    private static final Gson GSON = new Gson();
    private static final int PORT = 8888;
    private static final String SCOPE = "https://mail.google.com/";
    private static final HttpClient http = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        var clientId = System.getenv("GMAIL_CLIENT_ID");
        var clientSecret = System.getenv("GMAIL_CLIENT_SECRET");

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            System.err.println("ERROR: Set GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET env vars first.");
            System.err.println();
            System.err.println("1. Go to https://console.cloud.google.com/");
            System.err.println("2. Create a project (or select existing)");
            System.err.println("3. Enable Gmail API");
            System.err.println("4. Create OAuth consent screen (External, add your email as test user)");
            System.err.println("5. Create OAuth 2.0 credentials (Desktop application type)");
            System.err.println("6. Copy Client ID and Client Secret");
            System.exit(1);
        }

        // Build the authorization URL
        var redirectUri = "http://localhost:" + PORT;
        var authUrl = "https://accounts.google.com/o/oauth2/v2/auth"
            + "?client_id=" + urlEncode(clientId)
            + "&redirect_uri=" + urlEncode(redirectUri)
            + "&response_type=code"
            + "&scope=" + urlEncode(SCOPE)
            + "&access_type=offline"
            + "&prompt=consent"; // Force refresh token even if already authorized

        var codeFuture = new CompletableFuture<String>();

        // Start a temporary HTTP server to catch the callback
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", exchange -> {
            var query = exchange.getRequestURI().getQuery();
            var code = extractParam(query, "code");
            var error = extractParam(query, "error");
            if (error != null) {
                codeFuture.completeExceptionally(new RuntimeException("Authorization error: " + error));
            } else if (code != null) {
                codeFuture.complete(code);
            } else {
                codeFuture.completeExceptionally(new RuntimeException("No code in callback"));
            }
            var response = "<html><body><h1>Autorizado!</h1><p>Ya puedes cerrar esta ventana.</p></body></html>";
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, response.getBytes(StandardCharsets.UTF_8).length);
            try (var os = exchange.getResponseBody()) {
                os.write(response.getBytes(StandardCharsets.UTF_8));
            }
        });
        server.start();

        System.out.println("=== Gmail API OAuth2 Setup ===");
        System.out.println();
        System.out.println("Opening browser to authorize access...");
        System.out.println("Auth URL: " + authUrl);
        System.out.println();

        // Open the browser
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(new URI(authUrl));
        } else {
            System.out.println("COPY AND PASTE THIS URL IN YOUR BROWSER:");
            System.out.println(authUrl);
        }

        System.out.println("Waiting for authorization on http://localhost:" + PORT + " ...");
        System.out.println();

        try {
            var authCode = codeFuture.get(5, TimeUnit.MINUTES);

            System.out.println("Authorization code received. Exchanging for tokens...");

            // Exchange auth code for tokens
            var tokenBody = "code=" + urlEncode(authCode)
                + "&client_id=" + urlEncode(clientId)
                + "&client_secret=" + urlEncode(clientSecret)
                + "&redirect_uri=" + urlEncode(redirectUri)
                + "&grant_type=authorization_code";

            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://oauth2.googleapis.com/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(tokenBody))
                .build();

            var response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Token exchange failed: " + response.statusCode());
                System.err.println(response.body());
                System.exit(1);
            }

            var json = GSON.fromJson(response.body(), JsonObject.class);
            var refreshToken = json.get("refresh_token").getAsString();
            var accessToken = json.get("access_token").getAsString();

            System.out.println("=== SUCCESS! ===");
            System.out.println();
            System.out.println("Add this as a GitHub secret named GMAIL_REFRESH_TOKEN:");
            System.out.println();
            System.out.println(refreshToken);
            System.out.println();
            System.out.println("Your GMAIL_CLIENT_ID and GMAIL_CLIENT_SECRET are already set as env vars.");
            System.out.println("The access token (not stored): " + accessToken.substring(0, 20) + "...");
            System.out.println();
            System.out.println("After adding the secret, push to main to deploy.");

        } catch (java.util.concurrent.TimeoutException e) {
            System.err.println("Timed out waiting for authorization (5 minutes).");
            System.err.println("Run the utility again.");
            System.exit(1);
        } finally {
            server.stop(0);
        }
    }

    private static String extractParam(String query, String param) {
        if (query == null) return null;
        for (var part : query.split("&")) {
            var kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(param)) {
                return java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
