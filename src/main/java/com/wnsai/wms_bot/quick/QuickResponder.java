package com.wnsai.wms_bot.quick;

import reactor.core.publisher.Mono;

public interface QuickResponder {

    /** Instant greeting in the user's language. Target: < 1ms */
    String greet(String language);

    /** Top 5 low-stock items for the warehouse. Target: < 300ms */
    Mono<String> quickStock(String warehouseId);

    /** Count + list of pending inward receipts. Target: < 300ms */
    Mono<String> quickPending(String warehouseId);

    /** Active gate passes. Target: < 300ms */
    Mono<String> quickGatePasses(String warehouseId);

    /** Bonds expiring in the next 7 days. Target: < 300ms */
    Mono<String> quickBonds(String warehouseId);

    /** Route to the right handler based on extracted entity. */
    Mono<String> handleQuickQuery(String entity, String warehouseId);
}
