package quinielamundial;

import org.junit.jupiter.api.Test;
import quinielamundial.service.QuinielaService;
import quinielamundial.web.HtmlRenderer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the HtmlRenderer output changes:
 * - AJAX form submissions (data-confirm, async handler, X-Requested-With)
 * - Visual feedback (showToast, toast transitions)
 * - Admin forms still use confirmAction (backward compat)
 */
class QuinielaRenderingTest {

    private final QuinielaService service = new QuinielaService();
    private final HtmlRenderer renderer = new HtmlRenderer();

    // ── Change 1: AJAX form submissions ──

    @Test
    void predictionFormHasDataConfirmOnUpdate() {
        var group = service.createGroup("Test", "Ana");
        var ana = group.creator();
        var match = group.matches().get(0);
        TestUtils.setFutureKickoff(match);
        group.submitPrediction(ana.token(), match.id(), 2, 1);

        var html = renderer.groupPage(group, ana, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("data-confirm=\"¿Actualizar tu pronóstico"),
            "Prediction update form should use data-confirm instead of onsubmit");
    }

    @Test
    void jsIncludesXRequestedWith() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("X-Requested-With"),
            "AJAX handler must send X-Requested-With header for server detection");
    }

    @Test
    void jsIncludesPreventDefault() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("e.preventDefault"),
            "AJAX handler must prevent default form submission");
    }

    @Test
    void jsIncludesAsyncHandler() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("async function"),
            "Form submit handler should be async");
    }

    @Test
    void jsIncludesGuardandoFeedback() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("Guardando"),
            "Button should show 'Guardando…' during AJAX submission");
    }

    @Test
    void ajaxHandlerChecksContentTypeBeforeJsonParse() {
        var html = renderer.groupPage(
            service.createGroup("Test", "Ana"),
            null, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("Content-Type"),
            "AJAX handler should check Content-Type before parsing JSON");
        assertTrue(html.contains("application/json"),
            "AJAX handler should check for application/json");
        assertTrue(html.contains("window.location.reload"),
            "Non-JSON response should fallback to page reload");
    }

    // ── Change 2: Visual feedback (toasts) ──

    @Test
    void jsIncludesShowToastFunction() {
        var html = renderer.homePage(List.of(), null, List.of(), null);

        assertTrue(html.contains("function showToast"),
            "JS must include showToast function for visual feedback");
    }

    @Test
    void toastCssHasTransition() {
        var html = renderer.homePage(List.of(), null, List.of(), null);

        assertTrue(html.contains("transition:opacity"),
            "Toast CSS must have opacity transition for smooth fade-out animation");
    }

    @Test
    void showToastCreatesSuccessAndErrorVariants() {
        var html = renderer.homePage(List.of(), null, List.of(), null);

        assertTrue(html.contains("toast '+type"),
            "showToast must support dynamic type (success/error)");
    }

    @Test
    void showToastAutoRemovesAfterDelay() {
        var html = renderer.homePage(List.of(), null, List.of(), null);

        assertTrue(html.contains("setTimeout"),
            "showToast must auto-remove after timeout");
    }

    // ── Backward compat: Admin forms ──

    @Test
    void adminRemoveMemberStillUsesConfirmAction() {
        var group = service.createGroup("Test", "Admin");
        group.join("Luis");
        var admin = group.creator();

        var html = renderer.groupPage(group, admin, service.candidates(), null, false, 1, null);

        assertTrue(html.contains("confirmAction"),
            "Admin remove-member must still use confirmAction for backward compat");
    }

    @Test
    void confirmActionFunctionStillExists() {
        var html = renderer.homePage(List.of(), null, List.of(), null);

        assertTrue(html.contains("function confirmAction"),
            "confirmAction function must still exist for admin forms");
    }
}
