package com.docintellect.api.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_SCHEMA = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getTenantId() {
        return CURRENT_TENANT.get();
    }

    public static void setSchemaName(String schema) {
        CURRENT_SCHEMA.set(schema);
    }

    public static String getSchemaName() {
        return CURRENT_SCHEMA.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_SCHEMA.remove();
    }
}
