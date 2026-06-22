package quinielamundial.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import quinielamundial.logging.Logger;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Server-Sent Events hub for live score updates.
 * <p>
 * Browsers connect via {@code GET /api/scores/sse} and receive
 * {@code data: [...]} messages whenever scores change.
 * Connection is kept open; the {@link EventSource} JS API auto-reconnects on drop.
 */
public class ScoreStream {

    private static final Logger LOG = new Logger("ScoreStream");
    private static final Gson GSON = new Gson();
    private static final long HEARTBEAT_MS = 30_000;

    private final List<HttpExchange> clients = new CopyOnWriteArrayList<>();

    /** Subscribe a new SSE client. Blocks until the client disconnects. */
    public void subscribe(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.add("Content-Type", "text/event-stream");
        headers.add("Cache-Control", "no-cache");
        headers.add("Connection", "keep-alive");
        try {
            exchange.sendResponseHeaders(200, 0); // chunked
        } catch (IOException e) {
            LOG.error("SSE subscribe failed: {}", e.getMessage());
            return;
        }

        clients.add(exchange);
        LOG.info("SSE client connected ({} total)", clients.size());

        var os = exchange.getResponseBody();
        try {
            // Heartbeat loop — keeps connection alive, detects drops
            while (true) {
                Thread.sleep(HEARTBEAT_MS);
                try {
                    os.write(":\n\n".getBytes()); // SSE comment (heartbeat)
                    os.flush();
                } catch (IOException e) {
                    break; // client disconnected
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } finally {
            clients.remove(exchange);
            LOG.info("SSE client disconnected ({} remaining)", clients.size());
            try { os.close(); } catch (IOException ignored) {}
        }
    }

    /** True if at least one client is connected to this stream. */
    public boolean hasClients() {
        return !clients.isEmpty();
    }

    /** Send a score payload to every connected client. */
    public void broadcast(String jsonPayload) {
        if (clients.isEmpty()) return;
        var frame = ("data: " + jsonPayload + "\n\n").getBytes();
        for (var client : clients) {
            try {
                var os = client.getResponseBody();
                os.write(frame);
                os.flush();
            } catch (IOException e) {
                clients.remove(client);
            }
        }
    }
}
