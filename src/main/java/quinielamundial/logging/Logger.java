package quinielamundial.logging;

import com.google.gson.Gson;
import java.time.Instant;
import java.util.LinkedHashMap;

/**
 * Structured JSON logger.
 * <p>
 * Outputs one JSON line per log entry to stdout (INFO) or stderr (ERROR).
 * Example:
 * {"timestamp":"2026-06-22T12:00:00Z","level":"INFO","logger":"MatchUpdateService","message":"polling..."}
 */
public class Logger {

    private static final Gson GSON = new Gson();

    private final String name;

    public Logger(String name) {
        this.name = name;
    }

    public void info(String msg) {
        write("INFO", msg);
    }

    public void info(String format, Object... args) {
        write("INFO", String.format(format, args));
    }

    public void error(String msg) {
        write("ERROR", msg);
    }

    public void error(String format, Object... args) {
        write("ERROR", String.format(format, args));
    }

    private void write(String level, String msg) {
        var map = new LinkedHashMap<String, Object>();
        map.put("timestamp", Instant.now().toString());
        map.put("level", level);
        map.put("logger", name);
        map.put("message", msg);

        var json = GSON.toJson(map);
        if ("ERROR".equals(level)) {
            System.err.println(json);
        } else {
            System.out.println(json);
        }
    }
}
