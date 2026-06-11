package quinielamundial.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FormData {
    private final Map<String, String> values;
    private FormData(Map<String, String> values) { this.values = values; }
    public static FormData read(HttpExchange exchange) throws IOException { var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); return new FormData(parse(body)); }
    public static String param(HttpExchange exchange, String key) { var query = exchange.getRequestURI().getRawQuery(); return parse(query == null ? "" : query).getOrDefault(key, ""); }
    public static String encode(String value) { return value == null ? "" : value.replace(" ", "%20"); }
    public String value(String key, String defaultValue) { return values.getOrDefault(key, defaultValue); }
    public String required(String key) { var value = values.get(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("Falta el campo: " + key); return value; }
    private static Map<String, String> parse(String body) { var map = new LinkedHashMap<String, String>(); if (body == null || body.isBlank()) return map; for (var part : body.split("&")) { var kv = part.split("=", 2); var key = decode(kv[0]); var value = kv.length > 1 ? decode(kv[1]) : ""; map.put(key, value); } return map; }
    private static String decode(String value) { return URLDecoder.decode(value == null ? "" : value.replace('+', ' '), StandardCharsets.UTF_8); }
}
