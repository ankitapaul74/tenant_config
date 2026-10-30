
package com.example.tenant_config.model;

import java.util.List;

import com.example.tenant_config.entity.ConfigDefinitionEntity;
import com.example.tenant_config.entity.ConfigOverrideEntity;
import com.example.tenant_config.entity.ConfigRolloutEntity;

public class ConfigSnapshot {

    private List<ConfigDefinitionEntity> definitions;
    private List<ConfigOverrideEntity> overrides;
    private List<ConfigRolloutEntity> rollouts;

    private Long lastProcessedChangeId;
    private long createdAt;

    public ConfigSnapshot() {
    }

    public List<ConfigDefinitionEntity> getDefinitions() {
        return definitions;
    }

    public void setDefinitions(List<ConfigDefinitionEntity> definitions) {
        this.definitions = definitions;
    }

    public List<ConfigOverrideEntity> getOverrides() {
        return overrides;
    }

    public void setOverrides(List<ConfigOverrideEntity> overrides) {
        this.overrides = overrides;
    }

    public List<ConfigRolloutEntity> getRollouts() {
        return rollouts;
    }

    public void setRollouts(List<ConfigRolloutEntity> rollouts) {
        this.rollouts = rollouts;
    }

    public Long getLastProcessedChangeId() {
        return lastProcessedChangeId;
    }

    public void setLastProcessedChangeId(Long lastProcessedChangeId) {
        this.lastProcessedChangeId = lastProcessedChangeId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
