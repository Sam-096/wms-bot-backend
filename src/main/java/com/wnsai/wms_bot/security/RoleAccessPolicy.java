package com.wnsai.wms_bot.security;

import com.wnsai.wms_bot.chat.ChatResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Deterministic role → route gate. Used by ChatOrchestratorImpl BEFORE any LLM call
 * so ACCESS_DENIED events are produced by the server, not fabricated by the model.
 *
 * The policy is intentionally coarse: a route prefix match is enough to decide.
 * If a role is unknown, we fall open (ADMIN treatment) to avoid locking users out
 * due to config drift — real enforcement still happens at REST controllers via
 * {@code @PreAuthorize}.
 */
@Component
public class RoleAccessPolicy {

    private static final Set<String> ALL_ROUTES = Set.of(
        "/dashboard", "/reports", "/inward", "/outward", "/gate-pass",
        "/gate-operations", "/inventory", "/bonds", "/settings",
        "/users", "/finance", "/qc"
    );

    // Allowed route prefixes per role.
    private static final Map<String, Set<String>> ALLOWED = Map.of(
        "ADMIN",      ALL_ROUTES,
        "MANAGER",    Set.of("/dashboard", "/reports", "/inward", "/outward",
                             "/gate-pass", "/gate-operations", "/inventory", "/bonds"),
        "OPERATOR",   Set.of("/dashboard", "/inward", "/outward",
                             "/gate-pass", "/gate-operations"),
        "VIEWER",     Set.of("/dashboard", "/reports"),
        "GATE_STAFF", Set.of("/dashboard", "/gate-pass", "/gate-operations")
    );

    // Friendly fallback buttons per role for the ACCESS_DENIED card.
    private static final Map<String, List<Map<String, String>>> FALLBACK_ACTIONS = Map.of(
        "MANAGER",    List.of(btn("Dashboard", "/dashboard"), btn("Reports", "/reports")),
        "OPERATOR",   List.of(btn("New Inward", "/inward/new"),
                              btn("New Outward", "/outward/new"),
                              btn("Gate Pass", "/gate-operations")),
        "VIEWER",     List.of(btn("View Reports", "/reports"), btn("Dashboard", "/dashboard")),
        "GATE_STAFF", List.of(btn("Gate Pass", "/gate-operations"),
                              btn("Dashboard", "/dashboard"))
    );

    /**
     * Returns ACCESS_DENIED response if the role cannot reach {@code route}.
     * Empty Optional means access is allowed — proceed normally.
     */
    public Optional<ChatResponse> check(String role, String route) {
        if (route == null || route.isBlank()) return Optional.empty();
        String normRole = (role == null || role.isBlank()) ? "ADMIN" : role.toUpperCase();

        Set<String> allowed = ALLOWED.get(normRole);
        if (allowed == null) return Optional.empty();  // unknown role → fall open

        boolean ok = allowed.stream().anyMatch(route::startsWith);
        if (ok) return Optional.empty();

        String msg = buildDenyMessage(normRole, route);
        List<Map<String, String>> actions = FALLBACK_ACTIONS.getOrDefault(
            normRole, List.of(btn("Dashboard", "/dashboard")));
        return Optional.of(ChatResponse.accessDenied(msg, actions));
    }

    private static Map<String, String> btn(String label, String route) {
        return Map.of("label", label, "route", route);
    }

    private static String buildDenyMessage(String role, String route) {
        return switch (role) {
            case "OPERATOR"   -> "You don't have access to " + route +
                                 ". You can work on Inward, Outward, or Gate Pass.";
            case "VIEWER"     -> "Your account is read-only. Try a report or the dashboard instead.";
            case "GATE_STAFF" -> "You can only access gate operations.";
            case "MANAGER"    -> "Managers can't open " + route +
                                 ". Ask an admin for that area.";
            default           -> "You don't have access to " + route + ".";
        };
    }
}
