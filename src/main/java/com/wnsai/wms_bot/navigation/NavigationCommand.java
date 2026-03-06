package com.wnsai.wms_bot.navigation;

import java.util.Map;

public record NavigationCommand(
    String route,
    String label,
    Map<String, String> params
) {
    public NavigationCommand(String route, String label) {
        this(route, label, Map.of());
    }
}
