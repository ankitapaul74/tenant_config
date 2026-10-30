
package com.example.tenant_config.entity;

import com.example.tenant_config.model.ConfigLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "config_overrides")
public class ConfigOverrideEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String configKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigLevel level;

    private String tenantId;

    private String officeId;

    private String shiftId;

    @Column(nullable = false)
    private String value;

    // Percentage of tenants included in staged rollout
    @Column(nullable = false)
    private int rolloutPercentage = 100;

    public ConfigOverrideEntity() {
    }

    public ConfigOverrideEntity(
            String configKey,
            ConfigLevel level,
            String tenantId,
            String officeId,
            String shiftId,
            String value) {

        this.configKey = configKey;
        this.level = level;
        this.tenantId = tenantId;
        this.officeId = officeId;
        this.shiftId = shiftId;
        this.value = value;
        this.rolloutPercentage = 100;
    }

    public Long getId() {
        return id;
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

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }
}
