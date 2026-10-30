
package com.example.tenant_config.model;

public class ConfigOverrideRequest {

    private String configKey;
    private ConfigLevel level;
    private String tenantId;
    private String officeId;
    private String shiftId;
    private String value;
    private String author;
private String reason;

    public ConfigOverrideRequest() {
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public ConfigLevel getLevel() {
        return level;
    }

    public void setLevel(ConfigLevel level) {
        this.level = level;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getOfficeId() {
        return officeId;
    }

    public void setOfficeId(String officeId) {
        this.officeId = officeId;
    }

    public String getShiftId() {
        return shiftId;
    }

    public void setShiftId(String shiftId) {
        this.shiftId = shiftId;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
    public String getAuthor() {
    return author;
}

public void setAuthor(String author) {
    this.author = author;
}

public String getReason() {
    return reason;
}

public void setReason(String reason) {
    this.reason = reason;
}
}
