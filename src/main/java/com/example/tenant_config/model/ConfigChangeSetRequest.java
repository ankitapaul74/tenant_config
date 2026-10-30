
package com.example.tenant_config.model;

import java.util.List;

public class ConfigChangeSetRequest {

    private List<ConfigOverrideRequest> changes;
    private String author;
    private String reason;

    public ConfigChangeSetRequest() {
    }

    public List<ConfigOverrideRequest> getChanges() {
        return changes;
    }

    public void setChanges(List<ConfigOverrideRequest> changes) {
        this.changes = changes;
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
