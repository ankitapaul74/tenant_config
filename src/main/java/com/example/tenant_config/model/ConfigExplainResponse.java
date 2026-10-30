
package com.example.tenant_config.model;

public class ConfigExplainResponse {

    private String configKey;
    private Object value;
    private String sourceLevel;
    private String tenantId;
    private String officeId;
    private String shiftId;

    public ConfigExplainResponse(
            String configKey,
            Object value,
            String sourceLevel,
            String tenantId,
            String officeId,
            String shiftId) {

        this.configKey = configKey;
        this.value = value;
        this.sourceLevel = sourceLevel;
        this.tenantId = tenantId;
        this.officeId = officeId;
        this.shiftId = shiftId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public Object getValue() {
        return value;
    }

    public String getSourceLevel() {
        return sourceLevel;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getOfficeId() {
        return officeId;
    }

    public String getShiftId() {
        return shiftId;
    }
}
