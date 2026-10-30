
package com.example.tenant_config.entity;

import com.example.tenant_config.model.ConfigType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "config_definitions")
public class ConfigDefinitionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String configKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConfigType type;

    @Column(nullable = false)
    private String defaultValue;

    private Double minValue;

    private Double maxValue;

    @Column(columnDefinition = "TEXT")
    private String allowedValues;

    public ConfigDefinitionEntity() {
    }

    // Existing constructor preserved for compatibility
    public ConfigDefinitionEntity(
            String configKey,
            ConfigType type,
            String defaultValue,
            Double minValue,
            Double maxValue) {

        this.configKey = configKey;
        this.type = type;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    // Constructor supporting ENUM allowed values
    public ConfigDefinitionEntity(
            String configKey,
            ConfigType type,
            String defaultValue,
            Double minValue,
            Double maxValue,
            String allowedValues) {

        this.configKey = configKey;
        this.type = type;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.allowedValues = allowedValues;
    }

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public ConfigType getType() {
        return type;
    }

    public void setType(ConfigType type) {
        this.type = type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public Double getMinValue() {
        return minValue;
    }

    public void setMinValue(Double minValue) {
        this.minValue = minValue;
    }

    public Double getMaxValue() {
        return maxValue;
    }

    public void setMaxValue(Double maxValue) {
        this.maxValue = maxValue;
    }

    public String getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(String allowedValues) {
        this.allowedValues = allowedValues;
    }
}
