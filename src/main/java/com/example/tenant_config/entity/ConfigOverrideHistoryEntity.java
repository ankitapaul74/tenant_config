
package com.example.tenant_config.entity;

import java.time.LocalDateTime;

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
@Table(name = "config_override_history")
public class ConfigOverrideHistoryEntity {

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

    private String oldValue;
    private String author;
    private String reason;

    @Column(nullable = false)
    private String newValue;

    @Column(nullable = false)
    private Integer versionNumber;

    @Column(nullable = false)
    private LocalDateTime changedAt;

    public ConfigOverrideHistoryEntity() {
    }

    public ConfigOverrideHistoryEntity(
            String configKey,
            ConfigLevel level,
            String tenantId,
            String officeId,
            String shiftId,
            String oldValue,
            String newValue,
            Integer versionNumber,
            String author,
            String reason) {

        this.configKey = configKey;
        this.level = level;
        this.tenantId = tenantId;
        this.officeId = officeId;
        this.shiftId = shiftId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.versionNumber = versionNumber;
        this.author = author;
        this.reason = reason;
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

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public String getAuthor() {
        return author;
    }

    public String getReason() {
        return reason;
    }
}
