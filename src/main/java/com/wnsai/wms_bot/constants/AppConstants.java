package com.wnsai.wms_bot.constants;

public final class AppConstants {

    private AppConstants() {}

    // ── Roles ─────────────────────────────────────────────────────────────────
    public static final String ROLE_MANAGER    = "MANAGER";
    public static final String ROLE_OPERATOR   = "OPERATOR";
    public static final String ROLE_GATE_STAFF = "GATE_STAFF";
    public static final String ROLE_VIEWER     = "VIEWER";

    // ── Inward / Outward statuses ─────────────────────────────────────────────
    public static final String STATUS_PENDING  = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    // ── Gate Pass statuses ────────────────────────────────────────────────────
    public static final String GATE_STATUS_OPEN      = "OPEN";
    public static final String GATE_STATUS_CLOSED    = "CLOSED";
    public static final String GATE_STATUS_CANCELLED = "CANCELLED";

    // ── Bond statuses ─────────────────────────────────────────────────────────
    public static final String BOND_STATUS_ACTIVE  = "ACTIVE";
    public static final String BOND_STATUS_EXPIRED = "EXPIRED";
    public static final String BOND_STATUS_CLOSED  = "CLOSED";

    // ── Report types ──────────────────────────────────────────────────────────
    public static final String REPORT_STOCK_SUMMARY    = "STOCK_SUMMARY";
    public static final String REPORT_INWARD_SUMMARY   = "INWARD_SUMMARY";
    public static final String REPORT_OUTWARD_SUMMARY  = "OUTWARD_SUMMARY";
    public static final String REPORT_GATE_PASS_LOG    = "GATE_PASS_LOG";
    public static final String REPORT_BOND_STATUS      = "BOND_STATUS";
    public static final String REPORT_DAILY_ACTIVITY   = "DAILY_ACTIVITY";

    // ── Report formats ────────────────────────────────────────────────────────
    public static final String FORMAT_CSV = "CSV";
    public static final String FORMAT_PDF = "PDF";

    // ── Report statuses ───────────────────────────────────────────────────────
    public static final String REPORT_GENERATING = "GENERATING";
    public static final String REPORT_READY      = "READY";
    public static final String REPORT_FAILED     = "FAILED";

    // ── JWT claim keys ────────────────────────────────────────────────────────
    public static final String CLAIM_ROLE         = "role";
    public static final String CLAIM_WAREHOUSE_ID = "warehouseId";
    public static final String CLAIM_EMAIL        = "email";

    // ── Chat response types ───────────────────────────────────────────────────
    public static final String RESPONSE_TEXT    = "TEXT";
    public static final String RESPONSE_TABLE   = "TABLE";
    public static final String RESPONSE_CHART   = "CHART";
    public static final String RESPONSE_LIST    = "LIST";
    public static final String RESPONSE_ACTION  = "ACTION";
    public static final String RESPONSE_ALERT   = "ALERT";
    public static final String RESPONSE_REPORT  = "REPORT";

    // ── Overstay threshold (hours) ────────────────────────────────────────────
    public static final int GATE_OVERSTAY_HOURS = 4;

    // ── Expiring bonds look-ahead (days) ─────────────────────────────────────
    public static final int BOND_EXPIRY_LOOKAHEAD_DAYS = 30;

    // ── Pagination defaults ───────────────────────────────────────────────────
    public static final int DEFAULT_PAGE_SIZE = 20;
}
