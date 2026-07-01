package quinielamundial;

import org.junit.jupiter.api.Test;
import quinielamundial.service.QuinielaService;
import quinielamundial.web.HtmlRenderer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CSS framework coverage tests.
 * Verifies that all design tokens, component CSS classes, keyframes,
 * and responsive media queries are present in the generated HTML.
 *
 * These tests ensure CSS is not accidentally broken when modifying HtmlRenderer.
 */
class QuinielaCSSFrameworkTest {

    private final QuinielaService service = new QuinielaService();
    private final HtmlRenderer renderer = new HtmlRenderer();

    /** Extracts the CSS block from any full HTML page. */
    private String cssBlock() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        var start = html.indexOf("<style>");
        var end = html.indexOf("</style>");
        assertTrue(start >= 0, "<style> tag must exist");
        assertTrue(end > start, "</style> tag must exist after <style>");
        return html.substring(start + 7, end);
    }

    // ── Design tokens (CSS custom properties) ──

    @Test
    void allDesignTokensAreDefined() {
        var css = cssBlock();
        // Core background/surface tokens
        assertAll("Design tokens",
            () -> assertTrue(css.contains("--bg:"), "Missing --bg"),
            () -> assertTrue(css.contains("--surface:"), "Missing --surface"),
            () -> assertTrue(css.contains("--surface-hover:"), "Missing --surface-hover"),
            () -> assertTrue(css.contains("--surface-alt:"), "Missing --surface-alt"),
            () -> assertTrue(css.contains("--border:"), "Missing --border"),
            () -> assertTrue(css.contains("--border-light:"), "Missing --border-light"),
            // Text tokens
            () -> assertTrue(css.contains("--text:"), "Missing --text"),
            () -> assertTrue(css.contains("--text-sec:"), "Missing --text-sec"),
            () -> assertTrue(css.contains("--text-dim:"), "Missing --text-dim"),
            // Brand tokens
            () -> assertTrue(css.contains("--pri:"), "Missing --pri"),
            () -> assertTrue(css.contains("--pri-hover:"), "Missing --pri-hover"),
            () -> assertTrue(css.contains("--pri-light:"), "Missing --pri-light"),
            () -> assertTrue(css.contains("--accent:"), "Missing --accent"),
            // Status colors
            () -> assertTrue(css.contains("--green:"), "Missing --green"),
            () -> assertTrue(css.contains("--red:"), "Missing --red"),
            () -> assertTrue(css.contains("--yellow:"), "Missing --yellow"),
            // Medal colors
            () -> assertTrue(css.contains("--gold:"), "Missing --gold"),
            () -> assertTrue(css.contains("--silver:"), "Missing --silver"),
            () -> assertTrue(css.contains("--bronze:"), "Missing --bronze"),
            // Radii
            () -> assertTrue(css.contains("--radius-sm:"), "Missing --radius-sm"),
            () -> assertTrue(css.contains("--radius-md:"), "Missing --radius-md"),
            () -> assertTrue(css.contains("--radius-lg:"), "Missing --radius-lg"),
            () -> assertTrue(css.contains("--radius-xl:"), "Missing --radius-xl"),
            // Effects
            () -> assertTrue(css.contains("--gradient:"), "Missing --gradient"),
            () -> assertTrue(css.contains("--shadow-sm:"), "Missing --shadow-sm"),
            () -> assertTrue(css.contains("--shadow-md:"), "Missing --shadow-md"),
            () -> assertTrue(css.contains("--shadow-lg:"), "Missing --shadow-lg"),
            // Fonts
            () -> assertTrue(css.contains("--font:"), "Missing --font"),
            () -> assertTrue(css.contains("--font-mono:"), "Missing --font-mono")
        );
    }

    @Test
    void headerBgTokenExists() {
        var css = cssBlock();
        assertTrue(css.contains("--header-bg:"), "Missing --header-bg for sticky header");
    }

    @Test
    void gradientGlowTokenExists() {
        var css = cssBlock();
        assertTrue(css.contains("--gradient-glow:"), "Missing --gradient-glow");
    }

    // ── Light theme overrides ──

    @Test
    void lightThemeOverridesExist() {
        var css = cssBlock();
        var lightStart = css.indexOf("[data-theme=light]");
        assertTrue(lightStart >= 0, "Light theme [data-theme=light] override must exist");

        var lightBlock = css.substring(lightStart);
        assertAll("Light theme tokens",
            () -> assertTrue(lightBlock.contains("--bg:"), "Light --bg override"),
            () -> assertTrue(lightBlock.contains("--surface:"), "Light --surface override"),
            () -> assertTrue(lightBlock.contains("--text:"), "Light --text override"),
            () -> assertTrue(lightBlock.contains("--pri:"), "Light --pri override"),
            () -> assertTrue(lightBlock.contains("--header-bg:"), "Light --header-bg override")
        );
    }

    // ── Component CSS classes ──

    @Test
    void baseResetAndHtmlTagsPresent() {
        var css = cssBlock();
        assertAll("Base reset",
            () -> assertTrue(css.contains("*,*::before,*::after{box-sizing:"), "Universal box-sizing reset"),
            () -> assertTrue(css.contains("html{scroll-behavior:"), "Html scroll-behavior"),
            () -> assertTrue(css.contains("body{font-family:"), "Body font-family")
        );
    }

    @Test
    void formElementStylesPresent() {
        var css = cssBlock();
        assertAll("Form styles",
            () -> assertTrue(css.contains("input,select,textarea{padding:"), "input/select/textarea styles"),
            () -> assertTrue(css.contains("input:focus,select:focus{border-color:"), "Focus styles"),
            () -> assertTrue(css.contains("button{padding:"), "Button styles")
        );
    }

    @Test
    void cardComponentStylesPresent() {
        var css = cssBlock();
        assertAll("Card component",
            () -> assertTrue(css.contains(".card{background:"), ".card base style"),
            () -> assertTrue(css.contains(".card:hover{border-color:"), ".card hover style"),
            () -> assertTrue(css.contains(".card h2{" ), ".card h2 style")
        );
    }

    @Test
    void toastComponentStylesPresent() {
        var css = cssBlock();
        assertAll("Toast component",
            () -> assertTrue(css.contains(".toast{padding:"), ".toast base style"),
            () -> assertTrue(css.contains(".toast.error{background:"), ".toast.error style"),
            () -> assertTrue(css.contains(".toast.success{background:"), ".toast.success style")
        );
    }

    @Test
    void headerComponentStylesPresent() {
        var css = cssBlock();
        assertAll("Header component",
            () -> assertTrue(css.contains(".site-header{"), ".site-header"),
            () -> assertTrue(css.contains(".header-inner{"), ".header-inner"),
            () -> assertTrue(css.contains(".header-logo{"), ".header-logo"),
            () -> assertTrue(css.contains(".header-nav{"), ".header-nav"),
            () -> assertTrue(css.contains(".header-btn{"), ".header-btn")
        );
    }

    @Test
    void matchRowStylesPresent() {
        var css = cssBlock();
        assertAll("Match row styles",
            () -> assertTrue(css.contains(".match-row{background:"), ".match-row"),
            () -> assertTrue(css.contains(".match-row::before{"), ".match-row::before gradient accent"),
            () -> assertTrue(css.contains(".match-row:hover{"), ".match-row hover"),
            () -> assertTrue(css.contains(".match-row-main{"), ".match-row-main"),
            () -> assertTrue(css.contains(".match-row-top{"), ".match-row-top"),
            () -> assertTrue(css.contains(".match-row-body{"), ".match-row-body"),
            () -> assertTrue(css.contains(".match-teams{"), ".match-teams"),
            () -> assertTrue(css.contains(".match-time{"), ".match-time"),
            () -> assertTrue(css.contains(".match-actions{"), ".match-actions"),
            () -> assertTrue(css.contains(".team{display:flex;align-items:center;"), ".team base"),
            () -> assertTrue(css.contains(".team-name{"), ".team-name"),
            () -> assertTrue(css.contains(".flag{"), ".flag emoji")
        );
    }

    @Test
    void matchScoreStylesPresent() {
        var css = cssBlock();
        assertAll("Score/result styles",
            () -> assertTrue(css.contains(".match-score,.pred-display{"), ".match-score / .pred-display"),
            () -> assertTrue(css.contains(".team-score{display:"), ".team-score for mobile"),
            () -> assertTrue(css.contains(".score-sep{"), ".score-sep exists in CSS (separator dash)"),
            () -> assertTrue(css.contains(".result-dot{"), ".result-dot"),
            () -> assertTrue(css.contains(".dot-exact{"), ".dot-exact (green)"),
            () -> assertTrue(css.contains(".dot-winner{"), ".dot-winner (yellow)"),
            () -> assertTrue(css.contains(".dot-wrong{"), ".dot-wrong (red)"),
            () -> assertTrue(css.contains(".dot-none{"), ".dot-none")
        );
    }

    @Test
    void formInputScoreStylesPresent() {
        var css = cssBlock();
        assertAll("Score input styles",
            () -> assertTrue(css.contains(".score-input{width:"), ".score-input"),
            () -> assertTrue(css.contains(".score-input.saved{animation:"), ".score-input.saved flash feedback"),
            () -> assertTrue(css.contains(".score-inputs{display:"), ".score-inputs"),
            () -> assertTrue(css.contains(".btn-predict{"), ".btn-predict button")
        );
    }

    @Test
    void advancingPickerStylesRemoved() {
        var css = cssBlock();
        assertAll("Advancing picker styles must be gone",
            () -> assertFalse(css.contains(".advancing-picker{"), ".advancing-picker must be removed"),
            () -> assertFalse(css.contains(".adv-label{"), ".adv-label must be removed"),
            () -> assertFalse(css.contains(".adv-options{"), ".adv-options must be removed"),
            () -> assertFalse(css.contains(".adv-option{"), ".adv-option must be removed")
        );
    }

    @Test
    void predictionToggleStylesPresent() {
        var css = cssBlock();
        assertAll("Predictions toggle",
            () -> assertTrue(css.contains(".predictions-toggle{"), ".predictions-toggle"),
            () -> assertTrue(css.contains(".predictions-toggle summary{"), ".predictions-toggle summary"),
            () -> assertTrue(css.contains(".predictions-toggle ul{"), ".predictions-toggle ul"),
            () -> assertTrue(css.contains(".predictions-toggle li{"), ".predictions-toggle li")
        );
    }

    @Test
    void matchStatusStylesPresent() {
        var css = cssBlock();
        assertAll("Match status variations",
            () -> assertTrue(css.contains(".match-status{font-size:"), ".match-status base"),
            () -> assertTrue(css.contains(".match-status.playing{color:"), ".match-status.playing (EN VIVO)"),
            () -> assertTrue(css.contains(".match-status.locked{color:"), ".match-status.locked")
        );
    }

    @Test
    void starButtonAndIconStylesPresent() {
        var css = cssBlock();
        assertAll("Star match styles",
            () -> assertTrue(css.contains(".btn-star{"), ".btn-star"),
            () -> assertTrue(css.contains(".btn-star.active{"), ".btn-star.active"),
            () -> assertTrue(css.contains(".star-icon{"), ".star-icon (desktop)")
        );
    }

    @Test
    void leaderboardStylesPresent() {
        var css = cssBlock();
        assertAll("Leaderboard styles",
            () -> assertTrue(css.contains(".leaderboard{"), ".leaderboard"),
            () -> assertTrue(css.contains(".table-wrap{"), ".table-wrap"),
            () -> assertTrue(css.contains("table{width:100%;border-collapse:"), "table base"),
            () -> assertTrue(css.contains("th{text-align:"), "th styling"),
            () -> assertTrue(css.contains("td{padding:"), "td styling"),
            () -> assertTrue(css.contains("td.pts{"), "td.pts points column"),
            () -> assertTrue(css.contains("td.rank{"), "td.rank"),
            () -> assertTrue(css.contains(".rank-medal{"), ".rank-medal"),
            () -> assertTrue(css.contains(".rank-1{"), ".rank-1 (gold)"),
            () -> assertTrue(css.contains(".rank-2{"), ".rank-2 (silver)"),
            () -> assertTrue(css.contains(".rank-3{"), ".rank-3 (bronze)")
        );
    }

    @Test
    void drawerAndFabStylesPresent() {
        var css = cssBlock();
        assertAll("Drawer and FAB",
            () -> assertTrue(css.contains(".fab{"), ".fab floating action button"),
            () -> assertTrue(css.contains(".drawer-overlay{"), ".drawer-overlay"),
            () -> assertTrue(css.contains(".drawer-panel{"), ".drawer-panel"),
            () -> assertTrue(css.contains(".drawer-header{"), ".drawer-header"),
            () -> assertTrue(css.contains(".drawer-close{"), ".drawer-close"),
            () -> assertTrue(css.contains(".drawer-body{"), ".drawer-body")
        );
    }

    @Test
    void knockoutStylesPresent() {
        var css = cssBlock();
        assertAll("Knockout styles",
            () -> assertTrue(css.contains(".ko-round{"), ".ko-round"),
            () -> assertTrue(css.contains(".ko-round-title{"), ".ko-round-title"),
            () -> assertTrue(css.contains(".ko-card{"), ".ko-card"),
            () -> assertTrue(css.contains(".ko-pending{"), ".ko-pending (locked message)"),
            () -> assertTrue(css.contains(".ko-pending h2{"), ".ko-pending h2")
        );
    }

    @Test
    void viewToggleAndAccordionStylesPresent() {
        var css = cssBlock();
        assertAll("View toggle and accordion",
            () -> assertTrue(css.contains(".view-toggle{"), ".view-toggle"),
            () -> assertTrue(css.contains(".view-btn{"), ".view-btn"),
            () -> assertTrue(css.contains(".view-btn.active{"), ".view-btn.active"),
            () -> assertTrue(css.contains(".jor-accordion{"), ".jor-accordion"),
            () -> assertTrue(css.contains(".jor-section{"), ".jor-section"),
            () -> assertTrue(css.contains(".jor-header{"), ".jor-header"),
            () -> assertTrue(css.contains(".jor-current{"), ".jor-current"),
            () -> assertTrue(css.contains(".jor-matches{"), ".jor-matches"),
            () -> assertTrue(css.contains(".filter-bar{"), ".filter-bar"),
            () -> assertTrue(css.contains(".filter-pending{"), ".filter-pending")
        );
    }

    @Test
    void homePageStylesPresent() {
        var css = cssBlock();
        assertAll("Home page styles",
            () -> assertTrue(css.contains(".home-hero{"), ".home-hero"),
            () -> assertTrue(css.contains(".home-hero h1{"), ".home-hero h1"),
            () -> assertTrue(css.contains(".home-cards{"), ".home-cards grid"),
            () -> assertTrue(css.contains(".my-groups-grid{"), ".my-groups-grid"),
            () -> assertTrue(css.contains(".my-group-card{"), ".my-group-card"),
            () -> assertTrue(css.contains(".all-groups-list{"), ".all-groups-list"),
            () -> assertTrue(css.contains(".rules-list{"), ".rules-list"),
            () -> assertTrue(css.contains(".section-title{"), ".section-title")
        );
    }

    @Test
    void authPageStylesPresent() {
        var css = cssBlock();
        assertAll("Auth page styles",
            () -> assertTrue(css.contains(".auth-page{"), ".auth-page"),
            () -> assertTrue(css.contains(".auth-card{"), ".auth-card"),
            () -> assertTrue(css.contains(".auth-card h2{"), ".auth-card h2"),
            () -> assertTrue(css.contains(".auth-form{"), ".auth-form")
        );
    }

    @Test
    void groupLayoutStylesPresent() {
        var css = cssBlock();
        assertAll("Group layout",
            () -> assertTrue(css.contains(".group-layout{"), ".group-layout grid"),
            () -> assertTrue(css.contains(".col-main{"), ".col-main"),
            () -> assertTrue(css.contains(".col-side{"), ".col-side"),
            () -> assertTrue(css.contains(".group-header{"), ".group-header"),
            () -> assertTrue(css.contains(".back-link{"), ".back-link")
        );
    }

    @Test
    void settingsLayoutStylesPresent() {
        var css = cssBlock();
        assertTrue(css.contains(".settings-layout{"), ".settings-layout");
    }

    @Test
    void championFormStylesPresent() {
        var css = cssBlock();
        assertTrue(css.contains(".champion-form{"), ".champion-form");
    }

    @Test
    void scrollbarStylesPresent() {
        var css = cssBlock();
        assertAll("Custom scrollbar",
            () -> assertTrue(css.contains("::-webkit-scrollbar{width:"), "webkit-scrollbar"),
            () -> assertTrue(css.contains("::-webkit-scrollbar-thumb{background:"), "webkit-scrollbar-thumb"),
            () -> assertTrue(css.contains("::-webkit-scrollbar-track{background:"), "webkit-scrollbar-track")
        );
    }

    @Test
    void selectionStylesPresent() {
        var css = cssBlock();
        assertAll("Selection styles",
            () -> assertTrue(css.contains("::selection{background:"), "::selection"),
            () -> assertTrue(css.contains("::-moz-selection{background:"), "::-moz-selection")
        );
    }

    @Test
    void resultBadgeStylesPresent() {
        var css = cssBlock();
        assertAll("Result badge classes",
            () -> assertTrue(css.contains(".badge-exact{"), ".badge-exact"),
            () -> assertTrue(css.contains(".badge-winner{"), ".badge-winner"),
            () -> assertTrue(css.contains(".badge-wrong{"), ".badge-wrong"),
            () -> assertTrue(css.contains(".badge-none{"), ".badge-none")
        );
    }

    // ── Match row color variants ──

    @Test
    void matchRowColorVariantsForPredictionResults() {
        var css = cssBlock();
        assertAll("Match row result color variants",
            () -> assertTrue(css.contains(".match-row.correct-exact{"), ".match-row.correct-exact"),
            () -> assertTrue(css.contains(".match-row.correct-winner{"), ".match-row.correct-winner"),
            () -> assertTrue(css.contains(".match-row.wrong{"), ".match-row.wrong")
        );
    }

    // ── Keyframes / animations ──

    @Test
    void allAnimationsDefined() {
        var css = cssBlock();
        assertAll("Keyframe animations",
            () -> assertTrue(css.contains("@keyframes pulse{"), "pulse animation (EN VIVO)"),
            () -> assertTrue(css.contains("@keyframes fadeIn{"), "fadeIn animation"),
            () -> assertTrue(css.contains("@keyframes glow{"), "glow animation"),
            () -> assertTrue(css.contains("@keyframes flashGreen{"), "flashGreen (saved feedback)"),
            () -> assertTrue(css.contains("@keyframes livePulse{"), "livePulse (live dot)")
        );
    }

    // ── Media queries ──

    @Test
    void responsiveMediaQueriesPresent() {
        var css = cssBlock();
        assertAll("Responsive media queries",
            // Home cards grid
            () -> assertTrue(css.contains("@media(min-width:560px){.home-cards{grid-template-columns:"),
                "560px breakpoint for home cards"),
            // My groups grid
            () -> assertTrue(css.contains("@media(min-width:560px){.my-groups-grid{grid-template-columns:"),
                "560px breakpoint for my groups"),
            // Group layout
            () -> assertTrue(css.contains("@media(min-width:900px){.group-layout{grid-template-columns:"),
                "900px breakpoint for group layout (sidebar)"),
            // Drawer hide on desktop
            () -> assertTrue(css.contains("@media(min-width:900px){.drawer-overlay,.drawer-panel,.fab{display:"),
                "900px hides drawer/FAB on desktop"),
            // Sidebar sticky
            () -> assertTrue(css.contains("@media(min-width:900px){.col-side{position:sticky;"),
                "900px sidebar sticky"),
            // Sidebar hidden on mobile
            () -> assertTrue(css.contains("@media(max-width:899px){.col-side{display:none}}"),
                "899px sidebar hidden on mobile"),
            // FAB visible on mobile
            () -> assertTrue(css.contains("@media(max-width:899px){.fab{display:flex}}"),
                "899px FAB visible on mobile"),
            // Group badge hidden on very small
            () -> assertTrue(css.contains("@media(max-width:420px){.group-badge{display:none}}"),
                "420px group badge hidden"),
            // Large screen expansions
            () -> assertTrue(css.contains("@media(min-width:1024px){"), "1024px breakpoint"),
            () -> assertTrue(css.contains("@media(min-width:1280px){"), "1280px breakpoint"),
            () -> assertTrue(css.contains("@media(min-width:1536px){"), "1536px breakpoint"),
            () -> assertTrue(css.contains("@media(min-width:1920px){"), "1920px breakpoint"),
            () -> assertTrue(css.contains("@media(min-width:2560px){"), "2560px breakpoint")
        );
    }

    // ── JS functions ──

    @Test
    void allRequiredJsFunctionsAreDefined() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("JavaScript functions",
            () -> assertTrue(html.contains("function showToast"), "showToast()"),
            () -> assertTrue(html.contains("function confirmAction"), "confirmAction()"),
            () -> assertTrue(html.contains("function toggleDrawer"), "toggleDrawer()"),
            () -> assertTrue(html.contains("function closeDrawer"), "closeDrawer()"),
            () -> assertTrue(html.contains("function togglePendingFilter"), "togglePendingFilter()"),
            () -> assertTrue(html.contains("function liveScoresFromSSE"), "liveScoresFromSSE()"),
            () -> assertTrue(html.contains("function toggleTheme"), "toggleTheme()")
        );
    }

    @Test
    void localStorageThemePersistenceExists() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertAll("localStorage theme persistence",
            () -> assertTrue(html.contains("localStorage.setItem('theme'"), "setItem in toggleTheme"),
            () -> assertTrue(html.contains("localStorage.getItem('theme')"), "getItem on page load")
        );
    }

    @Test
    void liveScoresSSEExists() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertTrue(html.contains("EventSource('/groups/'"),
            "live scores must use EventSource (SSE) instead of polling");
    }

    @Test
    void escapeKeyClosesDrawer() {
        var html = renderer.homePage(List.of(), null, List.of(), null);
        assertTrue(html.contains("e.key==='Escape'") && html.contains("closeDrawer()"),
            "Escape key must close drawer");
    }

    @Test
    void ajaxHandlerStructureIsCorrect() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        assertAll("AJAX handler",
            () -> assertTrue(html.contains("async function"), "Must use async"),
            () -> assertTrue(html.contains("e.preventDefault"), "Must prevent default"),
            () -> assertTrue(html.contains("X-Requested-With"), "Must send X-Requested-With header"),
            () -> assertTrue(html.contains("Guardando"), "Must show Guardando feedback"),
            () -> assertTrue(html.contains("showToast('success'"), "Must call showToast on success"),
            () -> assertTrue(html.contains("showToast('error'"), "Must call showToast on error"),
            () -> assertTrue(html.contains("Content-Type") && html.contains("application/json"),
                "Must check Content-Type for JSON"),
            () -> assertTrue(html.contains("window.location.reload"),
                "Non-JSON response must reload")
        );
    }

    @Test
    void ajaxHandlerExcludesAdminForms() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        // The handler should skip admin and logout forms
        assertTrue(html.contains("f.action.includes('/admin/')"),
            "AJAX handler must skip admin URLs");
        assertTrue(html.contains("f.action.includes('/logout')"),
            "AJAX handler must skip logout");
    }

    @Test
    void notificationBadgeDotVsClassExists() {
        var css = cssBlock();
        assertTrue(css.contains(".dot-vs{"), ".dot-vs group comparison badge must exist");
    }

    @Test
    void enVivoLiveDotStylesExist() {
        var css = cssBlock();
        assertAll("EN VIVO live dot",
            () -> assertTrue(css.contains(".live-dot{"), ".live-dot pulsing indicator"),
            () -> assertTrue(css.contains(".match-status.playing{display:inline-flex;"),
                "EN VIVO playing status")
        );
    }

    @Test
    void backToTopButtonStylesExist() {
        var css = cssBlock();
        assertAll("Back-to-top button",
            () -> assertTrue(css.contains(".btn-top{position:fixed;"), ".btn-top"),
            () -> assertTrue(css.contains(".btn-top.visible{"), ".btn-top.visible")
        );
    }

    @Test
    void groupBadgeClassExists() {
        var css = cssBlock();
        assertTrue(css.contains(".group-badge{"), ".group-badge for group letter / round label");
    }

    @Test
    void mutedClassExists() {
        var css = cssBlock();
        assertTrue(css.contains(".muted{color:var(--text-sec)"), ".muted helper class");
    }

    @Test
    void teamFormModeClassExists() {
        var css = cssBlock();
        // The .team-form class for form wrapping (different from .team.team-form-mode)
        assertTrue(css.contains(".team-form{display:flex;flex-direction:column;"), ".team-form for form mode");
    }

    @Test
    void adminButtonClassExists() {
        var css = cssBlock();
        assertAll("Admin UI classes",
            () -> assertTrue(css.contains(".btn-admin{"), ".btn-admin"),
            () -> assertTrue(css.contains(".result-admin{"), ".result-admin"),
            () -> assertTrue(css.contains(".score-input-sm{"), ".score-input-sm"),
            () -> assertTrue(css.contains(".admin-label{"), ".admin-label")
        );
    }

    @Test
    void siteFooterStylesExist() {
        var css = cssBlock();
        assertTrue(css.contains(".site-footer{"), ".site-footer styles");
    }

    @Test
    void tabStylesExist() {
        var css = cssBlock();
        assertAll("Tab/jornada navigation styles",
            () -> assertTrue(css.contains(".tabs{display:"), ".tabs container"),
            () -> assertTrue(css.contains(".tab{padding:"), ".tab pill"),
            () -> assertTrue(css.contains(".tab.active{background:"), ".tab.active"),
            () -> assertTrue(css.contains(".tab-current{"), ".tab-current (with live dot)")
        );
    }
}
