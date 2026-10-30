
package com.example.tenant_config.controller;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.tenant_config.entity.ConfigOverrideEntity;
import com.example.tenant_config.entity.ConfigOverrideHistoryEntity;
import com.example.tenant_config.model.ConfigChangeSetRequest;
import com.example.tenant_config.model.ConfigDefinition;
import com.example.tenant_config.model.ConfigDefinitionRequest;
import com.example.tenant_config.model.ConfigExplainResponse;
import com.example.tenant_config.model.ConfigOverrideRequest;
import com.example.tenant_config.model.ConfigRollbackRequest;
import com.example.tenant_config.model.StagedRolloutRequest;
import com.example.tenant_config.service.ConfigEventService;
import com.example.tenant_config.service.ConfigService;
import com.example.tenant_config.service.ConfigVersionService;

@RestController
@RequestMapping("/api/configs")
public class ConfigController {

    private final ConfigService configService;
    private final ConfigEventService configEventService;
    private final ConfigVersionService configVersionService;

    public ConfigController(
            ConfigService configService,
            ConfigEventService configEventService,
            ConfigVersionService configVersionService) {

        this.configService = configService;
        this.configEventService = configEventService;
        this.configVersionService = configVersionService;
    }

    @GetMapping("/version")
    public Map<String, Object> checkVersion(
            @RequestParam(defaultValue = "0") Long since) {

        Long currentVersion =
                configVersionService.getCurrentVersion();

        return Map.of(
                "currentVersion", currentVersion,
                "updateAvailable",
                configVersionService.isUpdateAvailable(since)
        );
    }
   
@PostMapping("/staged-rollout/{rolloutId}/promote")
public String promoteStagedRollout(
        @PathVariable Long rolloutId,
        @RequestParam(defaultValue = "admin") String author,
        @RequestParam(defaultValue = "Promoted staged rollout globally")
                String reason) {

    configService.promoteStagedRollout(
            rolloutId,
            author,
            reason
    );

    return "Staged rollout promoted globally successfully";
}

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter subscribeToConfigEvents(
            @RequestHeader(
                    value = "Last-Event-ID",
                    required = false
            ) String lastEventId,
            Authentication authentication) {

        Long eventId = null;

        if (lastEventId != null && !lastEventId.isBlank()) {
            eventId = Long.parseLong(lastEventId);
        }

        boolean admin = isAdmin(authentication);

        return configEventService.subscribe(
                eventId,
                authentication.getName(),
                admin
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public String createDefinition(
            @RequestBody ConfigDefinitionRequest request) {

        ConfigDefinition definition = new ConfigDefinition(
                request.getKey(),
                request.getType(),
                request.getDefaultValue(),
                request.getMinValue(),
                request.getMaxValue(),
                request.getAllowedValues()
        );

        configService.registerDefinition(definition);

        return "Configuration created: " + definition.getKey();
    }

    @PostMapping("/overrides")
    @ResponseStatus(HttpStatus.CREATED)
    public String createOverride(
            @RequestBody ConfigOverrideRequest request) {

        ConfigOverrideEntity override = new ConfigOverrideEntity(
                request.getConfigKey(),
                request.getLevel(),
                request.getTenantId(),
                request.getOfficeId(),
                request.getShiftId(),
                request.getValue()
        );

        configService.addOverride(
                override,
                request.getAuthor(),
                request.getReason()
        );

        return "Configuration override created successfully";
    }

    @GetMapping("/resolve")
    public Object resolve(
            @RequestParam String configKey,
            @RequestParam String tenantId,
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String shiftId,
            Authentication authentication) {

        String effectiveTenantId =
                getEffectiveTenantId(tenantId, authentication);

        return configService.resolve(
                configKey,
                effectiveTenantId,
                officeId,
                shiftId
        );
    }

    @GetMapping("/explain")
    public ConfigExplainResponse explain(
            @RequestParam String configKey,
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String officeId,
            @RequestParam(required = false) String shiftId,
            Authentication authentication) {

        String effectiveTenantId =
                getEffectiveTenantId(tenantId, authentication);

        return configService.explainConfig(
                configKey,
                effectiveTenantId,
                officeId,
                shiftId
        );
    }

    // Tenant-aware configuration history
    @GetMapping("/history")
    public List<ConfigOverrideHistoryEntity> getConfigHistory(
            @RequestParam String configKey,
            Authentication authentication) {

        boolean admin = isAdmin(authentication);

        String tenantId = admin
                ? null
                : authentication.getName();

        return configService.getConfigHistory(
                configKey,
                tenantId,
                admin
        );
    }

    @PostMapping("/change-set")
    public String applyChangeSet(
            @RequestBody ConfigChangeSetRequest request) {

        configService.applyChangeSet(request);

        return "Configuration change set applied successfully";
    }

    @PostMapping("/staged-rollout")
    public String startStagedRollout(
            @RequestBody StagedRolloutRequest request) {

        configService.applyStagedRollout(request);

        return "Staged configuration rollout created successfully";
    }

    @PostMapping("/rollback")
    public String rollbackConfiguration(
            @RequestBody ConfigRollbackRequest request) {

        configService.rollbackConfig(request);

        return "Configuration rolled back successfully";
    }

    private boolean isAdmin(Authentication authentication) {

        return authentication.getAuthorities()
                .stream()
                .anyMatch(authority ->
                        authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private String getEffectiveTenantId(
            String requestedTenantId,
            Authentication authentication) {

        if (isAdmin(authentication)) {
            return requestedTenantId;
        }

        return authentication.getName();
    }
}
