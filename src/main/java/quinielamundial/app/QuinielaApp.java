package quinielamundial.app;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import quinielamundial.domain.Group;
import quinielamundial.domain.Member;
import quinielamundial.persistence.StateStore;
import quinielamundial.service.AuthService;
import quinielamundial.service.BracketResolver;
import quinielamundial.service.MatchUpdateService;
import quinielamundial.service.QuinielaService;
import quinielamundial.web.FormData;
import quinielamundial.web.HtmlRenderer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

public class QuinielaApp {
    private final java.nio.file.Path dataDir = java.nio.file.Path.of(System.getenv().getOrDefault("DATA_DIR", "data"));
    private final StateStore store = new StateStore(dataDir.resolve("quiniela-state.txt"));
    private final QuinielaService service = new QuinielaService();
    private final HtmlRenderer renderer = new HtmlRenderer();
    private AuthService auth;

    public void start(int port) throws IOException {
        this.auth = new AuthService(dataDir);
        var snapshot = store.load();
        service.restoreState(snapshot.groups(), snapshot.champion());
        for (var group : service.groups()) {
            attachPersistence(group);
        }
        // Resolve knockout brackets for all groups (populates R32 from current standings)
        service.resolveAllBrackets();
        store.save(service.groups(), service.tournamentChampion());
        var updater = new MatchUpdateService(service.groups().stream().toList(), () -> {
            for (var g : service.groups()) {
                BracketResolver.resolveBracket(g);
            }
            store.save(service.groups(), service.tournamentChampion());
        });
        updater.start();
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("QuinielaMundial running on http://localhost:" + port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getPath();

            if ("GET".equals(method) && "/".equals(path)) {
                var loggedIn = resolveSession(exchange);
                render(exchange, renderer.homePage(service.groups(), loggedIn, userGroups(loggedIn), null));
                return;
            }

            // ── Auth routes ──
            if ("GET".equals(method) && "/login".equals(path)) {
                render(exchange, renderer.loginPage(null));
                return;
            }
            if ("POST".equals(method) && "/login".equals(path)) {
                var form = FormData.read(exchange);
                var username = form.value("username", "").trim();
                var password = form.value("password", "");
                var sessionId = auth.login(username, password);
                if (sessionId == null) {
                    render(exchange, renderer.loginPage("Usuario o contraseña incorrectos."));
                    return;
                }
                setSessionCookie(exchange, sessionId);
                redirect(exchange, "/");
                return;
            }
            if ("GET".equals(method) && "/register".equals(path)) {
                render(exchange, renderer.registerPage(null));
                return;
            }
            if ("POST".equals(method) && "/register".equals(path)) {
                var form = FormData.read(exchange);
                var username = form.value("username", "").trim();
                var password = form.value("password", "");
                var confirm = form.value("confirm", "");
                if (username.length() < 2) {
                    render(exchange, renderer.registerPage("El usuario debe tener al menos 2 caracteres."));
                    return;
                }
                if (password.length() < 8) {
                    render(exchange, renderer.registerPage("La contraseña debe tener al menos 8 caracteres."));
                    return;
                }
                if (!password.equals(confirm)) {
                    render(exchange, renderer.registerPage("Las contraseñas no coinciden."));
                    return;
                }
                if (!auth.register(username, password)) {
                    render(exchange, renderer.registerPage("El usuario ya existe."));
                    return;
                }
                // Auto-login after register
                var sessionId = auth.login(username, password);
                setSessionCookie(exchange, sessionId);
                redirect(exchange, "/");
                return;
            }
            if ("GET".equals(method) && "/logout".equals(path)) {
                var sessionId = readSessionCookie(exchange);
                auth.logout(sessionId);
                clearSessionCookie(exchange);
                redirect(exchange, "/");
                return;
            }

            if ("GET".equals(method) && "/settings".equals(path)) {
                var loggedIn = resolveSession(exchange);
                if (loggedIn == null) { redirect(exchange, "/login"); return; }
                var success = FormData.param(exchange, "success");
                render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), null, success.isBlank() ? null : success));
                return;
            }

            if ("POST".equals(method) && "/settings/password".equals(path)) {
                var loggedIn = resolveSession(exchange);
                if (loggedIn == null) { redirect(exchange, "/login"); return; }
                var form = FormData.read(exchange);
                var current = form.value("current", "");
                var password = form.value("password", "");
                var confirm = form.value("confirm", "");
                if (password.length() < 8) {
                    render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), "La nueva contraseña debe tener al menos 8 caracteres.", null));
                    return;
                }
                if (!password.equals(confirm)) {
                    render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), "Las contraseñas no coinciden.", null));
                    return;
                }
                if (!auth.changePassword(loggedIn, current, password)) {
                    render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), "La contraseña actual no es correcta.", null));
                    return;
                }
                redirect(exchange, "/settings?success=Contrase%C3%B1a+cambiada+correctamente");
                return;
            }

            if ("POST".equals(method) && "/groups/create".equals(path)) {
                var form = FormData.read(exchange);
                var loggedIn = resolveSession(exchange);
                var creatorName = loggedIn != null ? loggedIn : form.value("creator", "Organizador");
                var group = service.createGroup(form.value("groupName", "Grupo de amigos"), creatorName);
                attachPersistence(group);
                store.save(service.groups(), service.tournamentChampion());
                var creator = group.creator();
                setTokenCookie(exchange, creator.token());
                redirect(exchange, "/groups/" + group.code() + "?token=" + creator.token());
                return;
            }

            if ("POST".equals(method) && "/groups/join".equals(path)) {
                var form = FormData.read(exchange);
                var loggedIn = resolveSession(exchange);
                var memberName = loggedIn != null ? loggedIn : form.value("member", "Invitado");
                var group = service.joinGroup(form.value("code", ""), memberName);
                if (group == null) {
                    var loggedIn2 = resolveSession(exchange);
                    render(exchange, renderer.homePage(service.groups(), loggedIn2, userGroups(loggedIn2), "Código inválido."));
                    return;
                }
                attachPersistence(group);
                store.save(service.groups(), service.tournamentChampion());
                // Find the member that was just joined (last added)
                var member = group.members().values().stream().reduce((a, b) -> b).orElseThrow();
                setTokenCookie(exchange, member.token());
                redirect(exchange, "/groups/" + group.code() + "?token=" + member.token());
                return;
            }

            if (path.startsWith("/groups/")) {
                var tail = path.substring("/groups/".length());
                var code = tail.contains("/") ? tail.substring(0, tail.indexOf('/')).toUpperCase() : tail.toUpperCase();
                var group = service.group(code);
                if (group == null) {
                    render(exchange, renderer.errorPage("Grupo no encontrado", "El código no existe."));
                    return;
                }

                if ("GET".equals(method) && tail.equals(code)) {
                    var member = resolveMember(exchange, group);
                    var jornadaStr = FormData.param(exchange, "jornada");
                    var selectedJornada = jornadaStr.isBlank() ? 1 : Integer.parseInt(jornadaStr);
                    var success = FormData.param(exchange, "success");
                    render(exchange, renderer.groupPage(group, member, service.candidates(), service.tournamentChampion(), service.tournamentStarted(), selectedJornada, success.isBlank() ? null : success));
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/prediction")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    group.submitPrediction(token, Integer.parseInt(form.required("matchId")), Integer.parseInt(form.required("homeGoals")), Integer.parseInt(form.required("awayGoals")));
                    var j = form.value("jornada", "1");
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Pron%C3%B3stico+guardado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/champion")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    group.setChampionBet(token, form.value("team", "").trim(), service.tournamentStarted());
                    var j = form.value("jornada", "1");
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Apuesta+al+campe%C3%B3n+guardada");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/star")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    group.setStarMatch(token, Integer.parseInt(form.required("jornada")), Integer.parseInt(form.required("matchId")));
                    var j = form.value("jornada", "1");
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Partido+estrella+actualizado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/result")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (!member.name().equals(group.creator().name())) {
                        render(exchange, renderer.errorPage("Acceso denegado", "Solo el creador del grupo puede cargar resultados."));
                        return;
                    }
                    int matchId = Integer.parseInt(form.required("matchId"));
                    group.registerResult(matchId, Integer.parseInt(form.required("homeGoals")), Integer.parseInt(form.required("awayGoals")));
                    // Resolve bracket: if group stage, recalculate standings & R32;
                    // if KO, propagate winners to later rounds.
                    if (matchId <= 72) {
                        service.resolveBracket(group);
                    } else {
                        service.propagateWinners(group);
                    }
                    var j = form.value("jornada", "1");
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Resultado+guardado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/champion-result")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (!member.name().equals(group.creator().name())) {
                        render(exchange, renderer.errorPage("Acceso denegado", "Solo el creador del grupo puede actualizar el campeón."));
                        return;
                    }
                    service.setTournamentChampion(form.required("team"));
                    store.save(service.groups(), service.tournamentChampion());
                    var j = form.value("jornada", "1");
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Campe%C3%B3n+actualizado");
                    return;
                }
            }

            render(exchange, renderer.errorPage("404", "Ruta no encontrada."));
        } catch (Exception e) {
            render(exchange, renderer.errorPage("Error", e.getMessage() == null ? "Error inesperado" : e.getMessage()));
        }
    }

    private Member resolveMember(HttpExchange exchange, Group group) {
        // 1️⃣ Try by token cookie (fast path — works across pages)
        var token = readTokenCookie(exchange);
        var fromQuery = false;
        if (token == null) { token = FormData.param(exchange, "token"); fromQuery = true; }
        if (token != null && !token.isBlank()) {
            try {
                var member = group.requireByToken(token);
                if (fromQuery) setTokenCookie(exchange, token);
                return member;
            } catch (Exception e) {
                // Token invalid — fall through to session-based resolution
            }
        }
        // 2️⃣ Fallback: resolve by username from session (multi-device support)
        var loggedIn = resolveSession(exchange);
        if (loggedIn != null) {
            var member = group.members().get(loggedIn);
            if (member != null) return member;
        }
        return null;
    }

    private String readTokenCookie(HttpExchange exchange) {
        var cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        return Arrays.stream(cookieHeader.split(";"))
            .map(String::trim)
            .filter(c -> c.startsWith("quiniela_token="))
            .map(c -> c.substring("quiniela_token=".length()))
            .findFirst()
            .orElse(null);
    }

    private void setTokenCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().add("Set-Cookie", "quiniela_token=" + token + "; Path=/; Max-Age=31536000; SameSite=Lax");
    }

    private static void render(HttpExchange exchange, String html) throws IOException {
        var bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
        exchange.close();
    }

    private void attachPersistence(Group group) {
        group.onChange(() -> store.save(service.groups(), service.tournamentChampion()));
    }

    // ── Session helpers ──

    private String resolveSession(HttpExchange exchange) {
        var sessionId = readSessionCookie(exchange);
        return auth.validateSession(sessionId);
    }

    private String readSessionCookie(HttpExchange exchange) {
        var cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        return Arrays.stream(cookieHeader.split(";"))
            .map(String::trim)
            .filter(c -> c.startsWith("quiniela_session="))
            .map(c -> c.substring("quiniela_session=".length()))
            .findFirst()
            .orElse(null);
    }

    private void setSessionCookie(HttpExchange exchange, String sessionId) {
        exchange.getResponseHeaders().add("Set-Cookie", "quiniela_session=" + sessionId + "; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly");
    }

    private void clearSessionCookie(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Set-Cookie", "quiniela_session=; Path=/; Max-Age=0; SameSite=Lax; HttpOnly");
    }

    /** Returns list of groups where the given username is a member. */
    private java.util.List<Group> userGroups(String username) {
        if (username == null) return java.util.List.of();
        return service.groups().stream()
            .filter(g -> g.members().values().stream().anyMatch(m -> m.name().equals(username)))
            .collect(java.util.stream.Collectors.toList());
    }
}
