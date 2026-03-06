package com.wnsai.wms_bot.navigation;

import java.util.Optional;

public interface NavigationResolver {

    /**
     * Map a natural-language message to an Angular route.
     * Returns empty if the message is not a navigation command.
     */
    Optional<NavigationCommand> resolve(String message);
}
