
package com.example.tenant_config.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "config_rollouts")
public class ConfigRolloutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String configKey;

    @Column(nullable = false)
    private String newValue;

    @Column(nullable = false)
    private int rolloutPercentage;

    @Column(columnDefinition = "TEXT")
    private String officeIds;

    @Column(nullable = false)
    private boolean promoted = false;

    private String author;
    private String reason;
    private LocalDateTime createdAt;

    public ConfigRolloutEntity() {
    }

    // Existing constructor preserved for compatibility
    public ConfigRolloutEntity(
            String configKey,
            String newValue,
            int rolloutPercentage,
            String author,
            String reason) {

        this.configKey = configKey;
        this.newValue = newValue;
        this.rolloutPercentage = rolloutPercentage;
        this.author = author;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    // New office-specific rollout constructor
    public ConfigRolloutEntity(
            String configKey,
            String newValue,
            List<String> officeIds,
            String author,
            String reason) {

        this.configKey = configKey;
        this.newValue = newValue;
        this.officeIds = String.join(",", officeIds);
        this.rolloutPercentage = 100;
        this.author = author;
        this.reason = reason;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getNewValue() {
        return newValue;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public String getOfficeIds() {
        return officeIds;
    }

    public List<String> getOfficeIdList() {
        if (officeIds == null || officeIds.isBlank()) {
            return List.of();
        }

        return Arrays.stream(officeIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    public boolean isPromoted() {
        return promoted;
    }

    public String getAuthor() {
        return author;
    }

    public String getReason() {
        return reason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public void setPromoted(boolean promoted) {
        this.promoted = promoted;
    }
}
