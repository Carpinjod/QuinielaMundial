package quinielamundial;

import org.junit.jupiter.api.Test;
import quinielamundial.service.QuinielaService;
import quinielamundial.web.HtmlRenderer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Page structure tests.
 * Verifies that all generated HTML pages have correct structure,
 * proper components, and expected content across all states.
 */
class QuinielaPageStructureTest {

    private final QuinielaService service = new QuinielaService();
    private final HtmlRenderer renderer = new HtmlRenderer();

    // ── HTML5 document structure ──

    @Test
    void htmlPageHasRequiredStructure() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("HTML5 document structure",
            () -> assertTrue(html.startsWith("<!doctype html>"),
                "Must start with HTML5 doctype"),
            () -> assertTrue(html.contains("<html lang='es'>"),
                "Must have lang='es' on html element"),
            () -> assertTrue(html.contains("<head>"), "Must have <head>"),
            () -> assertTrue(html.contains("<meta charset='utf-8'>"),
                "Must have charset utf-8"),
            () -> assertTrue(html.contains("<meta name='viewport' content='width=device-width, initial-scale=1'>"),
                "Must have viewport meta tag for mobile"),
            () -> assertTrue(html.contains("<title>"), "Must have <title>"),
            () -> assertTrue(html.contains("</head>"), "Must close </head>"),
            () -> assertTrue(html.contains("<body>"), "Must have <body>"),
            () -> assertTrue(html.contains("</body></html>"), "Must close body and html")
        );
    }

    @Test
    void googleFontsLinkIsPresent() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("Google Fonts",
            () -> assertTrue(html.contains("fonts.googleapis.com"),
                "Google Fonts preconnect"),
            () -> assertTrue(html.contains("Inter:wght@"),
                "Inter font must be loaded"),
            () -> assertTrue(html.contains("JetBrains+Mono"),
                "JetBrains Mono font must be loaded")
        );
    }

    // ── Home page structure ──

    @Test
    void homePageHasFooter() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertTrue(html.contains("<footer class='site-footer'>"),
            "Home page must have footer");
    }

    @Test
    void homePageHeaderVariesByLoginState() {
        var loggedOut = renderer.homePage(List.of(), null, List.of(), null);
        var loggedIn = renderer.homePage(List.of(), "Ana", List.of(), null);

        assertAll("Header nav varies",
            () -> assertTrue(loggedOut.contains(">Entrar</a>"),
                "Logged out: show Entrar link"),
            () -> assertTrue(loggedOut.contains(">Registrarse</a>"),
                "Logged out: show Registrarse link"),
            () -> assertTrue(loggedIn.contains(">Salir</a>"),
                "Logged in: show Salir link"),
            () -> assertTrue(loggedIn.contains(">Ajustes</a>"),
                "Logged in: show Ajustes link"),
            () -> assertTrue(loggedIn.contains("Ana"),
                "Logged in: show user name"),
            () -> assertFalse(loggedIn.contains(">Entrar</a>"),
                "Logged in: no Entrar link")
        );
    }

    @Test
    void homePageShowsThemeToggleButton() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertTrue(html.contains("id='theme-btn'"), "Theme toggle button must exist");
        assertTrue(html.contains("toggleTheme()"), "Theme toggle must call toggleTheme()");
    }

    @Test
    void homePageHasHeroSection() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("Hero section",
            () -> assertTrue(html.contains("class='home-hero'"), "Hero div"),
            () -> assertTrue(html.contains("Tu quiniela del Mundial 2026"),
                "Hero title text"),
            () -> assertTrue(html.contains("<h1>"), "Hero h1")
        );
    }

    @Test
    void homePageHasCreateAndJoinCards() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("Create/Join cards",
            () -> assertTrue(html.contains("➕ Crear grupo"), "Create card title"),
            () -> assertTrue(html.contains("🔑 Unirse con código"), "Join card title"),
            () -> assertTrue(html.contains("action='/groups/create'"), "Create form action"),
            () -> assertTrue(html.contains("action='/groups/join'"), "Join form action")
        );
    }

    @Test
    void homePageShowsMyGroupsWhenLoggedIn() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertFalse(html.contains("Mis grupos"),
            "Anonymous users should not see 'Mis grupos'");

        var group = service.createGroup("Test", "Ana");
        var loggedIn = renderer.homePage(List.of(group), "Ana", List.of(group), null);
        assertTrue(loggedIn.contains("Mis grupos"),
            "Logged-in user with groups should see 'Mis grupos'");
    }

    @Test
    void homePageShowsAllGroupsList() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("All groups list",
            () -> assertTrue(html.contains("Todos los grupos"),
                "Groups section title"),
            () -> assertTrue(html.contains("class='all-groups-list'"),
                "Groups list ul")
        );
    }

    @Test
    void homePageHasRulesSection() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("Rules section",
            () -> assertTrue(html.contains("📖 Reglas"), "Rules summary"),
            () -> assertTrue(html.contains("3 puntos"), "3pt rule"),
            () -> assertTrue(html.contains("1 punto"), "1pt rule"),
            () -> assertTrue(html.contains("Partido Estrella"), "Star match rule"),
            () -> assertTrue(html.contains("10 puntos extra"), "Champion rule")
        );
    }

    @Test
    void homePageShowsErrorToast() {
        var html = renderer.homePage(List.of(), null, List.of(), "Error de prueba", null);
        assertAll("Error toast on home page",
            () -> assertTrue(html.contains("toast error"), "Error toast class"),
            () -> assertTrue(html.contains("Error de prueba"), "Error message text")
        );
    }

    @Test
    void homePageShowsSuccessToast() {
        var html = renderer.homePage(List.of(), null, List.of(), null, "Grupo creado");
        assertAll("Success toast on home page",
            () -> assertTrue(html.contains("toast success"), "Success toast class"),
            () -> assertTrue(html.contains("Grupo creado"), "Success message text")
        );
    }

    // ── Group page structure ──

    @Test
    void groupPageHasHeaderAndBackLink() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        assertAll("Group page header",
            () -> assertTrue(html.contains("class='back-link'"), "Back link"),
            () -> assertTrue(html.contains("class='group-header'"), "Group header"),
            () -> assertTrue(html.contains(escapeHtml(group.name())), "Group name in page"),
            () -> assertTrue(html.contains(group.code()), "Group code in page")
        );
    }

    @Test
    void groupPageHasViewToggle() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        assertAll("View toggle",
            () -> assertTrue(html.contains("class='view-toggle'"), "View toggle container"),
            () -> assertTrue(html.contains("Fase de grupos"), "Groups tab"),
            () -> assertTrue(html.contains("Eliminatorias"), "Knockout tab")
        );
    }

    @Test
    void groupPageHasAccordionForJornadas() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        assertAll("Jornada accordion",
            () -> assertTrue(html.contains("class='jor-accordion'"), "Accordion container"),
            () -> assertTrue(html.contains("class='jor-section'"), "Jornada section"),
            () -> assertTrue(html.contains("class='jor-header'"), "Jornada header"),
            () -> assertTrue(html.contains("Jornada "), "Jornada title"),
            () -> assertTrue(html.contains("class='jor-matches'"), "Jornada matches")
        );
    }

    @Test
    void groupPageHasFilterBar() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        assertAll("Filter bar",
            () -> assertTrue(html.contains("class='filter-bar'"), "Filter bar container"),
            () -> assertTrue(html.contains("id='filterPending'"), "Pending filter checkbox"),
            () -> assertTrue(html.contains("Solo pendientes"), "Pending filter label")
        );
    }

    @Test
    void groupPageShowsUserBadge() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();

        var spectatorHtml = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        var memberHtml = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);

        assertAll("User badge",
            () -> assertTrue(spectatorHtml.contains("user-not-joined"),
                "Spectator sees 'not joined' badge"),
            () -> assertTrue(spectatorHtml.contains("para unirte"),
                "Spectator sees join instructions"),
            () -> assertTrue(memberHtml.contains("user-badge"),
                "Member sees user badge"),
            () -> assertTrue(memberHtml.contains("Ana"),
                "Member sees their name")
        );
    }

    @Test
    void groupPageHasLeaderboard() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        assertAll("Leaderboard section",
            () -> assertTrue(html.contains("Clasificación"),
                "Leaderboard title"),
            () -> assertTrue(html.contains("Pts"), "Points column header"),
            () -> assertTrue(html.contains("🎯"), "Exact hits column header")
        );
    }

    @Test
    void groupPageHasBracketView() {
        var group = service.createGroup("Test", "Ana");
        // Select knockouts via jornada=0
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 0, null);
        // Bracket should show either the "Esperando" message or actual rounds
        assertAll("Bracket view",
            () -> assertTrue(
                html.contains("ko-pending") || html.contains("ko-round"),
                "Must show either ko-pending (locked) or ko-round (unlocked)")
        );
    }

    @Test
    void groupPageHasDrawerAndFabOnMobile() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        assertAll("Drawer and FAB",
            () -> assertTrue(html.contains("id='drawer-overlay'"), "Drawer overlay"),
            () -> assertTrue(html.contains("id='drawer-panel'"), "Drawer panel"),
            () -> assertTrue(html.contains("id='fab-stats'"), "FAB button")
        );
    }

    @Test
    void groupPageHasJornadaTabs() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);
        // Jornada tabs should be present
        assertTrue(html.contains("class='tabs'") || html.contains("class='jor-section'"),
            "Group page must have jornada navigation (tabs or accordion)");
    }

    @Test
    void groupPageShowsChampionSectionForMember() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();
        var html = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);
        assertAll("Champion section for member",
            () -> assertTrue(html.contains("Tu apuesta al campeón"),
                "Champion bet form for member"),
            () -> assertTrue(html.contains("class='champion-form'"),
                "Champion form element")
        );
    }

    @Test
    void groupPageAdminSectionsVisibleForCreator() {
        var group = service.createGroup("Test", "Admin");
        TestUtils.setFutureKickoffs(group);
        group.join("Luis");
        var admin = group.creator();

        var html = renderer.groupPage(group, admin, service.candidates(), null, false, 1, null);
        assertAll("Admin sections",
            () -> assertTrue(html.contains("Administrar miembros"),
                "Admin sees member management"),
            () -> assertTrue(html.contains("admin/reset-password"),
                "Admin reset password form"),
            () -> assertTrue(html.contains("admin/remove-member"),
                "Admin remove member form")
        );
    }

    @Test
    void groupPageAdminDoesNotShowLeaveSection() {
        var group = service.createGroup("Test", "Admin");
        var admin = group.creator();
        var html = renderer.groupPage(group, admin, service.candidates(), null, false, 1, null);
        assertFalse(html.contains("Salir del grupo"),
            "Creator should not see 'Salir del grupo'");
    }

    @Test
    void groupPageNonCreatorCanSeeLeaveSection() {
        var group = service.createGroup("Test", "Admin");
        TestUtils.setFutureKickoffs(group);
        var luis = group.join("Luis");
        var html = renderer.groupPage(group, luis, service.candidates(), null, false, 1, null);
        assertTrue(html.contains("Salir del grupo"),
            "Non-creator member should see 'Salir del grupo'");
    }

    // ── Match card structure ──

    @Test
    void matchCardHasRequiredStructure() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);

        assertAll("Match card structure",
            () -> assertTrue(html.contains("class='match-row'"), "Match row article"),
            () -> assertTrue(html.contains("class='match-row-main'"), "Match row main"),
            () -> assertTrue(html.contains("class='match-row-top'"), "Match row top (time + group)"),
            () -> assertTrue(html.contains("class='match-row-body'"), "Match row body (teams + score)"),
            () -> assertTrue(html.contains("class='match-teams'"), "Teams container"),
            () -> assertTrue(html.contains("class='team team-home'"), "Home team"),
            () -> assertTrue(html.contains("class='team team-away'"), "Away team"),
            () -> assertTrue(html.contains("class='flag'"), "Team flag"),
            () -> assertTrue(html.contains("class='team-name'"), "Team name"),
            () -> assertTrue(html.contains("class='match-time'"), "Match time")
        );
    }

    @Test
    void matchCardInFormModeHasInputsAndSubmit() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var ana = group.creator();
        var html = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);

        assertAll("Match card form mode",
            () -> assertTrue(html.contains("class='score-form team-form'"),
                "Form mode uses team-form class"),
            () -> assertTrue(html.contains("name='homeGoals'"), "Home goals input"),
            () -> assertTrue(html.contains("name='awayGoals'"), "Away goals input"),
            () -> assertTrue(html.contains("class='score-input team-input'"),
                "Score input class"),
            () -> assertTrue(html.contains("class='btn-predict'"),
                "Predict button"),
            () -> assertTrue(html.contains("action='/groups/" + group.code() + "/prediction'"),
                "Prediction form action")
        );
    }

    @Test
    void matchCardFinishedShowsScore() {
        var group = service.createGroup("Test", "Ana");
        var match = group.matches().get(0);
        group.registerResult(match.id(), 3, 1);
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);

        assertAll("Finished match displays score",
            () -> assertTrue(html.contains("class='match-score'"), "Score display"),
            () -> assertTrue(html.contains("class='score-home'"), "Home score"),
            () -> assertTrue(html.contains("class='score-sep'"), "Score separator"),
            () -> assertTrue(html.contains("class='score-away'"), "Away score"),
            () -> assertTrue(html.contains("class='match-status'"), "Status badge")
        );
    }

    @Test
    void matchCardHasResultBadgeOnFinished() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var ana = group.creator();
        var match = group.matches().get(0);
        group.submitPrediction(ana.token(), match.id(), 2, 1);
        group.registerResult(match.id(), 2, 1); // exact hit
        var html = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);

        assertAll("Result badge on finished match",
            () -> assertTrue(html.contains("result-dot"), "Result dot indicator class"),
            () -> assertTrue(html.contains("🎯"), "Exact hit icon"),
            () -> assertTrue(html.contains("+3") || html.contains("+6"),
                "Points in result badge (3 or 6 for star)")
        );
    }

    @Test
    void matchCardHasPredictionsToggle() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("class='predictions-toggle'"),
            "Predictions toggle must exist");
        assertTrue(html.contains("Pronósticos"),
            "Predictions toggle title");
    }

    @Test
    void matchCardShowsStarFormWhenApplicable() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var ana = group.creator();
        var html = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);

        // HTML has "class='btn-star ' (with trailing space when not active)
        assertTrue(html.contains("⭐ Marcar") || html.contains("⭐ Quitar"),
            "Star button must be present for member with future matches");
    }

    @Test
    void matchCardShowsVsBadge() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var html = renderer.groupPage(group, null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("class='vs-badge'"),
            "VS badge must be present between teams");
    }

    // ── Auth pages ──

    @Test
    void loginPageHasRequiredStructure() {
        var html = renderer.loginPage(null);
        assertAll("Login page",
            () -> assertTrue(html.contains("class='auth-page'"), "Auth page container"),
            () -> assertTrue(html.contains("class='auth-card'"), "Auth card"),
            () -> assertTrue(html.contains("action='/login'"), "Login form action"),
            () -> assertTrue(html.contains("name='username'"), "Username input"),
            () -> assertTrue(html.contains("name='password'"), "Password input"),
            () -> assertTrue(html.contains("type='password'"), "Password type"),
            () -> assertTrue(html.contains("Iniciar sesión"), "Page title")
        );
    }

    @Test
    void loginPageShowsError() {
        var html = renderer.loginPage("Usuario o contraseña incorrectos");
        assertTrue(html.contains("toast error"), "Error toast");
        assertTrue(html.contains("Usuario o contraseña incorrectos"), "Error message");
    }

    @Test
    void registerPageHasRequiredStructure() {
        var html = renderer.registerPage(null);
        assertAll("Register page",
            () -> assertTrue(html.contains("action='/register'"), "Register form action"),
            () -> assertTrue(html.contains("name='confirm'"), "Confirm password input"),
            () -> assertTrue(html.contains("Crear cuenta"), "Register title"),
            () -> assertTrue(html.contains("Inicia sesión"), "Register page shows login link")
        );
    }

    @Test
    void settingsPageHasRequiredStructure() {
        var group = service.createGroup("Test", "Ana");
        var html = renderer.settingsPage("Ana", List.of(group), null, null, null);
        assertAll("Settings page",
            () -> assertTrue(html.contains("Ajustes"), "Settings title"),
            () -> assertTrue(html.contains("Cambiar contraseña"), "Password change section"),
            () -> assertTrue(html.contains("action='/settings/password'"), "Password form action"),
            () -> assertTrue(html.contains("Notificaciones por email"), "Email notification section"),
            () -> assertTrue(html.contains("action='/settings/email'"), "Email form action"),
            () -> assertTrue(html.contains("Mis estadísticas"), "Stats section"),
            () -> assertTrue(html.contains("Cerrar sesión"), "Logout button")
        );
    }

    @Test
    void errorPageShowsMessageAndBackLink() {
        var html = renderer.errorPage("Error", "Algo salió mal");
        assertAll("Error page",
            () -> assertTrue(html.contains("toast error"), "Error toast"),
            () -> assertTrue(html.contains("Algo salió mal"), "Error message"),
            () -> assertTrue(html.contains("Volver al inicio"), "Back link")
        );
    }

    // ── Prediction visibility rules ──

    @Test
    void predictionsAreHiddenBeforeMatchStarts() {
        var group = service.createGroup("Test", "Ana");
        TestUtils.setFutureKickoffs(group);
        var ana = group.creator();
        var luis = group.join("Luis");
        var match = group.matches().get(0);
        group.submitPrediction(ana.token(), match.id(), 2, 1);
        group.submitPrediction(luis.token(), match.id(), 0, 3);

        // Ana viewing: should see her own prediction but not Luis's
        var html = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);
        assertAll("Prediction visibility before match",
            () -> assertTrue(html.contains("🔒 oculto"),
                "Other members' predictions should be hidden 🔒"),
            () -> assertFalse(html.contains("Ana'> 2–1") || html.contains("Ana&#39;> 2–1"),
                "Prediction values should NOT be visible in clear for future matches")
        );
    }

    // ── Back-to-top button ──

    @Test
    void backToTopButtonExists() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("Back-to-top button",
            () -> assertTrue(html.contains("id='btn-top'"), "Button with btn-top id"),
            () -> assertTrue(html.contains("Volver arriba"), "Accessible label"),
            () -> assertTrue(html.contains("window.scrollTo({top:0"), "Scroll-to-top behavior")
        );
    }

    // ── Helper ──

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
