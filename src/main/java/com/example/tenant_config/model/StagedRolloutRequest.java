
package com.example.tenant_config.model;

import java.util.List;

public class StagedRolloutRequest {

    private ConfigOverrideRequest configuration;
    private int rolloutPercentage;
    private List<String> officeIds;

    public StagedRolloutRequest() {
    }

    public ConfigOverrideRequest getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ConfigOverrideRequest configuration) {
        this.configuration = configuration;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public List<String> getOfficeIds() {
        return officeIds;
    }

    public void setOfficeIds(List<String> officeIds) {
        this.officeIds = officeIds;
    }
}
