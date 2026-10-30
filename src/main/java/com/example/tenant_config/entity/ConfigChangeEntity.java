
package com.example.tenant_config.entity;

import java.time.LocalDateTime;

import com.example.tenant_config.model.ConfigLevel;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "config_changes")
public class ConfigChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String configKey;

    @Enumerated(EnumType.STRING)
    private ConfigLevel level;

    private String tenantId;
    private String officeId;
    private String shiftId;

    private LocalDateTime changedAt;

    public ConfigChangeEntity() {
    }

    public ConfigChangeEntity(
            String configKey,
            ConfigLevel level,
            String tenantId,
            String officeId,
            String shiftId) {

        this.configKey = configKey;
        this.level = level;
        this.tenantId = tenantId;
        this.officeId = officeId;
        this.shiftId = shiftId;
        this.changedAt = LocalDateTime.now();
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

    public LocalDateTime getChangedAt() {
        return changedAt;
    }
}
