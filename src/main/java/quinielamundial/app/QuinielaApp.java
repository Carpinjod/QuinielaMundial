package quinielamundial.app;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import quinielamundial.domain.Group;
import quinielamundial.domain.Match;
import quinielamundial.domain.Member;
import quinielamundial.persistence.StateStore;
import quinielamundial.logging.Logger;
import quinielamundial.service.AuthService;
import quinielamundial.web.ScoreStream;
import quinielamundial.service.BracketResolver;
import quinielamundial.service.MatchUpdateService;
import quinielamundial.service.QuinielaService;
import quinielamundial.notification.NotificationService;
import quinielamundial.web.FormData;
import quinielamundial.web.HtmlRenderer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class QuinielaApp {
    private static final Logger LOG = new Logger("QuinielaApp");

    private final java.nio.file.Path dataDir = java.nio.file.Path.of(System.getenv().getOrDefault("DATA_DIR", "data"));
    private final StateStore store = new StateStore(dataDir.resolve("quiniela-state.txt"));
    private final QuinielaService service = new QuinielaService();
    private final HtmlRenderer renderer = new HtmlRenderer();
    private final String adminUsername = System.getenv().getOrDefault("ADMIN_USER", "PJ");
    private AuthService auth;
    private final Map<String, ScoreStream> scoreStreams = new HashMap<>();
    private volatile boolean ready = false;

    public void start(int port) throws IOException {
        this.auth = new AuthService(dataDir);
        var snapshot = store.load();
        service.restoreState(snapshot.groups(), snapshot.champion());
        for (var group : service.groups()) {
            attachPersistence(group);
            scoreStreams.put(group.code(), new ScoreStream());
        }
        // Resolve knockout brackets for all groups (populates R32 from current standings)
        service.resolveAllBrackets();
        store.save(service.groups(), service.tournamentChampion());
        var updater = new MatchUpdateService(service.groups().stream().toList(),
            // Final result callback — resolve brackets, persist, broadcast
            updatedGroups -> {
                for (var g : service.groups()) {
                    BracketResolver.resolveBracket(g);
                }
                store.save(service.groups(), service.tournamentChampion());
                broadcastLiveScores(updatedGroups);
            },
            // Live score callback — broadcast only (NO persistence, NO bracket re-resolve)
            updatedGroups -> broadcastLiveScores(updatedGroups)
        );
        updater.start();
        var googleMailSender = new quinielamundial.notification.GoogleMailSender();
        var mailSender = googleMailSender.isConfigured()
            ? googleMailSender
            : new quinielamundial.notification.MailSender();
        var publicUrl = System.getenv().getOrDefault("PUBLIC_URL", "http://localhost:" + port);
        var notifier = new NotificationService(service.groups().stream().toList(), mailSender, publicUrl);
        notifier.start();
        var server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", this::handle);
        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(8));
        server.start();
        ready = true;
        LOG.info("Running on http://localhost:{}", port);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            var method = exchange.getRequestMethod();
            var path = exchange.getRequestURI().getPath();

            if ("GET".equals(method) && "/health".equals(path)) {
                renderJson(exchange, """
                    {"status":"UP","timestamp":"%s"}""".formatted(java.time.Instant.now().toString()));
                return;
            }
            if ("GET".equals(method) && "/health/readiness".equals(path)) {
                if (ready) {
                    renderJson(exchange, """
                        {"status":"UP","groups":%d}""".formatted(service.groups().size()));
                } else {
                    exchange.sendResponseHeaders(503, -1);
                }
                return;
            }

            if ("GET".equals(method) && "/".equals(path)) {
                var loggedIn = resolveSession(exchange);
                var isAdmin = loggedIn != null && loggedIn.equals(adminUsername);
                var success = FormData.param(exchange, "success");
                render(exchange, renderer.homePage(service.groups(), loggedIn, userGroups(loggedIn), null, success.isBlank() ? null : success, isAdmin));
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
                render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), userEmail(loggedIn), null, success.isBlank() ? null : success));
                return;
            }

            if ("POST".equals(method) && "/settings/email".equals(path)) {
                var loggedIn = resolveSession(exchange);
                if (loggedIn == null) { redirect(exchange, "/login"); return; }
                var form = FormData.read(exchange);
                var email = form.value("email", "").trim();
                // Update email on every member with this username across all groups
                for (var g : service.groups()) {
                    for (var m : g.members().values()) {
                        if (m.name().equals(loggedIn)) {
                            m.email(email.isBlank() ? null : email);
                        }
                    }
                }
                store.save(service.groups(), service.tournamentChampion());
                redirect(exchange, "/settings?success=Email+guardado");
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
                    render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), userEmail(loggedIn), "La nueva contraseña debe tener al menos 8 caracteres.", null));
                    return;
                }
                if (!password.equals(confirm)) {
                    render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), userEmail(loggedIn), "Las contraseñas no coinciden.", null));
                    return;
                }
                if (!auth.changePassword(loggedIn, current, password)) {
                    render(exchange, renderer.settingsPage(loggedIn, userGroups(loggedIn), userEmail(loggedIn), "La contraseña actual no es correcta.", null));
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
                    var isAdmin2 = loggedIn2 != null && loggedIn2.equals(adminUsername);
                    render(exchange, renderer.homePage(service.groups(), loggedIn2, userGroups(loggedIn2), "Código inválido.", null, isAdmin2));
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

            // ── Shareable join link ──
            if ("GET".equals(method) && path.startsWith("/join/")) {
                var code = path.substring("/join/".length()).toUpperCase();
                var group = service.group(code);
                if (group == null) {
                    render(exchange, renderer.errorPage("Grupo no encontrado", "El código no existe."));
                    return;
                }
                redirect(exchange, "/groups/" + group.code());
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
                    var selectedJornada = jornadaStr.isBlank() ? -1 : Integer.parseInt(jornadaStr);
                    var success = FormData.param(exchange, "success");
                    render(exchange, renderer.groupPage(group, member, service.candidates(), service.tournamentChampion(), service.tournamentStarted(), selectedJornada, success.isBlank() ? null : success));
                    return;
                }

                if ("GET".equals(method) && tail.equals(code + "/api/scores/sse")) {
                    var stream = scoreStreams.get(group.code());
                    if (stream != null) stream.subscribe(exchange);
                    return;
                }

                if ("GET".equals(method) && tail.equals(code + "/api/scores")) {
                    var scores = group.allMatches().stream()
                        .filter(m -> m.isStarted() || m.hasLiveScore())
                        .map(m -> matchToLiveScore(m))
                        .toList();
                    renderJson(exchange, new Gson().toJson(scores));
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/prediction")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    group.submitPrediction(token, Integer.parseInt(form.required("matchId")), Integer.parseInt(form.required("homeGoals")), Integer.parseInt(form.required("awayGoals")));
                    var j = form.value("jornada", "1");
                    if (isAjaxRequest(exchange)) { ajaxGroupPageResponse(exchange, group, token, j, "Pronóstico guardado"); return; }
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Pron%C3%B3stico+guardado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/champion")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    group.setChampionBet(token, form.value("team", "").trim());
                    var j = form.value("jornada", "1");
                    if (isAjaxRequest(exchange)) { ajaxGroupPageResponse(exchange, group, token, j, "Apuesta al campeón guardada"); return; }
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Apuesta+al+campe%C3%B3n+guardada");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/star")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    group.setStarMatch(token, Integer.parseInt(form.required("jornada")), Integer.parseInt(form.required("matchId")));
                    var j = form.value("jornada", "1");
                    if (isAjaxRequest(exchange)) { ajaxGroupPageResponse(exchange, group, token, j, "Partido estrella actualizado"); return; }
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Partido+estrella+actualizado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/leave")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (member.name().equals(group.creator().name())) {
                        render(exchange, renderer.errorPage("Acceso denegado", "El creador no puede salirse del grupo. Puedes eliminar el grupo desde la página principal si eres administrador."));
                        return;
                    }
                    group.removeMember(member.name());
                    store.save(service.groups(), service.tournamentChampion());
                    redirect(exchange, "/?success=Has+salido+del+grupo+" + URLEncoder.encode(group.name(), StandardCharsets.UTF_8));
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/result")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (!member.name().equals(group.creator().name())) {
                        if (isAjaxRequest(exchange)) {
                            var err = new java.util.LinkedHashMap<String, Object>();
                            err.put("success", false);
                            err.put("message", "Solo el creador del grupo puede cargar resultados.");
                            renderJson(exchange, new Gson().toJson(err));
                            return;
                        }
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
                    if (isAjaxRequest(exchange)) { ajaxGroupPageResponse(exchange, group, token, j, "Resultado guardado"); return; }
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Resultado+guardado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/champion-result")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (!member.name().equals(group.creator().name())) {
                        if (isAjaxRequest(exchange)) {
                            var err = new java.util.LinkedHashMap<String, Object>();
                            err.put("success", false);
                            err.put("message", "Solo el creador del grupo puede actualizar el campeón.");
                            renderJson(exchange, new Gson().toJson(err));
                            return;
                        }
                        render(exchange, renderer.errorPage("Acceso denegado", "Solo el creador del grupo puede actualizar el campeón."));
                        return;
                    }
                    service.setTournamentChampion(form.required("team"));
                    store.save(service.groups(), service.tournamentChampion());
                    var j = form.value("jornada", "1");
                    if (isAjaxRequest(exchange)) { ajaxGroupPageResponse(exchange, group, token, j, "Campeón actualizado"); return; }
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j + "&success=Campe%C3%B3n+actualizado");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/admin/reset-password")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (!member.name().equals(group.creator().name())) {
                        render(exchange, renderer.errorPage("Acceso denegado", "Solo el creador del grupo puede resetear contraseñas."));
                        return;
                    }
                    var username = form.value("username", "").trim();
                    if (username.isBlank()) {
                        render(exchange, renderer.errorPage("Error", "Falta el nombre de usuario."));
                        return;
                    }
                    var newPassword = auth.adminResetPassword(username);
                    if (newPassword == null) {
                        render(exchange, renderer.errorPage("Error", "El usuario '" + username + "' no tiene una cuenta registrada en el sistema de autenticación."));
                        return;
                    }
                    var j = form.value("jornada", "1");
                    render(exchange, renderer.passwordResetResultPage(username, newPassword, group.code(), token, Integer.parseInt(j)));
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/admin/remove-member")) {
                    var form = FormData.read(exchange);
                    var token = form.required("token");
                    var member = group.requireByToken(token);
                    if (!member.name().equals(group.creator().name())) {
                        render(exchange, renderer.errorPage("Acceso denegado", "Solo el creador del grupo puede eliminar miembros."));
                        return;
                    }
                    var username = form.value("username", "").trim();
                    if (username.isBlank()) {
                        render(exchange, renderer.errorPage("Error", "Falta el nombre de usuario."));
                        return;
                    }
                    group.removeMember(username);
                    store.save(service.groups(), service.tournamentChampion());
                    var j = form.value("jornada", "1");
                    redirect(exchange, "/groups/" + group.code() + "?token=" + token + "&jornada=" + j
                        + "&success=Miembro+" + URLEncoder.encode(username, StandardCharsets.UTF_8) + "+eliminado+del+grupo");
                    return;
                }

                if ("POST".equals(method) && tail.equals(code + "/admin/delete-group")) {
                    var loggedIn = resolveSession(exchange);
                    if (loggedIn == null || !loggedIn.equals(adminUsername)) {
                        render(exchange, renderer.errorPage("Acceso denegado", "No tienes permiso para eliminar grupos."));
                        return;
                    }
                    service.removeGroup(group.code());
                    store.save(service.groups(), service.tournamentChampion());
                    redirect(exchange, "/?success=Grupo+" + URLEncoder.encode(group.name(), StandardCharsets.UTF_8) + "+eliminado");
                    return;
                }
            }

            render(exchange, renderer.errorPage("404", "Ruta no encontrada."));
        } catch (Exception e) {
            if (isAjaxRequest(exchange)) {
                var err = new java.util.LinkedHashMap<String, Object>();
                err.put("success", false);
                err.put("message", e.getMessage() == null ? "Error inesperado" : e.getMessage());
                renderJson(exchange, new Gson().toJson(err));
                return;
            }
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
        exchange.getResponseHeaders().add("Set-Cookie", "quiniela_token=" + token + "; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly");
    }

    private boolean isAjaxRequest(HttpExchange exchange) {
        return "XMLHttpRequest".equals(exchange.getRequestHeaders().getFirst("X-Requested-With"));
    }

    private void ajaxGroupPageResponse(HttpExchange exchange, Group group, String token, String jornadaStr, String message) throws IOException {
        var selectedJornada = jornadaStr.isBlank() ? -1 : Integer.parseInt(jornadaStr);
        var member = group.requireByToken(token);
        var pageHtml = renderer.groupPage(group, member, service.candidates(), service.tournamentChampion(), service.tournamentStarted(), selectedJornada, null);
        var json = new java.util.LinkedHashMap<String, Object>();
        json.put("success", true);
        json.put("message", message);
        json.put("html", pageHtml);
        renderJson(exchange, new Gson().toJson(json));
    }

    private static void render(HttpExchange exchange, String html) throws IOException {
        var bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void renderJson(HttpExchange exchange, String json) throws IOException {
        var bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
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

    /** Get the email address of a user, or null if not set. */
    private String userEmail(String username) {
        if (username == null) return null;
        for (var g : service.groups()) {
            for (var m : g.members().values()) {
                if (m.name().equals(username) && m.email() != null) return m.email();
            }
        }
        return null;
    }

    /** Push live scores to SSE clients of the given groups. */
    private void broadcastLiveScores(List<Group> groups) {
        var gson = new Gson();
        for (var g : groups) {
            var stream = scoreStreams.get(g.code());
            if (stream == null || !stream.hasClients()) continue;
            var scores = g.allMatches().stream()
                .filter(m -> m.isStarted() || m.hasLiveScore())
                .map(m -> matchToLiveScore(m))
                .toList();
            stream.broadcast(gson.toJson(scores));
        }
    }

    /** Map a match to a LiveScore record, using live score if available. */
    private static LiveScore matchToLiveScore(Match m) {
        if (m.finished())
            return new LiveScore(m.id(), m.homeGoals(), m.awayGoals(), true);
        if (m.hasLiveScore())
            return new LiveScore(m.id(), m.liveHomeGoals(), m.liveAwayGoals(), false);
        return new LiveScore(m.id(), 0, 0, false);
    }

    private record LiveScore(int id, int homeGoals, int awayGoals, boolean finished) {}
}
