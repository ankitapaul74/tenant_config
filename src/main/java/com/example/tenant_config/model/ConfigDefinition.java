
package com.example.tenant_config.model;

import java.util.List;

public class ConfigDefinition {

    private String key;
    private ConfigType type;
    private Object defaultValue;
    private double minValue;
    private double maxValue;
    private List<String> allowedValues;

    // Original constructor preserved
    public ConfigDefinition(
            String key,
            ConfigType type,
            Object defaultValue,
            double minValue,
            double maxValue) {

        this(key, type, defaultValue, minValue, maxValue, null);
    }

    // Constructor supporting ENUM values
    public ConfigDefinition(
            String key,
            ConfigType type,
            Object defaultValue,
            double minValue,
            double maxValue,
            List<String> allowedValues) {

        this.key = key;
        this.type = type;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
        this.allowedValues = allowedValues;
    }

    public String getKey() {
        return key;
    }

    public ConfigType getType() {
        return type;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }
}
