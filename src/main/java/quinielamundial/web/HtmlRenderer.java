package quinielamundial.web;

import quinielamundial.domain.Group;
import quinielamundial.domain.Match;
import quinielamundial.domain.Member;
import quinielamundial.domain.Prediction;
import quinielamundial.domain.RankingEntry;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HtmlRenderer {
    public String homePage(Collection<Group> groups, String loggedInUser, Collection<Group> userGroups, String error) {
        return homePage(groups, loggedInUser, userGroups, error, null, false);
    }

    public String homePage(Collection<Group> groups, String loggedInUser, Collection<Group> userGroups, String error, String success) {
        return homePage(groups, loggedInUser, userGroups, error, success, false);
    }

    public String homePage(Collection<Group> groups, String loggedInUser, Collection<Group> userGroups, String error, String success, boolean isAdmin) {
        // Sort: user's groups first, then rest by name
        var sortedGroups = groups.stream()
            .sorted(Comparator.<Group, Boolean>comparing(g -> userGroups != null && userGroups.contains(g)).reversed()
                .thenComparing(Group::name))
            .collect(Collectors.toList());
        var groupsList = sortedGroups.stream().map(group -> {
            var name = escape(group.name());
            var code = escape(group.code());
            var deleteBtn = isAdmin
                ? "<form method='post' action='/groups/" + code + "/admin/delete-group' style='display:inline'"
                + " onsubmit=\"return confirmAction('Eliminar el grupo " + name + "? Se borrarán TODOS los pronósticos de TODOS los miembros. No se puede deshacer.')\">"
                + "<button type='submit' class='btn-admin' style='margin:0;padding:2px 8px;font-size:12px;background:#dc2626'>Eliminar</button>"
                + "</form>"
                : "";
            return "<li><span>" + name + "</span><span class='code-tag'>" + code + "</span>" + deleteBtn + "</li>";
        }).collect(Collectors.joining(""));

        // ── My Groups (logged-in only) ──
        var myGroupsHtml = "";
        if (loggedInUser != null) {
            var myList = userGroups.stream().map(g -> {
                var member = g.members().values().stream()
                    .filter(m -> m.name().equals(loggedInUser)).findFirst();
                if (member.isEmpty()) {
                    return "<a href='/groups/" + g.code() + "' class='my-group-card'>"
                        + "<div><div class='name'>" + escape(g.name()) + "</div><div class='code'>" + g.code() + "</div></div>"
                        + "<span class='arrow'>→</span></a>";
                }
                var stats = computeMemberStats(g, member.get(), null);
                var pendingText = stats.pendingPredictions() > 0
                    ? "<span class='my-pending'>⏳ " + stats.pendingPredictions() + " pendientes</span>"
                    : "<span class='my-pending done'>✅ Completado</span>";
                return "<a href='/groups/" + g.code() + "' class='my-group-card enhanced'>"
                    + "<div class='my-card-body'>"
                    + "<div class='name'>" + escape(g.name()) + "</div>"
                    + "<div class='my-meta'>#" + stats.rank() + " de " + stats.totalMembers() + " · " + stats.points() + " pts</div>"
                    + pendingText
                    + "</div>"
                    + "<div class='my-card-right'>"
                    + "<span class='arrow'>→</span>"
                    + "</div></a>";
            }).collect(Collectors.joining());
            myGroupsHtml = myList.isBlank() ? "" : "<div class='section-title'>📂 Mis grupos</div><div class='my-groups-grid'>" + myList + "</div>";
        }

        // ── Pre-fill name from session ──
        var nameAttr = loggedInUser != null ? " value='" + escape(loggedInUser) + "' readonly style='opacity:.7'" : " placeholder='Tu nombre' required";

        return page("Quiniela Mundial 2026",
            (error == null ? "" : "<div class='toast error'>" + escape(error) + "</div>")
            + (success == null ? "" : "<div class='toast success'>" + escape(success) + "</div>")
            + "<div class='home-hero'><h1>Tu quiniela del Mundial 2026</h1><p>Crea un grupo con amigos, pronostica los resultados del torneo más prestigioso del mundo y compite por la gloria.</p></div>"
            + myGroupsHtml
            + "<div class='home-cards'>"
            + "<div class='card'><h2>➕ Crear grupo</h2><form method='post' action='/groups/create'><input name='groupName' placeholder='Nombre del grupo' required><input name='creator'" + nameAttr + "><button type='submit'>Crear grupo</button></form></div>"
            + "<div class='card'><h2>🔑 Unirse con código</h2><form method='post' action='/groups/join'><input name='code' placeholder='Código de invitación' required><input name='member'" + nameAttr + "><button type='submit'>Entrar</button></form></div>"
            + "</div>"
            + "<div class='section-title'>📋 Todos los grupos</div>"
            + "<ul class='all-groups-list'>" + (groupsList.isBlank() ? "<li class='muted'>No hay grupos todavía.</li>" : groupsList) + "</ul>"
            + "<details class='card rules-card'><summary><h2 style='display:inline'>📖 Reglas</h2></summary><ul class='rules-list'>"
            + "<li><span class='rule-icon'>🎯</span><b>3 puntos</b> si aciertas el resultado exacto (goles)</li>"
            + "<li><span class='rule-icon'>✅</span><b>1 punto</b> si aciertas solo el ganador o empate</li>"
            + "<li><span class='rule-icon'>⭐</span><b>Partido Estrella</b> — uno por jornada, puntuación <b>doble</b></li>"
            + "<li><span class='rule-icon'>🏆</span><b>Campeón</b> — 10 puntos extra si aciertas</li>"
            + "<li><span class='rule-icon'>🔒</span>Los pronósticos de otros se ocultan hasta que empieza el partido</li>"
            + "</ul></details>", loggedInUser);
    }

    public String loginPage(String error) {
        return page("Iniciar sesión",
            "<div class='auth-page'>"
            + "<a href='/' class='back-link'>← Inicio</a>"
            + "<div class='auth-card'>"
            + "<h2>Iniciar sesión</h2>"
            + (error == null ? "" : "<div class='toast error'>" + escape(error) + "</div>")
            + "<form method='post' action='/login' class='auth-form'>"
            + "<input name='username' placeholder='Usuario' required autocomplete='username'>"
            + "<input name='password' type='password' placeholder='Contraseña' required autocomplete='current-password'>"
            + "<button type='submit'>Entrar</button>"
            + "</form>"
            + "<p class='muted' style='text-align:center;margin-top:16px'>¿No tienes cuenta? <a href='/register'>Regístrate</a></p>"
            + "</div>"
            + "</div>");
    }

    public String registerPage(String error) {
        return page("Registrarse",
            "<div class='auth-page'>"
            + "<a href='/' class='back-link'>← Inicio</a>"
            + "<div class='auth-card'>"
            + "<h2>Crear cuenta</h2>"
            + (error == null ? "" : "<div class='toast error'>" + escape(error) + "</div>")
            + "<form method='post' action='/register' class='auth-form'>"
            + "<input name='username' placeholder='Usuario' required autocomplete='username'>"
            + "<input name='password' type='password' placeholder='Contraseña (mín. 4 caracteres)' required autocomplete='new-password'>"
            + "<input name='confirm' type='password' placeholder='Repetir contraseña' required autocomplete='new-password'>"
            + "<button type='submit'>Crear cuenta</button>"
            + "</form>"
            + "<p class='muted' style='text-align:center;margin-top:16px'>¿Ya tienes cuenta? <a href='/login'>Inicia sesión</a></p>"
            + "</div>"
            + "</div>");
    }

    public String settingsPage(String username, Collection<Group> userGroups, String email, String error, String success) {
        var groupStats = userGroups.stream().map(g -> {
            var member = g.members().values().stream().filter(m -> m.name().equals(username)).findFirst();
            if (member.isEmpty()) return "";
            var memberObj = member.get();
            var leaderboard = g.leaderboard(null);
            var score = leaderboard.stream().filter(e -> e.member().name().equals(username)).findFirst();
            var pts = score.map(s -> String.valueOf(s.score().totalPoints())).orElse("0");
            var exact = score.map(s -> String.valueOf(s.score().exactHits())).orElse("0");
            var outcome = score.map(s -> String.valueOf(s.score().outcomeHits())).orElse("0");
            var rank = score.map(s -> String.valueOf(s.rank())).orElse("-");

            // ── Advanced stats ──
            var allMatches = Stream.concat(g.matches().stream(), g.knockoutMatches().stream())
                .filter(Match::finished).sorted(Comparator.comparing(Match::kickoff)).toList();
            var totalFinished = allMatches.size();
            var predicted = 0;
            var hitMatches = 0;
            var currentStreak = 0;
            var bestStreak = 0;
            var memberCount = g.members().size();

            for (var m : allMatches) {
                var p = memberObj.predictions().get(m.id());
                if (p == null) {
                    currentStreak = 0; // streak broken by unpredicted match
                    continue;
                }
                predicted++;
                var points = scoreMatch(m, p, memberObj, g);
                if (points > 0) {
                    hitMatches++;
                    currentStreak++;
                    if (currentStreak > bestStreak) bestStreak = currentStreak;
                } else {
                    currentStreak = 0;
                }
            }

            // Group average (total points of all members / member count)
            var groupAvgPoints = memberCount > 0
                ? g.members().values().stream()
                    .mapToInt(other -> g.score(other, null).totalPoints())
                    .average().orElse(0.0)
                : 0.0;

            var hitPct = predicted > 0 ? (double) hitMatches / predicted * 100 : 0.0;
            var diffAvg = score.map(s -> s.score().totalPoints() - groupAvgPoints).orElse(0.0);
            var diffAvgStr = diffAvg >= 0
                ? "<span style='color:var(--green)'>+" + String.format("%.0f", diffAvg) + "</span>"
                : "<span style='color:var(--red)'>" + String.format("%.0f", diffAvg) + "</span>";

            return "<tr>"
                + "<td><a href='/groups/" + g.code() + "'>" + escape(g.name()) + "</a></td>"
                + "<td class='pts'>" + pts + "</td>"
                + "<td>" + rank + "º</td>"
                + "<td>" + exact + "</td>"
                + "<td>" + outcome + "</td>"
                + "<td>" + (predicted > 0 ? String.format("%.0f", hitPct) + "%" : "–") + "</td>"
                + "<td>" + bestStreak + "</td>"
                + "<td>" + diffAvgStr + "</td>"
                + "</tr>";
        }).collect(Collectors.joining());

        return page("Ajustes",
            "<a href='/' class='back-link'>← Inicio</a>"
            + (error == null ? "" : "<div class='toast error'>" + escape(error) + "</div>")
            + (success == null ? "" : "<div class='toast success'>" + escape(success) + "</div>")
            + "<div class='settings-layout'>"
            + "<h1 class='section-title'>⚙️ Ajustes de " + escape(username) + "</h1>"
            + "<div class='card'><h2>🔑 Cambiar contraseña</h2>"
            + "<form method='post' action='/settings/password' class='auth-form' style='margin-top:12px'>"
            + "<input name='current' type='password' placeholder='Contraseña actual' required autocomplete='current-password'>"
            + "<input name='password' type='password' placeholder='Nueva contraseña (mín. 8 caracteres)' required autocomplete='new-password'>"
            + "<input name='confirm' type='password' placeholder='Repetir nueva contraseña' required autocomplete='new-password'>"
            + "<button type='submit'>Cambiar contraseña</button>"
            + "</form></div>"
            + "<div class='card'><h2>Notificaciones por email</h2>"
            + "<form method='post' action='/settings/email' class='auth-form' style='margin-top:12px'>"
            + "<input name='email' type='email' placeholder='tu@email.com' value='" + (email == null ? "" : escape(email)) + "'>"
            + "<button type='submit'>Guardar email</button>"
            + "</form>"
            + "<p class='muted' style='margin-top:8px;font-size:13px'>Recibirás un recordatorio 1 hora antes de cada partido que no hayas pronosticado.</p>"
            + "</div>"
            + "<div class='card'><h2>📊 Mis estadísticas</h2>"
            + "<div class='table-wrap'><table><thead><tr>"
            + "<th>Grupo</th><th>Pts</th><th>#</th><th>🎯</th><th>🎲</th><th>%</th><th>🔥 Rachas</th><th>📊 vs Grupo</th>"
            + "</tr></thead><tbody>"
            + groupStats
            + "</tbody></table>"
            + "<p class='muted' style='margin-top:12px;font-size:clamp(11px,1vw,13px)'>"
            + "<b>Pts</b> = Puntos · <b>#</b> = Puesto · <b>🎯</b> = Exactos · <b>🎲</b> = Resultado · "
            + "<b>%</b> = Aciertos · <b>🔥</b> = Mejor racha · <b>📊</b> = Diferencia vs media del grupo"
            + "</p>"
            + "</div></div>"
            + "<form method='post' action='/logout' style='margin-top:16px'>"
            + "<button type='submit' style='background:#dc2626'>Cerrar sesión</button>"
            + "</form>"
            + "</div>", username);
    }

    /** Compute points earned for a single match given a member's prediction, including star multiplier. */
    private int scoreMatch(Match match, Prediction prediction, Member member, Group group) {
        if (!match.finished()) return 0;
        int base;
        if (prediction.homeGoals() == match.homeGoals() && prediction.awayGoals() == match.awayGoals()) {
            base = 3;
        } else {
            var actual = match.result();
            base = (actual != null && prediction.outcome().equals(actual)) ? 1 : 0;
        }
        // Star match doubles group-stage points
        if (!match.knockout() && member.starByJornada().getOrDefault(match.jornada(), -1) == match.id()) {
            base *= 2;
        }
        return base;
    }

    /** Aggregated stats for a member within a group. */
    private record MemberStats(int rank, int totalMembers, int points, int pendingPredictions, double hitPct, int bestStreak) {}

    /** Compute MemberStats for a given member in a group, consistent with Group.computeScore(). */
    private MemberStats computeMemberStats(Group group, Member member, String championTeam) {
        var finished = group.allMatches().stream()
            .filter(Match::finished)
            .sorted(Comparator.comparing(Match::kickoff))
            .toList();

        int totalPoints = 0, exactHits = 0, outcomeHits = 0;
        int hitMatches = 0, bestStreak = 0, currentStreak = 0;

        for (var m : finished) {
            var p = member.predictions().get(m.id());
            if (p == null) { currentStreak = 0; continue; }

            // Base score (w/o star multiplier) for exact/outcome counting — same for group and KO
            int base;
            if (p.homeGoals() == m.homeGoals() && p.awayGoals() == m.awayGoals()) base = 3;
            else {
                var actual = m.result();
                base = (actual != null && p.outcome().equals(actual)) ? 1 : 0;
            }
            if (base == 3) exactHits++;
            else if (base == 1) outcomeHits++;

            // Total with star multiplier
            int pts = base;
            if (base > 0 && !m.knockout() && member.starByJornada().getOrDefault(m.jornada(), -1) == m.id()) pts *= 2;
            totalPoints += pts;

            if (pts > 0) { hitMatches++; currentStreak++; if (currentStreak > bestStreak) bestStreak = currentStreak; }
            else { currentStreak = 0; }
        }

        var championHit = member.championBet() != null && member.championBet().equals(championTeam);
        if (championHit) totalPoints += 10;

        var totalPredicted = (int) finished.stream().filter(m -> member.predictions().containsKey(m.id())).count();
        var hitPct = totalPredicted > 0 ? (double) hitMatches / totalPredicted * 100 : 0.0;

        var pending = (int) group.allMatches().stream()
            .filter(m -> !m.finished() && !m.isStarted() && !member.predictions().containsKey(m.id()))
            .count();

        var rank = group.leaderboard(championTeam).stream()
            .filter(e -> e.member().name().equals(member.name()))
            .mapToInt(RankingEntry::rank)
            .findFirst().orElse(0);

        return new MemberStats(rank, group.members().size(), totalPoints, pending, hitPct, bestStreak);
    }

    public String groupPage(Group group, Member member, List<String> candidates, String championTeam, boolean tournamentStarted, int selectedJornada, String success) {
        var isCreator = member != null && member.name().equals(group.creator().name());

        // ── Top bar ──
        var userSelector = "";
        if (member == null) {
            userSelector = "<span class='user-badge user-not-joined'>"
                + "🔒 Usa el código <b>" + escape(group.code()) + "</b> en la <a href='/'>página principal</a> para unirte</span>";
        } else {
            userSelector = "<span class='user-badge'>👤 " + escape(member.name()) + "</span>";
        }

        var topBar = "<div class='group-header'>"
            + "<div class='title-area'><h1>" + escape(group.name()) + "</h1>"
            + "<div class='meta'><span class='code-tag'>" + escape(group.code()) + "</span></div></div>"
            + "<div class='user-area'>" + userSelector + "</div>"
            + "</div>";

        // ── View toggle: Fase de grupos / Eliminatorias ──
        // Auto-detect: si no se especificó jornada y la fase de grupos terminó,
        // mostrar eliminatorias por defecto en lugar de la fase de grupos.
        if (selectedJornada == -1 && group.groupStageFinished()) {
            selectedJornada = 0;
        }
        var isKnockoutView = selectedJornada == 0;
        var tokenQs = member != null ? "&token=" + member.token() : "";
        var viewToggle = "<div class='view-toggle'>"
            + "<a href='/groups/" + escape(group.code()) + "?jornada=-2" + tokenQs + "' class='view-btn" + (isKnockoutView ? "" : " active") + "'>📋 Fase de grupos</a>"
            + "<a href='/groups/" + escape(group.code()) + "?jornada=0" + tokenQs + "' class='view-btn" + (isKnockoutView ? " active" : "") + "'>🏆 Eliminatorias</a>"
            + "</div>";

        // ── Content: Accordion (group stage) or Bracket (knockout) ──
        String mainContent;
        if (isKnockoutView) {
            mainContent = bracketView(group, member, isCreator, tournamentStarted, championTeam);
        } else {
            var totalJornadas = group.matches().stream().mapToInt(Match::jornada).max().orElse(3);
            var currentJor = currentJornada(group);
            var expandedJornada = selectedJornada > 0 ? selectedJornada : currentJor;

            // Filter bar
            var filterBar = "<div class='filter-bar'>"
                + "<label class='filter-pending'><input type='checkbox' id='filterPending' onclick='togglePendingFilter()'>"
                + "<span>Solo pendientes</span></label>"
                + "<span class='filter-info' id='filterInfo'></span>"
                + "</div>";

            var accordion = new StringBuilder();
            for (int j = 1; j <= totalJornadas; j++) {
                var jornadaIdx = j;
                var jMatches = group.matches().stream()
                    .filter(m -> m.jornada() == jornadaIdx)
                    .sorted(Comparator.comparing(Match::kickoff))
                    .collect(Collectors.toList());
                if (jMatches.isEmpty()) continue;

                var pending = member == null ? 0 : (int) jMatches.stream().filter(m -> !m.isStarted()).count();
                var finished = (int) jMatches.stream().filter(Match::finished).count();
                accordion.append("<details class='jor-section'")
                    .append(j == expandedJornada ? " open" : "")
                    .append(">")
                    .append("<summary class='jor-header")
                    .append(j == currentJor ? " jor-current" : "")
                    .append("'><span class='jor-title'><span class='jor-num'>Jornada ").append(j).append("</span>")
                    .append(j == currentJor ? " <span class='jor-now'>En vivo</span>" : "")
                    .append("</span><span class='jor-meta'>")
                    .append(jMatches.size()).append(" partidos")
                    .append(pending > 0 ? " · <span class='jor-pending'>" + pending + " pendientes</span>" : "")
                    .append(finished > 0 ? " · " + finished + " finalizados" : "")
                    .append("</span></summary>")
                    .append("<div class='jor-matches'>");

                for (var m : jMatches) {
                    var isActive = member != null && !m.finished();
                    accordion.append("<div class='match-wrapper' data-active=\"").append(isActive).append("\">")
                        .append(matchCard(group, m, member, tournamentStarted, m.jornada(), isCreator))
                        .append("</div>");
                }
                accordion.append("</div></details>");
            }

            mainContent = filterBar + "<div class='jor-accordion'>" + accordion.toString() + "</div>";
        }

        // ── Sidebar content (leaderboard + champion sections) ──
        var leaderboard = leaderboardSection(group, championTeam);
        var championSection = member == null ? ""
            : championForm(group, member, candidates, tournamentStarted, selectedJornada, championTeam);
        var championAdmin = isCreator ? championResultForm(group.code(), candidates, selectedJornada, member.token()) : "";
        var adminReset = isCreator ? adminResetSection(group, member, selectedJornada) : "";
        var leaveSection = member != null && !isCreator ? leaveGroupSection(group, member, selectedJornada) : "";
        var sideContent = leaderboard + championSection + championAdmin + adminReset + leaveSection;

        // ── Drawer (mobile) + FAB ──
        var drawerHtml = "";
        if (!sideContent.isBlank()) {
            drawerHtml = ""
                + "<div id='drawer-overlay' class='drawer-overlay' onclick='closeDrawer()'></div>"
                + "<aside id='drawer-panel' class='drawer-panel'>"
                + "<div class='drawer-header'>"
                + "<h2>📊 Panel</h2>"
                + "<button class='drawer-close' onclick='closeDrawer()' aria-label='Cerrar'>&times;</button>"
                + "</div>"
                + "<div class='drawer-body'>" + sideContent + "</div>"
                + "</aside>"
                + "<button id='fab-stats' class='fab' onclick='toggleDrawer()' aria-label='Abrir panel'>📊</button>";
        }

        var pageUser = member == null ? null : member.name();
        return page(group.name(),
            "<a href='/' class='back-link'>← Inicio</a>"
            + (success == null ? "" : "<div class='toast success'>" + escape(success) + "</div>")
            + topBar
            + viewToggle
            + "<div class='group-layout'>"
            + "<div class='col-main'>" + mainContent + "</div>"
            + "<aside class='col-side'>" + sideContent + "</aside>"
            + "</div>"
            + drawerHtml, pageUser);
    }

    public String errorPage(String title, String message) {
        return page(title, "<div class='toast error'>" + escape(message) + "</div><p><a href='/'>Volver al inicio</a></p>");
    }

    public String passwordResetResultPage(String username, String newPassword, String groupCode, String token, int jornada) {
        return page("Contraseña reseteada",
            "<div class='card' style='max-width:420px;margin:2rem auto;text-align:center'>"
            + "<h2>🔑 Contraseña reseteada</h2>"
            + "<p>Usuario: <strong>" + escape(username) + "</strong></p>"
            + "<p style='font-size:1.5rem;background:var(--surface);padding:0.75rem 1.5rem;border-radius:8px;font-family:monospace;letter-spacing:0.1em'>"
            + escape(newPassword) + "</p>"
            + "<p style='color:var(--muted)'>Guarda esta contraseña. Solo se muestra una vez.</p>"
            + "<a href='/groups/" + escape(groupCode) + "?token=" + escape(token) + "&jornada=" + jornada + "' class='btn'>Volver al grupo</a>"
            + "</div>");
    }

    // ── Match card ──
    private String matchCard(Group group, Match match, Member member, boolean tournamentStarted, int selectedJornada, boolean isCreator) {
        var started = match.isStarted();
        var finished = match.finished();
        var memberPrediction = member == null ? null : member.predictions().get(match.id());
        var starSelected = member != null && member.starByJornada().getOrDefault(match.jornada(), -1) == match.id();
        var jornadaIdx = match.jornada();
        // Star is locked only if the user already chose a star match AND that specific match has started
        var starLocked = member != null
            && member.starByJornada().containsKey(jornadaIdx)
            && group.matchById(member.starByJornada().get(jornadaIdx)).isStarted();

        // ═══ Grid columns: time | group | home | score/form | away | badge ═══

        // 1. Date + time (e.g. "Hoy 14:30" or "10 Jun 16:00")
        var timeHtml = "<div class='match-time'>" + formatKickoffCompact(match.kickoff()) + "</div>";

        // 2. Group badge (A–L)
        var groupHtml = "<div class='group-badge'>" + groupLetter(match.id()) + "</div>";

        // 3. Team scores for mobile compact list (inside each .team row)
        String homeTeamScore = "", awayTeamScore = "";
        if (finished) {
            homeTeamScore = "<span class='team-score'>" + match.homeGoals() + "</span>";
            awayTeamScore = "<span class='team-score'>" + match.awayGoals() + "</span>";
        } else if (started && match.hasLiveScore()) {
            homeTeamScore = "<span class='team-score live-score'>" + match.liveHomeGoals() + "</span>";
            awayTeamScore = "<span class='team-score live-score'>" + match.liveAwayGoals() + "</span>";
        } else if (started && memberPrediction != null) {
            homeTeamScore = "<span class='team-score'>" + memberPrediction.homeGoals() + "</span>";
            awayTeamScore = "<span class='team-score'>" + memberPrediction.awayGoals() + "</span>";
        }

        // 4. Determine if we show inline form (inputs inside each .team row)
        var isFormMode = member != null && !started;
        var homeFormVal = memberPrediction == null ? "" : String.valueOf(memberPrediction.homeGoals());
        var awayFormVal = memberPrediction == null ? "" : String.valueOf(memberPrediction.awayGoals());

        // 5. Home team: flag + name + [score (mobile) or input (form mode)]
        var homeHtml = "<div class='team team-home'>"
            + "<span class='flag'>" + flagOf(match.home()) + "</span>"
            + "<span class='team-name'>" + escape(teamEs(match.home())) + "</span>"
            + homeTeamScore;
        if (isFormMode) {
            homeHtml += "<input name='homeGoals' type='number' min='0' class='score-input team-input' value='" + homeFormVal + "' placeholder='0' required>";
        }
        homeHtml += "</div>";

        // 6. Score area — star badge back inside scoreHtml for correct desktop layout
        var starBadge = starSelected ? "<span class='star-icon'>⭐</span>" : "";
        var scoreHtml = "";
        if (finished) {
            scoreHtml = "<div class='match-score'><span class='score-home'>" + match.homeGoals() + "</span><span class='score-sep'>–</span><span class='score-away'>" + match.awayGoals() + "</span>" + starBadge + "</div>";
        } else if (started && match.hasLiveScore()) {
            scoreHtml = "<div class='match-score live'><span class='score-home'>" + match.liveHomeGoals() + "</span><span class='score-sep'>–</span><span class='score-away'>" + match.liveAwayGoals() + "</span></div>";
        } else if (member == null) {
            scoreHtml = "<span class='idle-msg'>🔒</span>";
        } else if (started) {
            scoreHtml = memberPrediction == null
                ? "<span class='idle-msg'>—</span>"
                : "<span class='pred-display'><span class='score-home'>" + memberPrediction.homeGoals() + "</span><span class='score-sep'>–</span><span class='score-away'>" + memberPrediction.awayGoals() + "</span></span>" + starBadge;
        }
        // Form mode: scoreHtml stays empty — the form wraps the teams instead

        // 7. Away team: flag + name + [score (mobile) or input (form mode)]
        // In form mode, override row-reverse so input appears on the right
        var awayHtml = "<div class='team team-away'>"
            + "<span class='flag'>" + flagOf(match.away()) + "</span>"
            + "<span class='team-name'>" + escape(teamEs(match.away())) + "</span>"
            + awayTeamScore;
        if (isFormMode) {
            awayHtml += "<input name='awayGoals' type='number' min='0' class='score-input team-input' value='" + awayFormVal + "' placeholder='0' required>";
        }
        awayHtml += "</div>";

        // 8. Status / result badge (rightmost column)
        var statusHtml = "";
        if (finished) {
            statusHtml = "<div class='match-status'>" + predictionResultBadge(match, memberPrediction, starSelected, member, group) + "</div>";
        } else if (started && match.hasLiveScore()) {
            statusHtml = "<div class='match-status playing'><span class='live-dot'></span>EN VIVO</div>";
        }

        // 9. Assemble: two modes
        //    - Form mode: <form> wraps .match-teams, inputs inside each .team, button after teams
        //    - Normal mode: separate .match-teams + .match-actions (score, status, star)
        String matchTeamsHtml, matchActionsHtml;
        if (isFormMode) {
            var confirmAttr = memberPrediction == null ? "" : " data-confirm=\"¿Actualizar tu pronóstico de " + homeFormVal + "–" + awayFormVal + " a otro resultado?\"";
            var btnLabel = memberPrediction == null ? "Pronosticar" : "Actualizar";
            matchTeamsHtml = "<form method='post' action='/groups/" + group.code() + "/prediction' class='score-form team-form'" + confirmAttr + ">"
                + hiddenToken(member.token()) + hiddenJornada(selectedJornada)
                + "<input type='hidden' name='matchId' value='" + match.id() + "'>"
                + "<div class='match-teams'>" + homeHtml + "<span class='vs-badge'>vs</span>" + awayHtml + "</div>"
                + "<button type='submit' class='btn-predict'>" + btnLabel + "</button>"
                + "</form>";
            matchActionsHtml = "<div class='match-actions'>" + statusHtml + starBadge + "</div>";
        } else {
            matchTeamsHtml = "<div class='match-teams'>" + homeHtml + "<span class='vs-badge'>vs</span>" + awayHtml + "</div>";
            var predInfo = finished && memberPrediction != null
                ? "<span class='pred-vs'><span class='pred-label'>PRONÓSTICO</span><span class='pred-score'>" + memberPrediction.homeGoals() + "<span class='pred-sep'>–</span>" + memberPrediction.awayGoals() + "</span></span>"
                : "";
            matchActionsHtml = "<div class='match-actions'>" + scoreHtml + statusHtml + predInfo + "</div>";
        }

        var mainRow = "<div class='match-row-main'>"
            + "<div class='match-row-top'>" + timeHtml + groupHtml + "</div>"
            + "<div class='match-row-body'>" + matchTeamsHtml + matchActionsHtml + "</div>"
            + "</div>";

        // ═══ Extras row (star, admin result, predictions toggle) ═══
        var extras = new StringBuilder();
        if (member != null && !match.knockout() && !match.isStarted() && !starLocked) {
            var label = starSelected ? "⭐ Quitar estrella" : "⭐ Marcar estrella";
            extras.append("<form method='post' action='/groups/").append(escape(group.code())).append("/star' class='star-form'>")
                .append(hiddenToken(member.token())).append(hiddenJornada(selectedJornada))
                .append("<input type='hidden' name='jornada' value='").append(match.jornada()).append("'>")
                .append("<input type='hidden' name='matchId' value='").append(match.id()).append("'>")
                .append("<button type='submit' class='btn-star ").append(starSelected ? "active" : "").append("'>").append(label).append("</button></form>");
        }
        extras.append(predictionsToggle(group, match, member, started));

        var extrasStr = extras.toString();
        var extrasHtml = extrasStr.isEmpty() ? "" : "<div class='match-row-extras'>" + extrasStr + "</div>";

        return "<article class='match-row' data-match-id='" + match.id() + "'>" + mainRow + extrasHtml + "</article>";
    }

    // ── Score prediction form (compact, for match-row) ──
    private String predictionForm(Group group, Match match, Member member, quinielamundial.domain.Prediction existing, int selectedJornada) {
        var home = existing == null ? "" : String.valueOf(existing.homeGoals());
        var away = existing == null ? "" : String.valueOf(existing.awayGoals());
        var confirmAttr = existing == null ? "" : " data-confirm=\"¿Actualizar tu pronóstico de " + home + "–" + away + " a otro resultado?\"";
        return "<form method='post' action='/groups/" + group.code() + "/prediction' class='score-form'" + confirmAttr + ">"
            + hiddenToken(member.token()) + hiddenJornada(selectedJornada)
            + "<input type='hidden' name='matchId' value='" + match.id() + "'>"
            + "<div class='score-inputs'>"
            + "<input name='homeGoals' type='number' min='0' class='score-input' value='" + home + "' placeholder='0' required>"
            + "<span class='score-sep'>–</span>"
            + "<input name='awayGoals' type='number' min='0' class='score-input' value='" + away + "' placeholder='0' required>"
            + "</div>"
            + "<button type='submit' class='btn-predict'>" + (existing == null ? "Pronosticar" : "Actualizar") + "</button>"
            + "</form>";
    }

    // ── Predictions toggle ──
    private String predictionsToggle(Group group, Match match, Member viewer, boolean started) {
        var viewerName = viewer == null ? null : viewer.name();
        var items = group.members().values().stream().map(other -> {
            var prediction = other.predictions().get(match.id());
            if (prediction == null) return "<li><b>" + escape(other.name()) + "</b>: —</li>";
            if (!started && !Objects.equals(other.name(), viewerName))
                return "<li><b>" + escape(other.name()) + "</b>: 🔒 oculto</li>";
            var scoreHtml = "<span class='pred-score'>" + prediction.homeGoals() + "–" + prediction.awayGoals() + "</span>"
                + (prediction.star() ? " ⭐" : "");
            return "<li><b>" + escape(other.name()) + "</b>: " + scoreHtml + "</li>";
        }).collect(Collectors.joining(""));

        var count = (int) group.members().values().stream().filter(m -> m.predictions().containsKey(match.id())).count();
        return "<details class='predictions-toggle'><summary>👥 Pronósticos (" + count + "/" + group.members().size() + ")</summary><ul>" + items + "</ul></details>";
    }

    // ── Knockout bracket view (accordion, like group-stage jornadas) ──
    private String bracketView(Group group, Member member, boolean isCreator, boolean tournamentStarted, String championTeam) {
        var koMatches = group.knockoutMatches();
        var groupFinished = group.groupStageFinished();

        // Group by round, ordering matches within each round by kickoff time
        var byRound = koMatches.stream()
            .sorted(Comparator.comparing(Match::kickoff))
            .collect(Collectors.groupingBy(Match::round, LinkedHashMap::new, Collectors.toList()));

        // Determine which round to open by default:
        // 1. If any round has in-progress matches → open that round (live badge)
        // 2. Otherwise, open the "current tournament round" — the highest-numbered
        //    round where ANY match has started (kicked off). This handles the case
        //    where a round's matches have all finished but the next hasn't started.
        // 3. If nothing has started → first upcoming round.
        // 4. If everything is finished → last round.
        int inProgressRound = 0;
        int currentTournamentRound = 0;
        int firstUpcoming = Integer.MAX_VALUE;
        int maxRound = 0;
        boolean anyStarted = false;
        for (var match : koMatches) {
            if (match.round() > maxRound) maxRound = match.round();
            if (!match.finished() && match.isStarted()) inProgressRound = match.round();
            if (match.isStarted()) {
                anyStarted = true;
                if (match.round() > currentTournamentRound) currentTournamentRound = match.round();
            }
            if (!match.isStarted() && match.round() < firstUpcoming) firstUpcoming = match.round();
        }
        int defaultOpenRound;
        if (inProgressRound > 0) {
            defaultOpenRound = inProgressRound;
        } else if (anyStarted) {
            defaultOpenRound = currentTournamentRound;
        } else if (firstUpcoming < Integer.MAX_VALUE) {
            defaultOpenRound = firstUpcoming;
        } else {
            defaultOpenRound = maxRound; // all finished, open last round
        }

        var sb = new StringBuilder();
        for (var entry : byRound.entrySet()) {
            int round = entry.getKey();
            var matches = entry.getValue();

            String roundName = switch (round) {
                case Match.ROUND_R32 -> "Dieciseisavos";
                case Match.ROUND_R16 -> "Octavos";
                case Match.ROUND_QF  -> "Cuartos";
                case Match.ROUND_SF  -> "Semifinales";
                case Match.ROUND_3RD -> "Tercer puesto";
                case Match.ROUND_FIN -> "Final";
                default -> "";
            };

            // Round statistics
            var total = matches.size();
            var finished = (int) matches.stream().filter(Match::finished).count();
            var started = (int) matches.stream().filter(m -> !m.finished() && m.isStarted()).count();
            var pending = total - finished - started;
            var isCurrent = round == inProgressRound && started > 0;
            var isOpen = round == defaultOpenRound;

            // ── Accordion section ──
            sb.append("<details class='jor-section'")
                .append(isOpen ? " open" : "")
                .append(">")
                .append("<summary class='jor-header")
                .append(isCurrent ? " jor-current" : "")
                .append("'><span class='jor-title'><span class='jor-num'>").append(escape(roundName)).append("</span>")
                .append(isCurrent ? " <span class='jor-now'>En vivo</span>" : "")
                .append("</span><span class='jor-meta'>")
                .append(total).append(" partidos")
                .append(pending > 0 ? " · <span class='jor-pending'>" + pending + " pendientes</span>" : "")
                .append(finished > 0 ? " · " + finished + " finalizados" : "")
                .append("</span></summary>")
                .append("<div class='jor-matches'>");

            // ── Knockout cards (wrapped for pending filter) ──
            for (var match : matches) {
                var isActive = member != null && !match.finished() && match.teamsKnown();
                sb.append("<div class='match-wrapper' data-active=\"").append(isActive).append("\">")
                    .append(knockoutCard(group, match, member, isCreator))
                    .append("</div>");
            }

            sb.append("</div></details>");
        }

        // ── Filter bar ──
        var filterBar = "<div class='filter-bar'>"
            + "<label class='filter-pending'><input type='checkbox' id='filterPending' onclick='togglePendingFilter()'>"
            + "<span>Solo pendientes</span></label>"
            + "<span class='filter-info' id='filterInfo'></span>"
            + "</div>";

        // ── Wrap in accordion container ──
        var accordion = "<div class='jor-accordion'>" + sb + "</div>";

        if (!groupFinished) {
            return "<div class='ko-pending'><div class='ko-pending-icon'>🔒</div><h2>Esperando a la fase de grupos</h2>"
                + "<p>Las eliminatorias se desbloquearán cuando los 72 partidos de la fase de grupos tengan resultado.</p></div>"
                + filterBar + accordion;
        }

        return filterBar + accordion;
    }

    private String knockoutCard(Group group, Match match, Member member, boolean isCreator) {
        var started = match.isStarted();
        var finished = match.finished();
        var teamsKnown = match.teamsKnown();
        var memberPrediction = member == null ? null : member.predictions().get(match.id());

        // ═══ Grid columns: time | round | home | score/form | away | badge ═══
        // 1. Date + time

        var timeHtml = "<div class='match-time'>" + formatKickoffCompact(match.kickoff()) + "</div>";

        // 2. Round badge
        var roundLabel = switch (match.round()) {
            case Match.ROUND_R32 -> "R32";
            case Match.ROUND_R16 -> "R16";
            case Match.ROUND_QF  -> "QF";
            case Match.ROUND_SF  -> "SF";
            case Match.ROUND_3RD -> "3rd";
            case Match.ROUND_FIN -> "FIN";
            default -> "";
        };
        var groupHtml = "<div class='group-badge'>" + roundLabel + "</div>";

        // 3. Team scores for mobile compact list
        String homeTeamScore = "", awayTeamScore = "";
        if (teamsKnown && finished) {
            homeTeamScore = "<span class='team-score'>" + match.homeGoals() + "</span>";
            awayTeamScore = "<span class='team-score'>" + match.awayGoals() + "</span>";
        } else if (teamsKnown && started && match.hasLiveScore()) {
            homeTeamScore = "<span class='team-score live-score'>" + match.liveHomeGoals() + "</span>";
            awayTeamScore = "<span class='team-score live-score'>" + match.liveAwayGoals() + "</span>";
        } else if (teamsKnown && started && memberPrediction != null) {
            homeTeamScore = "<span class='team-score'>" + memberPrediction.homeGoals() + "</span>";
            awayTeamScore = "<span class='team-score'>" + memberPrediction.awayGoals() + "</span>";
        }

        // 4. Form mode (teams known, not started, member present)
        var isFormMode = teamsKnown && member != null && !started;
        var homeFormVal = memberPrediction == null ? "" : String.valueOf(memberPrediction.homeGoals());
        var awayFormVal = memberPrediction == null ? "" : String.valueOf(memberPrediction.awayGoals());

        // 5. Home team
        String homeHtml;
        if (!teamsKnown && (match.home() == null || match.away() == null)) {
            var label = matchSourceLabel(match.id());
            homeHtml = "<div class='team team-home'><span class='team-name muted'>" + escape(label[0]) + "</span></div>";
        } else {
            homeHtml = "<div class='team team-home'>"
                + "<span class='flag'>" + flagOf(match.home()) + "</span>"
                + "<span class='team-name'>" + escape(teamEs(match.home())) + "</span>"
                + homeTeamScore;
            if (isFormMode) {
                homeHtml += "<input name='homeGoals' type='number' min='0' class='score-input team-input' value='" + homeFormVal + "' placeholder='0' required>";
            }
            homeHtml += "</div>";
        }

        // 6. Score area (empty for form mode — form wraps teams)
        var scoreHtml = "";
        if (!teamsKnown) {
            scoreHtml = "<span class='idle-msg'>🔒</span>";
        } else if (finished) {
            scoreHtml = "<div class='match-score'><span class='score-home'>" + match.homeGoals() + "</span><span class='score-sep'>–</span><span class='score-away'>" + match.awayGoals() + "</span></div>";
        } else if (started && match.hasLiveScore()) {
            scoreHtml = "<div class='match-score live'><span class='score-home'>" + match.liveHomeGoals() + "</span><span class='score-sep'>–</span><span class='score-away'>" + match.liveAwayGoals() + "</span></div>";
        } else if (member == null) {
            scoreHtml = "<span class='idle-msg'>🔒</span>";
        } else if (started) {
            scoreHtml = memberPrediction == null
                ? "<span class='idle-msg'>—</span>"
                : "<span class='pred-display'><span class='score-home'>" + memberPrediction.homeGoals() + "</span><span class='score-sep'>–</span><span class='score-away'>" + memberPrediction.awayGoals() + "</span></span>";
        }
        // Form mode: scoreHtml stays empty — form wraps the teams instead

        // 7. Away team
        String awayHtml;
        if (!teamsKnown && (match.home() == null || match.away() == null)) {
            var label = matchSourceLabel(match.id());
            awayHtml = "<div class='team team-away'><span class='team-name muted'>" + escape(label[1]) + "</span></div>";
        } else {
            var awayClass = "team team-away" + (isFormMode ? " team-form-mode" : "");
            awayHtml = "<div class='" + awayClass + "'>"
                + "<span class='flag'>" + flagOf(match.away()) + "</span>"
                + "<span class='team-name'>" + escape(teamEs(match.away())) + "</span>"
                + awayTeamScore;
            if (isFormMode) {
                awayHtml += "<input name='awayGoals' type='number' min='0' class='score-input team-input' value='" + awayFormVal + "' placeholder='0' required>";
            }
            awayHtml += "</div>";
        }

        // 8. Status / result badge
        var statusHtml = "";
        if (!teamsKnown) {
            statusHtml = "<div class='match-status locked'>🔒</div>";
        } else if (finished) {
            var badgeHtml = predictionResultBadge(match, memberPrediction, false, member, group);
            statusHtml = "<div class='match-status'>" + badgeHtml + "</div>";
        } else if (started && match.hasLiveScore()) {
            statusHtml = "<div class='match-status playing'><span class='live-dot'></span>EN VIVO</div>";
        }

        // 9. Assemble: form mode or normal
        String matchTeamsHtml, matchActionsHtml;
        if (isFormMode) {
            var confirmAttr = memberPrediction == null ? "" : " data-confirm=\"¿Actualizar tu pronóstico de " + homeFormVal + "–" + awayFormVal + " a otro resultado?\"";
            var btnLabel = memberPrediction == null ? "Pronosticar" : "Actualizar";

            matchTeamsHtml = "<form method='post' action='/groups/" + group.code() + "/prediction' class='score-form team-form'" + confirmAttr + ">"
                + hiddenToken(member.token()) + "<input type='hidden' name='jornada' value='0'>"
                + "<input type='hidden' name='matchId' value='" + match.id() + "'>"
                + "<div class='match-teams'>" + homeHtml + "<span class='vs-badge'>vs</span>" + awayHtml + "</div>"
                + "<button type='submit' class='btn-predict'>" + btnLabel + "</button>"
                + "</form>";
            matchActionsHtml = "<div class='match-actions'>" + statusHtml + "</div>";
        } else {
            matchTeamsHtml = "<div class='match-teams'>" + homeHtml + "<span class='vs-badge'>vs</span>" + awayHtml + "</div>";
            matchActionsHtml = "<div class='match-actions'>" + scoreHtml + statusHtml + "</div>";
        }

        var mainRow = "<div class='match-row-main'>"
            + "<div class='match-row-top'>" + timeHtml + groupHtml + "</div>"
            + "<div class='match-row-body'>" + matchTeamsHtml + matchActionsHtml + "</div>"
            + "</div>";

        // ═══ Extras ═══
        var extras = new StringBuilder();
        extras.append(predictionsToggle(group, match, member, started));

        var extrasStr = extras.toString();
        var extrasHtml = extrasStr.isEmpty() ? "" : "<div class='match-row-extras'>" + extrasStr + "</div>";

        return "<article class='match-row' data-match-id='" + match.id() + "'>" + mainRow + extrasHtml + "</article>";
    }

    /** Human-readable label for a KO match slot before teams are known. */
    private String[] matchSourceLabel(int matchId) {
        var sources = quinielamundial.service.WorldCupSchedule.bracketSources().get(matchId);
        if (sources == null) return new String[]{"?", "?"};
        return new String[]{formatSource(sources[0]), formatSource(sources[1])};
    }

    private String formatSource(String src) {
        if (src.startsWith("W")) return "Ganador partido " + src.substring(1);
        if (src.startsWith("L")) return "Perdedor partido " + src.substring(1);
        if (src.startsWith("3rd(")) {
            var inner = src.substring(4, src.length() - 1);
            return "3º(" + inner + ")";
        }
        // "1A", "2B", etc
        if (src.length() >= 2) {
            var group = src.substring(src.length() - 1);
            var pos = src.substring(0, src.length() - 1);
            return pos + "º Grupo " + group;
        }
        return src;
    }

    private String knockoutPredictionForm(Group group, Match match, Member member, quinielamundial.domain.Prediction existing) {
        var home = existing == null ? "" : String.valueOf(existing.homeGoals());
        var away = existing == null ? "" : String.valueOf(existing.awayGoals());
        var confirmAttr = existing == null ? "" : " data-confirm=\"¿Actualizar tu pronóstico de " + home + "–" + away + " a otro resultado?\"";
        return "<form method='post' action='/groups/" + group.code() + "/prediction' class='score-form'" + confirmAttr + ">"
            + "<input type='hidden' name='token' value='" + escape(member.token()) + "'>"
            + "<input type='hidden' name='jornada' value='0'>"
            + "<input type='hidden' name='matchId' value='" + match.id() + "'>"
            + "<div class='score-inputs'>"
            + "<input name='homeGoals' type='number' min='0' class='score-input' value='" + home + "' placeholder='0' required>"
            + "<span class='score-sep'>–</span>"
            + "<input name='awayGoals' type='number' min='0' class='score-input' value='" + away + "' placeholder='0' required>"
            + "</div>"
            + "<button type='submit' class='btn-predict'>" + (existing == null ? "Pronosticar" : "Actualizar") + "</button>"
            + "</form>";
    }


    // ── Forms ──
    private String championForm(Group group, Member member, List<String> candidates, boolean tournamentStarted, int selectedJornada, String championTeam) {
        var disabled = group.groupStageFinished() ? "disabled" : "";
        var current = member.championBet() == null ? "" : member.championBet();
        var options = candidates.stream().map(team -> "<option value='" + escape(team) + "' " + (team.equals(current) ? "selected" : "") + ">" + escape(team) + "</option>").collect(Collectors.joining(""));
        return "<div class='card'><h2>🏆 Tu apuesta al campeón</h2><p class='muted'>Campeón actual: <b>" + escape(championTeam) + "</b></p>"
            + "<form method='post' action='/groups/" + group.code() + "/champion' class='champion-form'>"
            + hiddenToken(member.token()) + hiddenJornada(selectedJornada)
            + "<select name='team' " + disabled + ">" + options + "</select>"
            + "<button type='submit' " + disabled + ">Guardar</button>"
            + "</form></div>";
    }

    public String championResultForm(String groupCode, List<String> candidates, int selectedJornada, String token) {
        var options = candidates.stream().map(team -> "<option value='" + escape(team) + "'>" + escape(team) + "</option>").collect(Collectors.joining(""));
        return "<div class='card'><h2>🏆 Campeón del torneo</h2>"
            + "<form method='post' action='/groups/" + escape(groupCode) + "/champion-result'>"
            + "<select name='team'>" + options + "</select>"
            + hiddenToken(token) + hiddenJornada(selectedJornada)
            + "<button type='submit'>Actualizar</button>"
            + "</form></div>";
    }

    public String adminResetSection(Group group, Member admin, int selectedJornada) {
        var groupCode = escape(group.code());
        var token = escape(admin.token());
        var members = group.members().values().stream()
            .filter(m -> !m.name().equals(admin.name()))
            .map(m -> {
                var name = escape(m.name());
                return "<li style='display:flex;align-items:center;justify-content:space-between;padding:6px 0;border-bottom:1px solid var(--border)'>"
                    + "<span>" + name + "</span>"
                    + "<div style='display:flex;gap:4px'>"
                    + "<form method='post' action='/groups/" + groupCode + "/admin/reset-password' style='display:inline'>"
                    + "<input type='hidden' name='token' value='" + token + "'>"
                    + "<input type='hidden' name='username' value='" + name + "'>"
                    + "<input type='hidden' name='jornada' value='" + selectedJornada + "'>"
                    + "<button type='submit' class='btn-admin' style='margin:0'>Resetear clave</button>"
                    + "</form>"
                    + "<form method='post' action='/groups/" + groupCode + "/admin/remove-member' style='display:inline'"
                    + " onsubmit=\"return confirmAction('¿Eliminar a " + name + " del grupo? Se borrarán todos sus pronósticos.')\">"
                    + "<input type='hidden' name='token' value='" + token + "'>"
                    + "<input type='hidden' name='username' value='" + name + "'>"
                    + "<input type='hidden' name='jornada' value='" + selectedJornada + "'>"
                    + "<button type='submit' class='btn-admin' style='margin:0;background:#dc2626'>Eliminar</button>"
                    + "</form>"
                    + "</div>"
                    + "</li>";
            })
            .collect(Collectors.joining());
        if (members.isEmpty()) return "";
        return "<div class='card'><h2>🔑 Administrar miembros</h2>"
            + "<ul style='list-style:none;padding:0;margin:0'>" + members + "</ul></div>";
    }

    // ── Leave group ──
    private String leaveGroupSection(Group group, Member member, int selectedJornada) {
        return "<div class='card' style='text-align:center;padding:16px'>"
            + "<form method='post' action='/groups/" + escape(group.code()) + "/leave'"
            + " onsubmit=\"return confirmAction('¿Salir del grupo " + escape(group.name()) + "? Se borrarán TODOS tus pronósticos. No se puede deshacer.')\">"
            + "<input type='hidden' name='token' value='" + escape(member.token()) + "'>"
            + "<input type='hidden' name='jornada' value='" + selectedJornada + "'>"
            + "<button type='submit' style='background:var(--red);box-shadow:none;width:100%'>🚪 Salir del grupo</button>"
            + "</form></div>";
    }

    // ── Leaderboard ──
    private String leaderboardSection(Group group, String championTeam) {
        var rows = group.leaderboard(championTeam).stream().map(entry -> {
            var rank = entry.rank();
            var medal = rank == 1 ? "<span class='rank-medal rank-1'>🥇</span>"
                : rank == 2 ? "<span class='rank-medal rank-2'>🥈</span>"
                : rank == 3 ? "<span class='rank-medal rank-3'>🥉</span>"
                : String.valueOf(rank);
            var stats = computeMemberStats(group, entry.member(), championTeam);
            return "<tr><td class='rank'>" + medal + "</td>"
                + "<td><b>" + escape(entry.member().name()) + "</b></td>"
                + "<td class='pts'>" + entry.score().totalPoints() + "</td>"
                + "<td>" + entry.score().exactHits() + "</td>"
                + "<td>" + entry.score().outcomeHits() + "</td>"
                + "<td>" + (entry.score().championHit() ? "✅" : "–") + "</td>"
                + "<td class='stat-pct'>" + String.format("%.0f", stats.hitPct()) + "%</td>"
                + "<td class='stat'>" + stats.bestStreak() + "</td></tr>";
        }).collect(Collectors.joining(""));
        return "<details class='card leaderboard' open><summary><h2>📊 Clasificación</h2></summary>"
            + "<div class='table-wrap'><table><thead><tr><th>#</th><th>Usuario</th><th>Pts</th><th>🎯</th><th>🎲</th><th>🏆</th><th>%</th><th>🔥</th></tr></thead><tbody>"
            + rows + "</tbody></table></div></details>";
    }

    // ── Helpers ──
    private String hiddenToken(String token) { return "<input type='hidden' name='token' value='" + escape(token) + "'>"; }
    private String hiddenJornada(int jornada) { return "<input type='hidden' name='jornada' value='" + jornada + "'>"; }
    private String escape(String value) { if (value == null) return ""; return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }

    // ═══════════════════════════════════════════════════════
    //  FLAGS & SPANISH TEAM NAMES
    // ═══════════════════════════════════════════════════════

    private static final Map<String, String> TEAM_FLAGS = new LinkedHashMap<>();
    private static final Map<String, String> TEAM_ES = new LinkedHashMap<>();
    static {
        // Flag map
        TEAM_FLAGS.put("Mexico", "\uD83C\uDDF2\uD83C\uDDFD");
        TEAM_FLAGS.put("South Africa", "\uD83C\uDFFF\uD83C\uDDE6");
        TEAM_FLAGS.put("South Korea", "\uD83C\uDDF0\uD83C\uDDF7");
        TEAM_FLAGS.put("Czechia", "\uD83C\uDDE8\uD83C\uDDFF");
        TEAM_FLAGS.put("Canada", "\uD83C\uDDE8\uD83C\uDDE6");
        TEAM_FLAGS.put("Bosnia and Herzegovina", "\uD83C\uDDE7\uD83C\uDDE6");
        TEAM_FLAGS.put("Qatar", "\uD83C\uDDF6\uD83C\uDDE6");
        TEAM_FLAGS.put("Switzerland", "\uD83C\uDDE8\uD83C\uDDED");
        TEAM_FLAGS.put("Brazil", "\uD83C\uDDE7\uD83C\uDDF7");
        TEAM_FLAGS.put("Morocco", "\uD83C\uDDF2\uD83C\uDDE6");
        TEAM_FLAGS.put("Haiti", "\uD83C\uDDED\uD83C\uDDF9");
        TEAM_FLAGS.put("Scotland", "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC73\uDB40\uDC63\uDB40\uDC74\uDB40\uDC7F");
        TEAM_FLAGS.put("USA", "\uD83C\uDDFA\uD83C\uDDF8");
        TEAM_FLAGS.put("Paraguay", "\uD83C\uDDF5\uD83C\uDDFE");
        TEAM_FLAGS.put("Australia", "\uD83C\uDDE6\uD83C\uDDFA");
        TEAM_FLAGS.put("Türkiye", "\uD83C\uDDF9\uD83C\uDDF7");
        TEAM_FLAGS.put("Germany", "\uD83C\uDDE9\uD83C\uDDEA");
        TEAM_FLAGS.put("Curaçao", "\uD83C\uDDE8\uD83C\uDDFC");
        TEAM_FLAGS.put("Ivory Coast", "\uD83C\uDDE8\uD83C\uDDEE");
        TEAM_FLAGS.put("Ecuador", "\uD83C\uDDEA\uD83C\uDDE8");
        TEAM_FLAGS.put("Netherlands", "\uD83C\uDDF3\uD83C\uDDF1");
        TEAM_FLAGS.put("Japan", "\uD83C\uDDEF\uD83C\uDDF5");
        TEAM_FLAGS.put("Sweden", "\uD83C\uDDF8\uD83C\uDDEA");
        TEAM_FLAGS.put("Tunisia", "\uD83C\uDDF9\uD83C\uDDF3");
        TEAM_FLAGS.put("Belgium", "\uD83C\uDDE7\uD83C\uDDEA");
        TEAM_FLAGS.put("Egypt", "\uD83C\uDDEA\uD83C\uDDEC");
        TEAM_FLAGS.put("Iran", "\uD83C\uDDEE\uD83C\uDDF7");
        TEAM_FLAGS.put("New Zealand", "\uD83C\uDDF3\uD83C\uDDFF");
        TEAM_FLAGS.put("Spain", "\uD83C\uDDEA\uD83C\uDDF8");
        TEAM_FLAGS.put("Cape Verde", "\uD83C\uDDE8\uD83C\uDDFB");
        TEAM_FLAGS.put("Saudi Arabia", "\uD83C\uDDF8\uD83C\uDDE6");
        TEAM_FLAGS.put("Uruguay", "\uD83C\uDDFA\uD83C\uDDFE");
        TEAM_FLAGS.put("France", "\uD83C\uDDEB\uD83C\uDDF7");
        TEAM_FLAGS.put("Senegal", "\uD83C\uDDF8\uD83C\uDDF3");
        TEAM_FLAGS.put("Iraq", "\uD83C\uDDEE\uD83C\uDDF6");
        TEAM_FLAGS.put("Norway", "\uD83C\uDDF3\uD83C\uDDF4");
        TEAM_FLAGS.put("Argentina", "\uD83C\uDDE6\uD83C\uDDF7");
        TEAM_FLAGS.put("Algeria", "\uD83C\uDDE9\uD83C\uDDFF");
        TEAM_FLAGS.put("Austria", "\uD83C\uDDE6\uD83C\uDDF9");
        TEAM_FLAGS.put("Jordan", "\uD83C\uDDEF\uD83C\uDDF4");
        TEAM_FLAGS.put("Portugal", "\uD83C\uDDF5\uD83C\uDDF9");
        TEAM_FLAGS.put("DR Congo", "\uD83C\uDDE8\uD83C\uDDE9");
        TEAM_FLAGS.put("Uzbekistan", "\uD83C\uDDFA\uD83C\uDDFF");
        TEAM_FLAGS.put("Colombia", "\uD83C\uDDE8\uD83C\uDDF4");
        TEAM_FLAGS.put("England", "\uD83C\uDFF4\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F");
        TEAM_FLAGS.put("Croatia", "\uD83C\uDDED\uD83C\uDDF7");
        TEAM_FLAGS.put("Ghana", "\uD83C\uDDEC\uD83C\uDDED");
        TEAM_FLAGS.put("Panama", "\uD83C\uDDF5\uD83C\uDDE6");

        // Spanish translations
        TEAM_ES.put("Mexico", "M\u00e9xico");
        TEAM_ES.put("South Africa", "Sud\u00e1frica");
        TEAM_ES.put("South Korea", "Corea del Sur");
        TEAM_ES.put("Czechia", "Chequia");
        TEAM_ES.put("Canada", "Canad\u00e1");
        TEAM_ES.put("Bosnia and Herzegovina", "Bosnia y Herzegovina");
        TEAM_ES.put("Qatar", "Catar");
        TEAM_ES.put("Switzerland", "Suiza");
        TEAM_ES.put("Brazil", "Brasil");
        TEAM_ES.put("Morocco", "Marruecos");
        TEAM_ES.put("Haiti", "Hait\u00ed");
        TEAM_ES.put("Scotland", "Escocia");
        TEAM_ES.put("USA", "EE. UU.");
        TEAM_ES.put("Paraguay", "Paraguay");
        TEAM_ES.put("Australia", "Australia");
        TEAM_ES.put("T\u00fcrkiye", "Turqu\u00eda");
        TEAM_ES.put("Germany", "Alemania");
        TEAM_ES.put("Cura\u00e7ao", "Curazao");
        TEAM_ES.put("Ivory Coast", "Costa de Marfil");
        TEAM_ES.put("Ecuador", "Ecuador");
        TEAM_ES.put("Netherlands", "Pa\u00edses Bajos");
        TEAM_ES.put("Japan", "Jap\u00f3n");
        TEAM_ES.put("Sweden", "Suecia");
        TEAM_ES.put("Tunisia", "T\u00fanez");
        TEAM_ES.put("Belgium", "B\u00e9lgica");
        TEAM_ES.put("Egypt", "Egipto");
        TEAM_ES.put("Iran", "Ir\u00e1n");
        TEAM_ES.put("New Zealand", "Nueva Zelanda");
        TEAM_ES.put("Spain", "Espa\u00f1a");
        TEAM_ES.put("Cape Verde", "Cabo Verde");
        TEAM_ES.put("Saudi Arabia", "Arabia Saud\u00ed");
        TEAM_ES.put("Uruguay", "Uruguay");
        TEAM_ES.put("France", "Francia");
        TEAM_ES.put("Senegal", "Senegal");
        TEAM_ES.put("Iraq", "Irak");
        TEAM_ES.put("Norway", "Noruega");
        TEAM_ES.put("Argentina", "Argentina");
        TEAM_ES.put("Algeria", "Argelia");
        TEAM_ES.put("Austria", "Austria");
        TEAM_ES.put("Jordan", "Jordania");
        TEAM_ES.put("Portugal", "Portugal");
        TEAM_ES.put("DR Congo", "RD Congo");
        TEAM_ES.put("Uzbekistan", "Uzbekist\u00e1n");
        TEAM_ES.put("Colombia", "Colombia");
        TEAM_ES.put("England", "Inglaterra");
        TEAM_ES.put("Croatia", "Croacia");
        TEAM_ES.put("Ghana", "Ghana");
        TEAM_ES.put("Panama", "Panam\u00e1");
    }

    private String flagOf(String team) {
        if (team == null) return "";
        return TEAM_FLAGS.getOrDefault(team, "");
    }

    /** Translate English team name to Spanish. */
    private String teamEs(String english) {
        if (english == null) return "";
        return TEAM_ES.getOrDefault(english, english);
    }

    /** Returns e.g. "🇪🇸 España" (flag + Spanish name, HTML-escaped). */
    private String teamWithFlagEs(String team) {
        return flagOf(team) + escape(teamEs(team));
    }

    /** Derives the FIFA group letter (A–L) from a group-stage match ID (1–72). */
    private String groupLetter(int matchId) {
        if (matchId < 1 || matchId > 72) return "";
        return String.valueOf((char) ('A' + (matchId - 1) / 6));
    }

    /** Returns just HH:mm in Spain timezone for the compact match row. */
    private String formatKickoffTime(Instant kickoff) {
        var zdt = ZonedDateTime.ofInstant(kickoff, SPAIN_ZONE);
        return zdt.format(SPAIN_TIME_ONLY);
    }

    /** Compact date + time for match rows: "Hoy 14:30" or "10 Jun 16:00" */
    private String formatKickoffCompact(Instant kickoff) {
        if (kickoff == null) return "<span class='match-d'>—</span><span class='match-h'>??</span>";
        var zdt = ZonedDateTime.ofInstant(kickoff, SPAIN_ZONE);
        var today = LocalDate.now(SPAIN_ZONE);
        var matchDay = zdt.toLocalDate();
        String label;
        if (matchDay.equals(today)) {
            label = "<span class='match-d match-d-rel'>Hoy</span>";
        } else if (matchDay.equals(today.plusDays(1))) {
            label = "<span class='match-d match-d-rel'>Mañana</span>";
        } else {
            label = "<span class='match-d'>" + zdt.format(DateTimeFormatter.ofPattern("d MMM", new Locale("es"))) + "</span>";
        }
        return label + "<span class='match-h'>" + zdt.format(SPAIN_TIME_ONLY) + "</span>";
    }

    /** Build a coloured result indicator for a finished match + user prediction.
     *  Returns e.g. "🎯 +6 ⭐" (exact + star), "🎯 +3" (exact), "✅ +2 ⭐" (winner + star),
     *  "✅ +1" (winner), "❌ +0" (wrong), or "—" (no prediction). */
    private String predictionResultBadge(Match match, quinielamundial.domain.Prediction prediction, boolean starMatch, Member member, Group group) {
        if (!match.finished()) return "";
        // Group comparison: how many members got this match right
        var vsGroup = "";
        if (group != null) {
            var actual = match.result();
            var total = (int) group.members().values().stream()
                .filter(m -> m.predictions().containsKey(match.id()))
                .count();
            var hits = total == 0 ? 0 : (int) group.members().values().stream()
                .filter(m -> {
                    var p = m.predictions().get(match.id());
                    if (p == null || actual == null) return false;
                    // Direct comparison: exact score OR correct outcome for THIS match only
                    return (p.homeGoals() == match.homeGoals() && p.awayGoals() == match.awayGoals())
                        || p.outcome().equals(actual);
                })
                .count();
            if (total > 0) {
                vsGroup = " <span class='dot-vs'>" + hits + "/" + total + "</span>";
            }
        }
        if (prediction == null) return "<span class='result-dot dot-none'>—" + vsGroup + "</span>";

        // Determine points — same logic for group AND knockout (no more advancing picker)
        int base;
        String icon, label;
        if (prediction.homeGoals() == match.homeGoals() && prediction.awayGoals() == match.awayGoals()) {
            base = 3; icon = "🎯"; label = "Exacto";
        } else {
            var actual = match.result();
            if (actual != null && prediction.outcome().equals(actual)) {
                base = 1; icon = "✅"; label = "Ganador";
            } else {
                base = 0; icon = "❌"; label = "Fallado";
            }
        }
        var pts = starMatch ? base * 2 : base;
        var starIcon = starMatch && base > 0 ? " ⭐" : "";
        return "<span class='result-dot dot-" + (base == 3 ? "exact" : base == 1 ? "winner" : "wrong") + "'>" + icon + " +" + pts + starIcon + vsGroup + "</span>";
    }

    /**
     * Determina la jornada que debe aparecer expandida por defecto:
     * 1. Si hay jornadas con partidos en curso (empezados pero no terminados) → la de mayor número.
     * 2. Si no hay en curso pero hay pendientes → la siguiente jornada a jugar (la de menor número con partidos pendientes).
     * 3. Si todas están finalizadas → la última jornada (Jornada 3).
     * 4. Si nada ha empezado → Jornada 1 (cae en el caso 2, la primera con pendientes es la 1).
     */
    private int currentJornada(Group group) {
        var now = Instant.now();
        // 1. Jornada en curso: partidos empezados pero no terminados
        var inProgress = group.matches().stream()
            .filter(m -> !m.finished() && m.kickoff().isBefore(now))
            .mapToInt(Match::jornada)
            .max();
        if (inProgress.isPresent()) return inProgress.getAsInt();
        // 2. Próxima jornada con partidos pendientes (no han empezado aún)
        var upcoming = group.matches().stream()
            .filter(m -> !m.finished() && m.kickoff().isAfter(now))
            .mapToInt(Match::jornada)
            .min();
        if (upcoming.isPresent()) return upcoming.getAsInt();
        // 3. Todo terminado → última jornada
        return group.matches().stream()
            .mapToInt(Match::jornada)
            .max()
            .orElse(1);
    }

    /** Format kickoff time from UTC to Spain timezone (CEST, UTC+2 in June). */
    private static final DateTimeFormatter SPAIN_DTF = DateTimeFormatter.ofPattern("d MMM yyyy • HH:mm", new Locale("es"));
    private static final DateTimeFormatter SPAIN_TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm", new Locale("es"));
    private static final ZoneId SPAIN_ZONE = ZoneId.of("Europe/Madrid");

    private String formatKickoffSpain(Instant kickoff) {
        var zdt = ZonedDateTime.ofInstant(kickoff, SPAIN_ZONE);
        var today = LocalDate.now(SPAIN_ZONE);
        var matchDay = zdt.toLocalDate();
        var relative = "";
        if (matchDay.equals(today)) {
            relative = "<span class='match-rel-today'>Hoy</span> • ";
        } else if (matchDay.equals(today.plusDays(1))) {
            relative = "<span class='match-rel-tomorrow'>Mañana</span> • ";
        } else if (matchDay.equals(today.plusDays(2))) {
            relative = "<span class='match-rel-soon'>Pasado mañana</span> • ";
        }
        return relative + zdt.format(SPAIN_DTF);
    }

    private String page(String title, String body) { return page(title, body, null); }

    private String page(String title, String body, String loggedInUser) {
        var hdr = buildHeader(loggedInUser);
        return "<!doctype html><html lang='es'><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>"
            + "<title>" + escape(title) + " — Quiniela Mundial 2026</title>"
            + "<link rel='preconnect' href='https://fonts.googleapis.com'>"
            + "<link rel='preconnect' href='https://fonts.gstatic.com' crossorigin>"
            + "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;500;600;700&display=swap' rel='stylesheet'>"
            + "<style>"
            // ═══════════════════════════════════════
            //  DESIGN TOKENS
            // ═══════════════════════════════════════
             + ":root{"
             + "--bg:#0B1020;--surface:#131A2E;--surface-hover:#1A2240;--surface-alt:#0E1528"
             + ";--surface2:#1A2240"
             + ";--border:#1E2A45;--border-light:#2A3A5A"
             + ";--text:#F0F4FF;--text-sec:#8899BB;--text-mid:#8899BB;--text-dim:#5A6A8A"
             + ";--pri:#00D4FF;--pri-hover:#33DDFF;--pri-light:rgba(0,212,255,.12)"
             + ";--accent:#7C3AED;--accent-light:rgba(124,58,237,.12)"
             + ";--green:#10B981;--green-dim:#0A2E1A;--green-light:rgba(16,185,129,.10);--green-border:rgba(16,185,129,.25)"
             + ";--red:#EF4444;--red-light:rgba(239,68,68,.10);--red-border:rgba(239,68,68,.2)"
             + ";--yellow:#F59E0B;--yellow-light:rgba(245,158,11,.10);--yellow-border:rgba(245,158,11,.2)"
             + ";--gold:#F59E0B;--silver:#6A7A9A;--bronze:#B45309"
             + ";--radius-sm:8px;--radius-md:12px;--radius-lg:16px;--radius-xl:20px"
             + ";--gradient:linear-gradient(135deg,#00D4FF,#7C3AED)"
             + ";--gradient-glow:0 0 24px rgba(0,212,255,.12),0 0 48px rgba(124,58,237,.08)"
             + ";--shadow-sm:0 2px 8px rgba(0,0,0,.25)"
             + ";--shadow-md:0 8px 24px rgba(0,0,0,.3)"
             + ";--shadow-lg:0 16px 48px rgba(0,0,0,.35)"
             + ";--header-bg:rgba(11,16,32,.92)"
             + ";--font:'Inter',-apple-system,BlinkMacSystemFont,'Segoe UI',system-ui,sans-serif"
             + ";--font-mono:'JetBrains Mono','Fira Code','Courier New',monospace}"
             + "[data-theme=light]{"
             + "--bg:#F2F4F8;--surface:#FFFFFF;--surface-hover:#EDEEF2;--surface-alt:#F0F1F5"
             + ";--surface2:#F0F1F5"
             + ";--border:#DDE1E9;--border-light:#CBD0DA"
             + ";--text:#0B1020;--text-sec:#5A6A8A;--text-mid:#5A6A8A;--text-dim:#8899BB"
             + ";--pri:#009DC4;--pri-hover:#007A9E;--pri-light:rgba(0,157,196,.10)"
             + ";--accent:#6D28D9;--accent-light:rgba(109,40,217,.10)"
             + ";--green:#059669;--green-dim:#D1FAE5;--green-light:rgba(5,150,105,.08);--green-border:rgba(5,150,105,.2)"
             + ";--red:#DC2626;--red-light:rgba(220,38,38,.08);--red-border:rgba(220,38,38,.2)"
             + ";--yellow:#D97706;--yellow-light:rgba(217,119,6,.08);--yellow-border:rgba(217,119,6,.2)"
             + ";--gold:#D97706;--silver:#5A6A8A;--bronze:#92400E"
             + ";--gradient:linear-gradient(135deg,#009DC4,#6D28D9)"
             + ";--gradient-glow:none"
             + ";--shadow-sm:0 1px 3px rgba(0,0,0,.06),0 1px 2px rgba(0,0,0,.04)"
             + ";--shadow-md:0 4px 12px rgba(0,0,0,.07),0 2px 4px rgba(0,0,0,.04)"
             + ";--shadow-lg:0 8px 24px rgba(0,0,0,.08),0 4px 8px rgba(0,0,0,.04)"
             + ";--header-bg:rgba(242,244,248,.92)}"

            // ═══════════════════════════════════════
            //  BASE
            // ═══════════════════════════════════════
            + "*,*::before,*::after{box-sizing:border-box;margin:0;padding:0}"
            + "html{scroll-behavior:smooth;-webkit-font-smoothing:antialiased;-moz-osx-font-smoothing:grayscale;overflow-x:hidden}"
            + "body{font-family:var(--font);background:var(--bg);color:var(--text);min-height:100vh;display:flex;flex-direction:column;font-size:clamp(14px,1.3vw,16px);line-height:1.5;overflow-x:hidden;width:100%}"
            + "main{flex:1;width:100%;max-width:min(1120px,100% - 1.5rem);margin:0 auto;padding:clamp(16px,2.5vw,32px) clamp(12px,2vw,24px) clamp(32px,4vw,48px)}"
            + "a{color:var(--pri);text-decoration:none;transition:color .15s ease;word-break:break-word}"
            + "a:hover{color:var(--pri-hover)}"
            + "img{max-width:100%;height:auto}"
            + "p{overflow-wrap:break-word;word-break:break-word}"

            // ═══════════════════════════════════════
            //  FORMS
            // ═══════════════════════════════════════
            + "input,select,textarea{padding:clamp(10px,1.5vw,14px);border-radius:var(--radius-md);border:1px solid var(--border);background:var(--surface);color:var(--text);font-size:clamp(14px,1.2vw,16px);outline:none;transition:border-color .15s ease,box-shadow .15s ease;font-family:var(--font);width:100%;min-height:44px}"
            + "input:focus,select:focus{border-color:var(--pri);box-shadow:0 0 0 3px var(--pri-light)}"
            + "input::placeholder{color:var(--text-dim)}"
            + "button{padding:clamp(10px,1.2vw,14px) clamp(16px,2vw,24px);border-radius:100px;border:none;background:var(--gradient);color:#fff;font-size:clamp(14px,1.2vw,16px);font-weight:700;cursor:pointer;transition:all .2s ease;font-family:var(--font);display:inline-flex;align-items:center;justify-content:center;gap:8px;min-height:44px;box-shadow:0 2px 8px rgba(0,212,255,.15)}"
            + "button:hover{opacity:.9;transform:translateY(-1px);box-shadow:0 4px 16px rgba(0,212,255,.25)}"
            + "button:active{transform:scale(.98)}"
            + "button:disabled{opacity:.4;cursor:not-allowed;transform:none!important}"

            // ═══════════════════════════════════════
            //  COMPONENTS
            // ═══════════════════════════════════════

            // Card
            + ".card{background:var(--surface);border:1px solid var(--border);border-radius:clamp(var(--radius-md),1.5vw,var(--radius-lg));padding:clamp(16px,2.5vw,28px);margin:clamp(14px,2vw,24px) 0;box-shadow:var(--shadow-sm);transition:border-color .2s ease,box-shadow .2s ease}"
            + ".card:hover{border-color:var(--border-light);box-shadow:var(--shadow-md)}"
            + ".card h2{font-size:clamp(16px,1.5vw,20px);font-weight:700;margin-bottom:clamp(10px,1.2vw,16px);color:var(--text);letter-spacing:-.02em}"
            + ".muted{color:var(--text-sec);font-size:clamp(13px,1.2vw,15px);line-height:1.7}"

            // Toasts
            + ".toast{padding:clamp(10px,1.2vw,14px) clamp(14px,1.5vw,20px);border-radius:var(--radius-md);margin-bottom:clamp(12px,1.5vw,20px);font-weight:500;font-size:clamp(13px,1.2vw,15px);display:flex;align-items:center;gap:8px;border:1px solid transparent;transition:opacity .3s ease,transform .3s ease}"
            + ".toast.error{background:var(--red-light);color:var(--red);border-color:var(--red-border)}"
            + ".toast.success{background:var(--green-light);color:var(--green);border-color:var(--green-border)}"

            // ═══════════════════════════════════════
            //  HEADER
            // ═══════════════════════════════════════
            + ".site-header{position:sticky;top:0;z-index:100;background:var(--header-bg);backdrop-filter:blur(20px);-webkit-backdrop-filter:blur(20px);border-bottom:1px solid var(--border);padding:0 max(12px,2vw);display:flex;align-items:center;justify-content:space-between}"
            + ".header-inner{max-width:min(1120px,100% - 1rem);margin:0 auto;display:flex;align-items:center;justify-content:space-between;height:clamp(52px,6vh,64px)}"
            + ".header-logo{display:flex;align-items:center;gap:clamp(8px,1vw,14px);font-size:clamp(15px,1.5vw,20px);font-weight:800;color:var(--text);text-decoration:none;letter-spacing:-.03em;background:var(--gradient);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}"
            + ".header-logo:hover{opacity:.9;text-decoration:none}"
            + ".header-logo .logo-icon{width:clamp(30px,2.8vw,38px);height:clamp(30px,2.8vw,38px);border-radius:var(--radius-sm);background:var(--gradient);color:#fff;display:flex;align-items:center;justify-content:center;font-size:clamp(15px,1.4vw,19px);font-weight:700;-webkit-text-fill-color:#fff;box-shadow:var(--gradient-glow)}"
            + ".header-nav{display:flex;align-items:center;gap:clamp(6px,.8vw,12px)}"
            + ".header-nav .user-name{font-size:clamp(12px,1.1vw,14px);color:var(--text-sec);font-weight:500;padding:0 clamp(6px,.8vw,12px);max-width:clamp(80px,10vw,140px);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}"
            + ".header-btn{font-size:clamp(12px,1.1vw,14px);padding:clamp(6px,.8vw,10px) clamp(12px,1.5vw,20px);border-radius:100px;color:var(--text-sec);border:1px solid var(--border);background:transparent;text-decoration:none;transition:all .2s ease;font-weight:500;min-height:38px;display:inline-flex;align-items:center}"
            + ".header-btn:hover{background:var(--surface-hover);color:var(--text);border-color:var(--text-dim);text-decoration:none}"
            + ".header-btn.primary{background:var(--gradient);color:#fff;border:none}"
            + ".header-btn.primary:hover{opacity:.9}"

            // ═══════════════════════════════════════
            //  FOOTER
            // ═══════════════════════════════════════
            + ".site-footer{border-top:1px solid var(--border);padding:clamp(20px,2.5vw,36px);text-align:center;font-size:clamp(12px,1.1vw,14px);color:var(--text-dim);margin-top:auto}"

            // ═══════════════════════════════════════
            //  AUTH PAGES
            // ═══════════════════════════════════════
            + ".auth-page{max-width:min(420px,100% - 2rem);margin:clamp(32px,8vh,80px) auto}"
            + ".auth-card{background:var(--surface);border:1px solid var(--border);border-radius:clamp(var(--radius-lg),2vw,var(--radius-xl));padding:clamp(28px,4vw,48px);box-shadow:var(--shadow-lg);position:relative;overflow:hidden}"
            + ".auth-card::before{content:'';position:absolute;top:0;left:0;right:0;height:3px;background:var(--gradient);opacity:.5}"
            + ".auth-card h2{font-size:clamp(24px,3vw,32px);margin-bottom:clamp(24px,3vw,36px);text-align:center;font-weight:800;letter-spacing:-.03em;background:var(--gradient);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}"
            + ".auth-form{display:flex;flex-direction:column;gap:clamp(12px,1.5vw,18px)}"
            + ".auth-form input{width:100%}"
            + ".auth-card .muted{text-align:center;margin-top:clamp(16px,2vw,24px);font-size:clamp(13px,1.1vw,14px)}"

            // ═══════════════════════════════════════
            //  HOME PAGE
            // ═══════════════════════════════════════
            + ".home-hero{text-align:center;padding:clamp(36px,6vw,72px) clamp(16px,3vw,32px) clamp(28px,4vw,56px)}"
            + ".home-hero h1{font-size:clamp(28px,4.5vw,52px);margin-bottom:clamp(8px,1.2vw,16px);font-weight:800;letter-spacing:-.03em;line-height:1.15;background:var(--gradient);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}"
            + ".home-hero p{color:var(--text-sec);max-width:min(500px,100%);margin:0 auto;line-height:1.7;font-size:clamp(14px,1.4vw,18px)}"
            + ".home-cards{display:grid;grid-template-columns:1fr;gap:clamp(12px,1.5vw,24px);margin-top:clamp(12px,1.5vw,20px)}"
            + "@media(min-width:560px){.home-cards{grid-template-columns:1fr 1fr}}"
            + ".home-cards .card{padding:clamp(20px,2.5vw,28px);margin:0}"
            + ".home-cards .card h2{font-size:clamp(15px,1.4vw,18px);margin-bottom:clamp(12px,1.5vw,20px)}"
            + ".home-cards form{display:flex;flex-direction:column;gap:clamp(10px,1.2vw,16px)}"
            + ".home-cards form button{margin-top:clamp(4px,.5vw,8px)}"
            + ".section-title{font-size:clamp(18px,2vw,24px);font-weight:700;margin:clamp(28px,4vw,48px) 0 clamp(12px,1.5vw,20px);color:var(--text);letter-spacing:-.02em}"
            + ".my-groups-grid{display:grid;grid-template-columns:1fr;gap:clamp(10px,1.2vw,16px)}"
            + "@media(min-width:560px){.my-groups-grid{grid-template-columns:1fr 1fr}}"
            + ".my-group-card{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-md);padding:clamp(12px,1.5vw,18px) clamp(16px,2vw,24px);display:flex;justify-content:space-between;align-items:center;text-decoration:none;transition:all .15s ease;box-shadow:var(--shadow-sm);min-height:44px}"
            + ".my-group-card:hover{border-color:var(--pri);text-decoration:none;box-shadow:0 0 0 1px var(--pri)}"
            + ".my-group-card .name{font-weight:600;font-size:clamp(14px,1.3vw,16px);color:var(--text)}"
            + ".my-group-card .code{font-size:clamp(11px,1vw,13px);color:var(--text-dim);margin-top:2px}"
            + ".my-group-card .arrow{color:var(--text-dim);font-size:clamp(16px,1.5vw,20px);transition:transform .15s ease}"
            + ".my-group-card:hover .arrow{color:var(--pri);transform:translateX(4px)}"
            + ".my-group-card.enhanced{align-items:stretch;gap:clamp(10px,1.2vw,16px);padding:clamp(14px,1.8vw,22px) clamp(16px,2vw,24px)}"
            + ".my-card-body{display:flex;flex-direction:column;gap:clamp(4px,.6vw,8px);flex:1;min-width:0}"
            + ".my-card-body .my-meta{font-size:clamp(12px,1.1vw,14px);color:var(--text-sec);font-weight:500}"
            + ".my-card-right{display:flex;align-items:center;justify-content:center;flex-shrink:0}"
            + ".my-pending{font-size:clamp(11px,1vw,13px);color:var(--yellow);font-weight:600;display:inline-flex;align-items:center;gap:clamp(4px,.5vw,6px)}"
            + ".my-pending.done{color:var(--green)}"
            + ".all-groups-list{list-style:none;display:flex;flex-direction:column;gap:6px}"
            + ".all-groups-list li{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-sm);padding:clamp(10px,1.2vw,14px) clamp(12px,1.5vw,18px);font-size:clamp(13px,1.2vw,15px);display:flex;justify-content:space-between;align-items:center;min-height:44px}"
            + ".all-groups-list li .code-tag{font-size:clamp(10px,.9vw,12px);background:var(--surface-alt);padding:2px 10px;border-radius:4px;color:var(--text-dim);font-family:ui-monospace,monospace;letter-spacing:.02em}"
            + "details.card summary{cursor:pointer;padding:clamp(4px,.5vw,8px) 0;user-select:none;color:var(--text-sec);font-size:clamp(13px,1.2vw,15px)}"
            + "details.card summary:hover{color:var(--text)}"
            + "details.card summary::-webkit-details-marker{color:var(--text-dim)}"
            + ".rules-list{margin-top:clamp(12px,1.5vw,20px);padding-left:0;list-style:none;display:flex;flex-direction:column;gap:clamp(6px,.7vw,10px)}"
            + ".rules-list li{padding:clamp(10px,1.2vw,16px) clamp(12px,1.5vw,20px);background:var(--surface-alt);border:1px solid var(--border);border-radius:var(--radius-md);font-size:clamp(13px,1.2vw,15px);color:var(--text);display:flex;align-items:center;gap:clamp(8px,1vw,14px);line-height:1.5;min-height:44px}"
            + ".rules-list li .rule-icon{width:clamp(20px,2vw,28px);text-align:center;flex-shrink:0}"

            // ═══════════════════════════════════════
            //  GROUP PAGE
            // ═══════════════════════════════════════

            // Back link
            + ".back-link{display:inline-flex;align-items:center;gap:6px;margin-bottom:clamp(12px,1.5vw,20px);font-size:clamp(12px,1.1vw,14px);color:var(--text-dim)}"
            + ".back-link:hover{color:var(--text-sec)}"

            // Group header
            + ".group-header{display:flex;justify-content:space-between;align-items:flex-start;flex-wrap:wrap;gap:clamp(10px,1.2vw,16px);margin-bottom:clamp(16px,2.5vw,28px)}"
            + ".group-header .title-area h1{font-size:clamp(22px,3.5vw,36px);font-weight:800;margin:0;letter-spacing:-.03em;line-height:1.15;background:var(--gradient);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}"
            + ".group-header .title-area .meta{display:flex;align-items:center;gap:clamp(8px,1vw,14px);margin-top:clamp(4px,.6vw,8px);flex-wrap:wrap}"
            + ".code-tag{font-size:clamp(10px,.9vw,12px);background:var(--surface-alt);padding:clamp(3px,.4vw,5px) clamp(8px,1vw,12px);border-radius:6px;color:var(--text-sec);font-family:ui-monospace,monospace;font-weight:500;border:1px solid var(--border);letter-spacing:.02em}"
            + ".group-header .user-area .user-badge{background:var(--surface);border:1px solid var(--border);border-radius:20px;padding:clamp(5px,.6vw,8px) clamp(12px,1.5vw,20px);font-size:clamp(12px,1.1vw,14px);font-weight:500;display:inline-flex;align-items:center;min-height:36px}"
            + ".user-not-joined{border-color:var(--yellow)!important}"
            + ".user-not-joined a{color:var(--yellow)!important}"

            // Tabs — pill style (horizontal scroll on mobile)
            + ".tabs{display:flex;gap:6px;margin-bottom:clamp(16px,2.5vw,28px);overflow-x:auto;flex-wrap:nowrap;padding-bottom:4px;-webkit-overflow-scrolling:touch;scrollbar-width:none}"
            + ".tabs::-webkit-scrollbar{display:none}"
            + ".tab{padding:clamp(8px,1vw,12px) clamp(16px,1.8vw,24px);border-radius:100px;background:var(--surface);border:1px solid var(--border);color:var(--text-sec);font-size:clamp(12px,1.1vw,14px);font-weight:600;transition:all .2s ease;text-decoration:none;white-space:nowrap;flex-shrink:0}"
            + ".tab:hover{background:var(--surface-hover);border-color:var(--text-dim);color:var(--text);text-decoration:none}"
            + ".tab.active{background:var(--gradient);border:none;color:#fff}"
            + ".tab-current{position:relative}"
            + ".tab-current:not(.active)::after{content:'';position:absolute;top:-2px;right:-2px;width:8px;height:8px;border-radius:50%;background:var(--pri);box-shadow:0 0 6px rgba(0,212,255,.6)}"
            + ".tabs-spacer{height:8px}"

            // Layout: two columns (single column on mobile, sidebar on desktop)
            + ".group-layout{display:grid;grid-template-columns:1fr;gap:clamp(16px,2vw,28px);align-items:start}"
            + "@media(min-width:900px){.group-layout{grid-template-columns:1fr 300px}}"
            + "@media(min-width:1200px){.group-layout{grid-template-columns:1fr 320px}}"
            + ".col-main{min-width:0}"
            + ".col-side{display:flex;flex-direction:column;gap:clamp(12px,1.5vw,20px)}"
            + "@media(min-width:900px){.col-side{position:sticky;top:clamp(56px,7vh,72px)}}"
            + "@media(max-width:899px){.col-side{display:none}}"

            // ═══════════════════════════════════════
            //  MATCH ROWS — compact list layout (mobile-style, all sizes)
            // ═══════════════════════════════════════
            + ".matches{display:flex;flex-direction:column;gap:clamp(10px,1.2vw,16px)}"
            + ".match-row{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);overflow:hidden;transition:all .25s cubic-bezier(.4,0,.2,1);box-shadow:var(--shadow-sm);position:relative}"
            + ".match-row::before{content:'';position:absolute;top:0;left:0;width:3px;height:100%;background:var(--gradient);border-radius:3px 0 0 3px;opacity:.6}"
            + ".match-row:hover{border-color:var(--border-light);transform:translateY(-2px);box-shadow:var(--shadow-md)}"
            + ".match-row-main{display:flex;flex-direction:column;gap:clamp(4px,.5vw,8px);padding:clamp(6px,1.8vw,10px) clamp(6px,1.8vw,10px)}"
            + ".match-row-top{display:flex;align-items:center;gap:clamp(6px,.7vw,10px)}"
            + ".match-row-body{display:flex;flex-direction:column;gap:clamp(4px,.6vw,8px)}"
            + ".match-teams{display:flex;flex-direction:column;gap:clamp(2px,.3vw,5px)}"
            // Score display (centered) — hidden behind team-score layout
            + ".match-score,.pred-display{display:inline-flex;align-items:center;gap:clamp(2px,.3vw,6px);font-family:var(--font-mono);font-size:clamp(16px,1.6vw,22px);font-weight:700;color:var(--text);letter-spacing:-.03em}"
            // Team score inside each team row — always visible
            + ".team-score{display:inline-block;font-family:var(--font-mono);font-size:clamp(18px,4.5vw,26px);font-weight:700;color:var(--text);margin-left:auto;flex-shrink:0;line-height:1}"
            + ".team-score.live-score,.match-score.live .score-home,.match-score.live .score-away{color:#22c55e}"
            + ".vs-badge{display:none}"
            + ".match-actions{display:flex;align-items:center;justify-content:center;gap:clamp(4px,.5vw,8px);width:100%;flex-wrap:wrap}"
            + ".match-actions .match-score,.match-actions .pred-display{display:none}"
            + ".pred-vs{display:inline-flex;align-items:center;gap:clamp(4px,.4vw,7px);white-space:nowrap}"
            + ".pred-vs .pred-label{font-size:clamp(9px,.75vw,10px);font-weight:700;color:var(--text-dim);letter-spacing:.06em;text-transform:uppercase}"
            + ".pred-vs .pred-score{font-family:var(--font-mono);font-size:clamp(13px,1.1vw,16px);font-weight:700;color:var(--text-sec);letter-spacing:-.02em}"
            + ".pred-vs .pred-sep{font-family:var(--font-mono);font-size:clamp(11px,1vw,14px);color:var(--text-dim);margin:0 1px}"
            + ".team-form{display:flex;flex-direction:column;align-items:center;gap:clamp(6px,.6vw,10px);width:100%}"
            + ".team-form .btn-predict{margin-top:clamp(2px,.3vw,4px)}"
            + ".result-dot{justify-content:center}"
            + ".match-row-extras{display:flex;flex-wrap:wrap;align-items:center;gap:clamp(8px,1vw,14px);padding:clamp(10px,1.2vw,14px) clamp(12px,1.5vw,20px) clamp(12px,1.4vw,18px);border-top:1px solid var(--border)}"
            + ".match-time{font-family:var(--font-mono);font-size:clamp(11px,1vw,14px);color:var(--text-dim);text-align:center;white-space:nowrap;font-weight:500;display:flex;flex-direction:column;align-items:center;line-height:1.3;gap:1px}"
            + ".match-d{font-size:clamp(9px,.8vw,11px);font-weight:600;text-transform:uppercase;letter-spacing:.02em}"
            + ".match-d-rel{color:var(--pri)}"
            + ".match-h{font-size:clamp(12px,1.1vw,14px);font-weight:600}"
            + ".group-badge{font-size:clamp(8px,.7vw,10px);font-weight:700;letter-spacing:.08em;text-transform:uppercase;color:var(--text-sec);background:var(--surface2);border:1px solid var(--border);border-radius:4px;padding:clamp(2px,.3vw,4px) clamp(4px,.5vw,8px);text-align:center;flex-shrink:0}"
            + "@media(max-width:420px){.group-badge{display:none}}"
            + ".team{display:flex;align-items:center;gap:clamp(6px,.8vw,10px);min-width:0;max-width:100%;flex-direction:row}"
            + ".team-home,.team-away{justify-content:flex-start;text-align:left}"
            + ".flag{font-size:clamp(18px,4.5vw,22px);flex-shrink:0;line-height:1;filter:drop-shadow(0 1px 2px rgba(0,0,0,.3))}"
            + ".team-name{font-size:clamp(14px,3.8vw,17px);font-weight:600;color:var(--text);word-break:break-word;overflow-wrap:break-word;hyphens:auto;letter-spacing:-.01em;line-height:1.3;white-space:normal;overflow:visible}"
            + ".team-name.muted{color:var(--text-dim);font-style:italic;font-weight:400}"

            // Score form (inline, compact)
            + ".score-form{display:flex;align-items:center;gap:clamp(4px,.5vw,8px);flex-wrap:wrap;justify-content:center}"
            + ".score-inputs{display:flex;align-items:center;gap:3px}"
            + ".score-input{width:clamp(32px,3.2vw,40px);height:clamp(34px,3.2vw,40px);text-align:center;font-size:clamp(15px,1.4vw,18px);font-weight:700;padding:2px;border-radius:var(--radius-sm);background:var(--surface2);border:1px solid var(--border);color:var(--text);font-family:var(--font-mono);-moz-appearance:textfield;appearance:textfield;transition:all .2s ease}"
            + ".score-input::-webkit-inner-spin-button,.score-input::-webkit-outer-spin-button{display:none}"
                         + ".score-input:focus{border-color:var(--pri);background:rgba(0,212,255,.05);box-shadow:0 0 0 3px var(--pri-light)}"
             + ".score-input.saved{animation:flashGreen .6s ease-out}"
            + ".score-sep{color:var(--text-dim);font-family:var(--font-mono);font-size:clamp(13px,1.2vw,16px);font-weight:600}"
            + ".btn-predict{font-size:clamp(11px,1vw,13px);padding:clamp(5px,.6vw,8px) clamp(10px,1.1vw,16px);border-radius:100px;background:var(--gradient);color:#fff;font-weight:700;border:none;cursor:pointer;white-space:nowrap;transition:all .2s ease;font-family:var(--font);min-height:clamp(32px,3.2vw,40px);box-shadow:0 2px 8px rgba(0,212,255,.2)}"
            + ".btn-predict:hover{opacity:.9;transform:translateY(-1px);box-shadow:0 4px 16px rgba(0,212,255,.3)}"
            + ".btn-predict:disabled{opacity:.4;cursor:not-allowed;transform:none!important}"

            // Result display (for finished matches)
            + ".result-badge{display:inline-flex;align-items:center;gap:clamp(4px,.5vw,6px);font-family:var(--font-mono);font-size:clamp(11px,1vw,13px);font-weight:700;white-space:nowrap}"
            + ".badge-exact{color:var(--green)}"
            + ".badge-winner{color:var(--yellow)}"
            + ".badge-wrong{color:var(--red);opacity:.7}"
            + ".badge-none{color:var(--text-dim);font-weight:400}"

            // Result dot indicators
            + ".result-dot{display:inline-flex;align-items:center;gap:clamp(4px,.5vw,6px);font-family:var(--font-mono);font-size:clamp(11px,1vw,13px);font-weight:700;white-space:nowrap}"
            + ".dot-exact{color:var(--green)}"
            + ".dot-winner{color:var(--yellow)}"
            + ".dot-wrong{color:var(--red);opacity:.7}"
            + ".dot-none{color:var(--text-dim);font-weight:400}"
             + ".dot-vs{font-size:clamp(9px,.8vw,11px);color:var(--text-dim);font-weight:500;margin-left:4px;opacity:.7;white-space:nowrap}"

            // Status badge (finalizado, en vivo, próximo)
            + ".match-status{font-size:clamp(10px,.9vw,12px);font-weight:600;white-space:nowrap;text-align:right;font-family:var(--font-mono)}"
            + ".match-status.playing{color:var(--red);animation:pulse 1.5s ease-in-out infinite}"
            + ".match-status.played{color:var(--text-dim)}"
            + ".match-status.ready{color:var(--pri)}"
            + ".match-status.locked{color:var(--text-dim)}"

            // Your prediction display
            + ".pred-display{font-size:clamp(11px,1vw,13px);color:var(--pri);font-weight:600;font-family:var(--font-mono)}"

            // Idle
            + ".idle-msg{color:var(--text-dim);font-size:clamp(11px,1vw,13px);padding:clamp(2px,.3vw,4px) 0}"

            // Star
            + ".btn-star{font-size:clamp(10px,.9vw,12px);padding:clamp(5px,.6vw,8px) clamp(10px,1.2vw,16px);border-radius:100px;background:transparent;border:1px solid var(--yellow-border);color:var(--yellow);font-weight:600;cursor:pointer;transition:all .2s ease;font-family:var(--font);min-height:clamp(32px,3vw,40px)}"
            + ".btn-star.active{background:rgba(245,158,11,.15);border-color:var(--yellow);color:var(--yellow);box-shadow:0 0 12px rgba(245,158,11,.15)}"
+ ".btn-star:hover{background:rgba(245,158,11,.1);border-color:var(--yellow)}"
             + ".star-icon{display:inline-flex;font-size:14px;margin-left:4px;vertical-align:middle;color:var(--yellow)}"

             // Predictions toggle
            + ".predictions-toggle{font-size:clamp(11px,1vw,13px)}"
            + ".predictions-toggle summary{color:var(--text-dim);cursor:pointer;font-weight:500;padding:clamp(3px,.4vw,6px) 0;transition:color .15s ease}"
            + ".predictions-toggle summary:hover{color:var(--text-sec)}"
            + ".predictions-toggle ul{margin-top:clamp(4px,.5vw,8px);padding-left:0;list-style:none;display:flex;flex-direction:column;gap:3px}"
            + ".predictions-toggle li{font-size:clamp(11px,1vw,13px);color:var(--text-dim);display:flex;align-items:center;gap:clamp(6px,.7vw,10px);padding:clamp(4px,.5vw,6px) clamp(8px,1vw,12px);border-radius:6px}"
            + ".predictions-toggle li:nth-child(odd){background:var(--surface-alt)}"
            + ".predictions-toggle .pred-score{color:var(--pri);font-weight:600}"

            // Result admin
            + ".result-admin{display:flex;align-items:center;gap:clamp(6px,.7vw,10px);flex-wrap:wrap}"
            + ".admin-label{font-size:clamp(10px,.9vw,12px);color:var(--text-dim)}"
            + ".score-input-sm{width:clamp(26px,2.5vw,32px);height:clamp(28px,2.5vw,32px);text-align:center;font-size:clamp(12px,1.1vw,14px);font-weight:600;padding:2px;background:var(--surface2);border:1px solid var(--border);border-radius:6px;color:var(--text);font-family:var(--font-mono);-moz-appearance:textfield;appearance:textfield;transition:border-color .2s ease}"
            + ".score-input-sm::-webkit-inner-spin-button,.score-input-sm::-webkit-outer-spin-button{display:none}"
            + ".score-input-sm:focus{border-color:var(--pri);box-shadow:0 0 0 3px var(--pri-light)}"
            + ".btn-admin{font-size:clamp(10px,.9vw,12px);padding:clamp(4px,.5vw,6px) clamp(8px,1vw,14px);border-radius:100px;background:var(--surface2);border:1px solid var(--border);color:var(--text-dim);font-weight:600;cursor:pointer;font-family:var(--font);min-height:clamp(32px,3vw,38px);transition:all .2s ease}"
            + ".btn-admin:hover{background:var(--surface-hover);color:var(--text);border-color:var(--text-dim)}"

            // Live indicator pulse
            + "@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}"
            + "@keyframes fadeIn{from{opacity:0;transform:translateY(8px)}to{opacity:1;transform:translateY(0)}}"
            + "@keyframes glow{0%,100%{box-shadow:0 0 8px rgba(0,212,255,.15)}50%{box-shadow:0 0 20px rgba(0,212,255,.25)}}"
             + "@keyframes flashGreen{0%{background:rgba(16,185,129,.25);border-color:var(--green)}100%{background:var(--surface2);border-color:var(--border)}}"

            // Match row color variants for prediction results
            + ".match-row.correct-exact{border-color:var(--green)!important;background:rgba(16,185,129,.06)!important}"
            + ".match-row.correct-exact::before{background:var(--green)!important}"
            + ".match-row.correct-winner{border-color:var(--yellow)!important;background:rgba(245,158,11,.06)!important}"
            + ".match-row.correct-winner::before{background:var(--yellow)!important}"
            + ".match-row.wrong{border-color:var(--red)!important;background:rgba(239,68,68,.04)!important}"
            + ".match-row.wrong::before{background:var(--red)!important}"

            // ═══════════════════════════════════════
            //  LEADERBOARD — premium ranking
            // ═══════════════════════════════════════
            + ".leaderboard{margin:0}"
            + ".leaderboard details[open] summary{margin-bottom:clamp(8px,1vw,14px)}"
            + ".leaderboard h2{font-size:clamp(15px,1.4vw,18px);display:inline}"
            + ".table-wrap{overflow-x:auto;-webkit-overflow-scrolling:touch;margin:0;width:100%}"
            + ".table-wrap table{min-width:min(400px,100%)}"
            + "table{width:100%;border-collapse:collapse;font-size:clamp(12px,1.1vw,14px)}"
            + "th{text-align:left;padding:clamp(8px,1vw,12px) clamp(8px,.9vw,12px);border-bottom:1px solid var(--border);color:var(--text-sec);font-size:clamp(10px,.9vw,11px);text-transform:uppercase;letter-spacing:.06em;font-weight:700}"
            + "td{padding:clamp(10px,1.1vw,14px) clamp(8px,.9vw,12px);border-bottom:1px solid var(--border)}"
            + "tbody tr{transition:all .15s ease}"
            + "tbody tr:hover{background:var(--surface-hover)}"
            + "td.rank{width:clamp(28px,2.5vw,36px);font-weight:700;text-align:center;font-size:clamp(12px,1.1vw,14px);color:var(--text-dim);font-family:var(--font-mono)}"
            // Podium: first 3 rows stand out with glow
            + "tbody tr:first-child td{background:rgba(16,185,129,.06);border-bottom-color:var(--green-border)}"
            + "tbody tr:nth-child(2) td{background:var(--pri-light);border-bottom-color:var(--pri-light)}"
            + "tbody tr:nth-child(3) td{background:rgba(245,158,11,.06);border-bottom-color:var(--yellow-border)}"
            + ".rank-medal{display:inline-flex;align-items:center;justify-content:center;width:clamp(28px,2.5vw,34px);height:clamp(28px,2.5vw,34px);border-radius:50%;font-size:clamp(12px,1.1vw,15px);font-weight:800}"
            + ".rank-1{background:var(--gold);color:#0B1020;box-shadow:0 0 12px rgba(245,158,11,.3)}"
            + ".rank-2{background:var(--silver);color:#0B1020}"
            + ".rank-3{background:var(--bronze);color:#fff;box-shadow:0 0 12px rgba(180,83,9,.3)}"
            + "td.pts{text-align:center;font-weight:800;color:var(--pri);font-size:clamp(18px,1.8vw,24px);font-family:var(--font-mono)}"
            + "td.stat{text-align:center;font-size:clamp(11px,1vw,13px);font-weight:600;white-space:nowrap}"
            + "td.stat-pct{text-align:center;font-size:clamp(11px,1vw,13px);font-weight:600;white-space:nowrap;color:var(--text-sec)}"

            // Sidebar overrides
            + ".col-side .card{padding:clamp(14px,2vw,24px);margin:0}"
            + ".col-side table{font-size:clamp(12px,1.1vw,14px)}"
            + ".col-side td{padding:clamp(8px,1vw,12px) clamp(4px,.5vw,8px)}"
            + ".col-side th{padding:clamp(6px,.7vw,10px) clamp(4px,.5vw,8px)}"

            // ═══════════════════════════════════════
            //  DRAWER (mobile) + FAB
            // ═══════════════════════════════════════
            + ".fab{display:none;position:fixed;bottom:clamp(20px,3vh,32px);right:clamp(16px,2.5vw,28px);width:56px;height:56px;border-radius:50%;background:var(--gradient);color:#fff;font-size:24px;border:none;cursor:pointer;z-index:200;box-shadow:0 8px 28px rgba(0,0,0,.5);align-items:center;justify-content:center;transition:transform .2s ease,box-shadow .2s ease}"
            + ".fab:hover{transform:scale(1.08);box-shadow:0 12px 36px rgba(0,212,255,.3)}"
            + ".fab:active{transform:scale(.95)}"
            + "@media(max-width:899px){.fab{display:flex}}"
            + ".drawer-overlay{display:none;position:fixed;inset:0;background:rgba(0,0,0,.55);z-index:300;backdrop-filter:blur(4px);-webkit-backdrop-filter:blur(4px);opacity:0;transition:opacity .3s ease}"
            + ".drawer-overlay.open{display:block;opacity:1}"
            + ".drawer-panel{display:block;position:fixed;top:0;right:0;bottom:0;width:min(340px,82vw);background:var(--surface);border-left:1px solid var(--border);z-index:301;transform:translateX(105%);transition:transform .35s cubic-bezier(.4,0,.2,1);box-shadow:-8px 0 32px rgba(0,0,0,.4);overflow-y:auto;-webkit-overflow-scrolling:touch}"
            + ".drawer-panel.open{transform:translateX(0)}"
            + ".drawer-header{display:flex;align-items:center;justify-content:space-between;padding:clamp(16px,2.5vw,24px) clamp(20px,3vw,28px) 0;margin-bottom:clamp(12px,1.5vw,20px)}"
            + ".drawer-header h2{font-size:clamp(17px,1.6vw,20px);font-weight:700;color:var(--text)}"
            + ".drawer-close{width:36px;height:36px;border-radius:50%;background:var(--surface2);border:1px solid var(--border);color:var(--text-sec);font-size:20px;cursor:pointer;display:flex;align-items:center;justify-content:center;transition:all .15s ease;line-height:1;padding:0}"
            + ".drawer-close:hover{background:var(--surface-hover);color:var(--text);border-color:var(--text-dim)}"
            + ".drawer-body{padding:0 clamp(20px,3vw,28px) clamp(24px,3vw,40px)}"
            + ".drawer-body .card{border-color:var(--border-light);margin:clamp(10px,1.2vw,16px) 0}"
            + ".drawer-body .leaderboard{margin:clamp(10px,1.2vw,16px) 0}"
            + ".drawer-body table{font-size:clamp(12px,1.1vw,14px)}"
            + ".drawer-body td{padding:clamp(8px,1vw,12px) clamp(4px,.5vw,8px)}"
            + ".drawer-body th{padding:clamp(6px,.7vw,10px) clamp(4px,.5vw,8px)}"
            + "@media(min-width:900px){.drawer-overlay,.drawer-panel,.fab{display:none!important}}"

            // Champion
            + ".champion-form{display:flex;gap:clamp(8px,.8vw,12px);margin-top:clamp(12px,1.5vw,20px)}"
            + ".champion-form select{flex:1;border-radius:100px}"

            // ═══════════════════════════════════════
            //  SETTINGS
            // ═══════════════════════════════════════
            + ".settings-layout{max-width:min(560px,100%);margin:0 auto}"

            // ═══════════════════════════════════════
            //  KNOCKOUT BRACKET — premium dark
            // ═══════════════════════════════════════
            + ".ko-round{margin-bottom:clamp(24px,3vw,48px)}"
            + ".ko-round-title{font-size:clamp(18px,1.8vw,26px);font-weight:800;margin-bottom:clamp(12px,1.5vw,20px);padding:0;color:var(--text);letter-spacing:-.03em;background:var(--gradient);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}"
            + ".ko-card{position:relative;border:1px solid var(--border);border-radius:var(--radius-lg);overflow:hidden;background:var(--surface);box-shadow:var(--shadow-sm);transition:all .25s ease}"
            + ".ko-card::before{content:'';position:absolute;top:0;left:0;width:3px;height:100%;background:var(--gradient);border-radius:3px 0 0 3px;opacity:.6}"
            + ".ko-card:hover{border-color:var(--border-light);box-shadow:var(--shadow-md)}"
            + ".ko-card .teams-row{padding:clamp(6px,.7vw,10px) 0}"
            + ".ko-placeholder .home,.ko-placeholder .away{color:var(--text-dim)!important;font-style:italic;font-weight:400!important}"
            + ".ko-pending{text-align:center;padding:clamp(48px,6vw,80px) clamp(20px,3vw,40px);background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-xl);margin-bottom:clamp(20px,3vw,40px);position:relative;overflow:hidden}"
            + ".ko-pending::before{content:'';position:absolute;top:0;left:0;right:0;height:3px;background:var(--gradient);opacity:.3}"
            + ".ko-pending-icon{font-size:clamp(40px,5vw,64px);display:block;margin-bottom:clamp(16px,2vw,24px)}"
            + ".ko-pending h2{font-size:clamp(22px,2.8vw,32px);font-weight:800;margin-bottom:clamp(8px,1vw,14px);letter-spacing:-.03em;background:var(--gradient);-webkit-background-clip:text;-webkit-text-fill-color:transparent;background-clip:text}"
            + ".ko-pending p{color:var(--text-sec);font-size:clamp(15px,1.4vw,18px);max-width:min(500px,100%);margin:0 auto;line-height:1.7}"

            // ═══════════════════════════════════════
            //  SCROLLBAR — dark premium
            // ═══════════════════════════════════════
            + "::-webkit-scrollbar{width:8px;height:8px}"
            + "::-webkit-scrollbar-track{background:transparent}"
            + "::-webkit-scrollbar-thumb{background:var(--border);border-radius:4px;border:2px solid var(--bg)}"
            + "::-webkit-scrollbar-thumb:hover{background:var(--border-light)}"
            + "::-webkit-scrollbar-corner{background:transparent}"

            // Selection
            + "::selection{background:var(--pri-light);color:var(--text)}"
            + "::-moz-selection{background:var(--pri-light);color:var(--text)}"

            // ═══════════════════════════════════════
            //  LARGE SCREENS — expand layouts for bigger viewports
            // ═══════════════════════════════════════
            // Tablet landscape / small laptop
            + "@media(min-width:1024px){"
            + "main{max-width:min(1120px,100% - 3rem);padding:clamp(24px,3vw,40px) clamp(24px,3vw,40px) clamp(40px,5vw,64px)}"
            + ".header-inner{height:clamp(52px,6vh,64px)}"
            + ".auth-page{margin:clamp(48px,10vh,100px) auto}"
            + ".my-groups-grid{grid-template-columns:1fr 1fr}}"
            // Desktop
            + "@media(min-width:1280px){"
            + "main{max-width:min(1200px,100% - 4rem)}"
            + ".home-hero{padding:clamp(48px,8vh,96px) clamp(24px,4vw,48px) clamp(36px,5vw,64px)}"
            + ".group-layout{gap:clamp(24px,3vw,40px)}"
            + ".col-side{width:320px}}"
            // Large desktop
            + "@media(min-width:1536px){"
            + "main{max-width:min(1320px,100% - 6rem)}"
            + ".home-hero h1{font-size:clamp(40px,4vw,56px)}"
            + ".group-header .title-area h1{font-size:clamp(30px,3vw,42px)}}"
            // Ultra-wide / 4K
            + "@media(min-width:1920px){"
            + "main{max-width:min(1440px,100% - 8rem)}"
            + ".home-hero h1{font-size:clamp(44px,3.5vw,60px)}"
            + ".group-header .title-area h1{font-size:clamp(34px,2.5vw,48px)}"
            + "html{font-size:clamp(16px,.9vw,18px)}}"
            // 4K+
            + "@media(min-width:2560px){"
            + "main{max-width:min(1600px,80%)}"
            + "html{font-size:clamp(16px,.7vw,20px)}"
            + ".home-hero h1{font-size:clamp(48px,2.8vw,72px)}}"


            // ═══════════════════════════════════════
            //  VIEW TOGGLE + ACCORDION + FILTER
            // ═══════════════════════════════════════
            + ".view-toggle{display:flex;gap:6px;margin-bottom:clamp(16px,2.5vw,28px)}"
            + ".view-btn{padding:clamp(8px,1vw,12px) clamp(14px,1.6vw,22px);border-radius:100px;background:var(--surface);border:1px solid var(--border);color:var(--text-sec);font-size:clamp(12px,1.1vw,14px);font-weight:600;transition:all .2s ease;text-decoration:none;white-space:nowrap;min-height:44px;display:inline-flex;align-items:center}"
            + ".view-btn:hover{background:var(--surface-hover);border-color:var(--text-dim);color:var(--text);text-decoration:none}"
            + ".view-btn.active{background:var(--gradient);border:none;color:#fff}"

            + ".jor-accordion{display:flex;flex-direction:column;gap:clamp(8px,1vw,12px)}"
            + ".jor-section{background:var(--surface);border:1px solid var(--border);border-radius:var(--radius-lg);overflow:hidden;transition:border-color .2s ease;box-shadow:var(--shadow-sm)}"
            + ".jor-section[open]{border-color:var(--border-light)}"
            + ".jor-header{padding:clamp(12px,1.3vw,18px) clamp(16px,2vw,24px);cursor:pointer;display:flex;justify-content:space-between;align-items:center;gap:clamp(8px,1vw,14px);flex-wrap:wrap;user-select:none;list-style:none;transition:background .15s ease;-webkit-tap-highlight-color:transparent}"
            + ".jor-header::-webkit-details-marker,.jor-header::marker{display:none;content:''}"
            + ".jor-header:hover{background:var(--surface-hover)}"
            + ".jor-header:active{background:var(--surface2)}"
            + ".jor-current{background:var(--pri-light)}"
            + ".jor-current:hover{background:rgba(0,212,255,.18)}"
            + ".jor-title{display:flex;align-items:center;gap:clamp(6px,.6vw,10px);min-width:0}"
            + ".jor-num{font-size:clamp(15px,1.4vw,19px);font-weight:700;color:var(--text);letter-spacing:-.02em}"
            + ".jor-now{font-size:clamp(9px,.8vw,11px);font-weight:700;color:var(--red);padding:2px 8px;border-radius:4px;border:1px solid rgba(239,68,68,.3);background:rgba(239,68,68,.1);animation:pulse 1.5s ease-in-out infinite;white-space:nowrap}"
            + ".jor-meta{font-size:clamp(10px,.9vw,12px);color:var(--text-sec);white-space:nowrap}"
            + ".jor-pending{color:var(--pri);font-weight:600}"
            + ".jor-matches{padding:clamp(4px,.4vw,8px) clamp(8px,1vw,14px) clamp(8px,1vw,14px)}"
            + ".jor-matches .match-row{margin:0 0 clamp(6px,.7vw,10px)}"
+ ".jor-matches .match-row:last-child{margin-bottom:0}"
             + ".filter-bar{display:flex;align-items:center;gap:clamp(10px,1.2vw,18px);margin-bottom:clamp(10px,1.2vw,16px);flex-wrap:wrap}"
            + ".filter-pending{display:inline-flex;align-items:center;gap:clamp(6px,.6vw,10px);cursor:pointer;font-size:clamp(12px,1.1vw,14px);color:var(--text-sec);font-weight:500;padding:clamp(6px,.6vw,10px) clamp(12px,1.4vw,18px);border-radius:100px;background:var(--surface);border:1px solid var(--border);transition:all .2s ease;min-height:44px;user-select:none;-webkit-tap-highlight-color:transparent}"
            + ".filter-pending:hover{border-color:var(--border-light);color:var(--text)}"
            + ".filter-pending input[type=checkbox]{width:18px;height:18px;accent-color:var(--pri);cursor:pointer;margin:0;min-height:auto;flex-shrink:0}"
            + ".filter-info{font-size:clamp(11px,1vw,13px);color:var(--text-dim);font-weight:500}"

            + ".match-wrapper{transition:opacity .2s ease}"

            // EN VIVO badge (green pulsing dot + text)
            + ".match-status.playing{display:inline-flex;align-items:center;gap:5px;color:#22c55e;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:.04em;animation:none}"
            + ".live-dot{width:8px;height:8px;border-radius:50%;background:#22c55e;display:inline-block;animation:livePulse 1.2s ease-in-out infinite;vertical-align:middle}"
            + "@keyframes livePulse{0%,100%{opacity:1;box-shadow:0 0 0 0 rgba(34,197,94,.6)}50%{opacity:.7;box-shadow:0 0 0 5px rgba(34,197,94,0)}}"

            // Back-to-top button
            + ".btn-top{position:fixed;bottom:clamp(20px,3vh,32px);left:clamp(16px,2.5vw,28px);width:48px;height:48px;border-radius:50%;background:var(--surface);border:1px solid var(--border);color:var(--text-sec);font-size:22px;cursor:pointer;z-index:99;box-shadow:0 4px 16px rgba(0,0,0,.3);opacity:0;pointer-events:none;transform:translateY(12px);transition:opacity .25s ease,transform .25s ease;display:flex;align-items:center;justify-content:center}"
            + ".btn-top.visible{opacity:1;pointer-events:auto;transform:translateY(0)}"
            + ".btn-top:hover{background:var(--surface-hover);color:var(--text);border-color:var(--text-dim);transform:translateY(-3px);box-shadow:0 6px 20px rgba(0,0,0,.4)}"
            + ".btn-top:active{transform:scale(.95)}"

            + "</style>"
            + "</head><body>"
            // ── Global header ──
            + "<header class='site-header'>" + hdr + "</header>"
            // ── Main content ──
            + "<main>" + body + "</main>"
            // ── Footer ──
            + "<footer class='site-footer'>Quiniela Mundial 2026</footer>"
            // ── Back-to-top button ──
            + "<button id='btn-top' class='btn-top' onclick='this.style.opacity=\"0\";this.style.pointerEvents=\"none\";this.classList.remove(\"visible\");window.scrollTo({top:0,behavior:\"smooth\"})' aria-label='Volver arriba'>⬆</button>"
            // ── Scripts ──
            + "<script>"
            + "function showToast(type,msg){var t=document.createElement('div');t.className='toast '+type;t.textContent=msg;var m=document.querySelector('main');if(!m)return;m.insertBefore(t,m.firstChild);setTimeout(function(){t.style.opacity='0';t.style.transform='translateY(-8px)';setTimeout(function(){t.remove()},300)},3500)}"
            + "function confirmAction(msg){return confirm(msg)}"
            + "function toggleDrawer(){var p=document.getElementById('drawer-panel'),o=document.getElementById('drawer-overlay');if(!p||!o)return;var open=p.classList.toggle('open');o.classList.toggle('open',open);document.body.style.overflow=open?'hidden':''}"
            + "function closeDrawer(){var p=document.getElementById('drawer-panel'),o=document.getElementById('drawer-overlay');if(p)p.classList.remove('open');if(o)o.classList.remove('open');document.body.style.overflow=''}"
            + "function togglePendingFilter(){var cb=document.getElementById('filterPending');if(!cb)return;var on=cb.checked;var t=0;document.querySelectorAll('.match-wrapper').forEach(function(e){var a=e.getAttribute('data-active')==='true';e.style.display=on&&!a?'none':'';if(a)t++});var i=document.getElementById('filterInfo');if(i)i.textContent=on?'Mostrando '+t+' sin finalizar':''}"
            + "function liveScoresFromSSE(data){try{var d=JSON.parse(data);d.forEach(function(m){var c=document.querySelector('[data-match-id=\"'+m.id+'\"]');if(!c)return;var sh=c.querySelector('.score-home');var sa=c.querySelector('.score-away');if(sh)sh.textContent=m.homeGoals;if(sa)sa.textContent=m.awayGoals;if(m.finished){var w=c.closest('.match-wrapper');if(w)w.setAttribute('data-active','false');var st=c.querySelector('.match-status.playing');if(st){st.classList.remove('playing');st.textContent='\u2705'}var pl=c.querySelector('.playing');if(pl)pl.classList.remove('playing');var cb=document.getElementById('filterPending');if(cb&&cb.checked)setTimeout(function(){togglePendingFilter()},10)}})}catch(e){}};(function(){var gc=window.location.pathname.split('/')[2];if(!gc)return;var s=new EventSource('/groups/'+gc+'/api/scores/sse');s.onmessage=function(e){liveScoresFromSSE(e.data)}})();"
            + "document.addEventListener('submit',async function(e){var f=e.target;if(!f.closest('.group-layout,.group-header'))return;if(f.action.includes('/admin/')||f.action.includes('/logout'))return;e.preventDefault();var msg=f.getAttribute('data-confirm');if(msg&&!confirm(msg))return;var btn=f.querySelector('button[type=submit]');var orig=btn?btn.textContent:'';if(btn){btn.disabled=true;btn.textContent='Guardando\u2026'}var i=f.querySelector('input[name=matchId]');var d=i?i.value:null;try{var r=await fetch(f.action,{method:'POST',body:new URLSearchParams(new FormData(f)),headers:{'X-Requested-With':'XMLHttpRequest'}});var ct=r.headers.get('Content-Type')||'';if(ct.includes('application/json')){var j=await r.json();if(j.success){                    if(j.html){var tmp=document.createElement('div');tmp.innerHTML=j.html;var newMain=tmp.querySelector('main');var m=document.querySelector('main');if(m&&newMain){var _fc=document.getElementById('filterPending');var _fch=_fc?_fc.checked:false;m.innerHTML=newMain.innerHTML;if(_fch){var _nc=document.getElementById('filterPending');if(_nc){_nc.checked=true;togglePendingFilter()}}if(d){var c=document.querySelector('[data-match-id=\"'+d+'\"]');if(c){c.querySelectorAll('.score-input').forEach(function(x){x.classList.add('saved');setTimeout(function(){x.classList.remove('saved')},700)})}}}}showToast('success',j.message)}else{showToast('error',j.message);if(btn){btn.disabled=false;btn.textContent=orig}}}else{window.location.reload()}}catch(err){showToast('error','Error de conexi\u00f3n');if(btn){btn.disabled=false;btn.textContent=orig}}})"
             + ";document.addEventListener('keydown',function(e){if(e.key==='Escape')closeDrawer()})"
             + ";(function(){var e=document.querySelector('.jor-section[open]');if(e)setTimeout(function(){e.scrollIntoView({behavior:'smooth',block:'start'})},100)})()"
+ ";var lastY=window.scrollY;window.addEventListener('scroll',function(){var b=document.getElementById('btn-top');if(!b)return;var dy=window.scrollY-lastY;lastY=window.scrollY;if(window.scrollY>300&&dy>0){b.style.opacity='';b.style.pointerEvents='';b.classList.add('visible')}else{b.classList.remove('visible')}})"
             + ";function toggleTheme(){var d=document.documentElement;var l=d.getAttribute('data-theme')==='light';d.setAttribute('data-theme',l?'':'light');localStorage.setItem('theme',l?'':'light');var b=document.getElementById('theme-btn');if(b)b.textContent=l?'☀️':'🌙'}"
             + ";(function(){var t=localStorage.getItem('theme');if(t==='light'){document.documentElement.setAttribute('data-theme','light');var b=document.getElementById('theme-btn');if(b)b.textContent='🌙'}})()"
             + "</script>"
            + "</body></html>";
    }

    /** Build the global site header based on login state. */
    private String buildHeader(String loggedInUser) {
        if (loggedInUser != null) {
            return "<a href='/' class='header-logo'><span class='logo-icon'>⚽</span> World Cup 26</a>"
                + "<nav class='header-nav'>"
                + "<span class='user-name'>" + escape(loggedInUser) + "</span>"
                + "<button id='theme-btn' class='header-btn' onclick='toggleTheme()' style='font-size:16px;padding:6px 10px;min-height:36px' aria-label='Cambiar tema'>☀️</button>"
                + "<a href='/settings' class='header-btn'>Ajustes</a>"
                + "<a href='/logout' class='header-btn'>Salir</a>"
                + "</nav>";
        }
        return "<a href='/' class='header-logo'><span class='logo-icon'>⚽</span> World Cup 26</a>"
            + "<nav class='header-nav'>"
            + "<button id='theme-btn' class='header-btn' onclick='toggleTheme()' style='font-size:16px;padding:6px 10px;min-height:36px' aria-label='Cambiar tema'>☀️</button>"
            + "<a href='/login' class='header-btn'>Entrar</a>"
            + "<a href='/register' class='header-btn primary'>Registrarse</a>"
            + "</nav>";


    }
}
