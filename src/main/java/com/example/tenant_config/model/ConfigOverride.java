
package com.example.tenant_config.model;

public class ConfigOverride {

    private String configKey;
    private ConfigLevel level;
    private String tenantId;
    private String officeId;
    private String shiftId;
    private Object value;

    public ConfigOverride(
            String configKey,
            ConfigLevel level,
            String tenantId,
            String officeId,
            String shiftId,
            Object value) {

        this.configKey = configKey;
        this.level = level;
        this.tenantId = tenantId;
        this.officeId = officeId;
        this.shiftId = shiftId;
        this.value = value;
    }

    public String getConfigKey() {
        return configKey;
    }

    public ConfigLevel getLevel() {
        return level;
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

    public Object getValue() {
        return value;
    }
}
