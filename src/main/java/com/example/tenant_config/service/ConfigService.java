
package com.example.tenant_config.service;
import java.time.Duration;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.example.tenant_config.entity.ConfigChangeEntity;
import com.example.tenant_config.entity.ConfigDefinitionEntity;
import com.example.tenant_config.entity.ConfigOverrideEntity;
import com.example.tenant_config.entity.ConfigOverrideHistoryEntity;
import com.example.tenant_config.entity.ConfigRolloutEntity;
import com.example.tenant_config.model.ConfigChangeSetRequest;
import com.example.tenant_config.model.ConfigDefinition;
import com.example.tenant_config.model.ConfigExplainResponse;
import com.example.tenant_config.model.ConfigLevel;
import com.example.tenant_config.model.ConfigOverrideRequest;
import com.example.tenant_config.model.ConfigRollbackRequest;
import com.example.tenant_config.model.ConfigSnapshot;
import com.example.tenant_config.model.ConfigType;
import com.example.tenant_config.model.StagedRolloutRequest;
import com.example.tenant_config.repository.ConfigChangeRepository;
import com.example.tenant_config.repository.ConfigDefinitionRepository;
import com.example.tenant_config.repository.ConfigOverrideHistoryRepository;
import com.example.tenant_config.repository.ConfigOverrideRepository;
import com.example.tenant_config.repository.ConfigRolloutRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class ConfigService {

    private final ConfigDefinitionRepository definitionRepository;
    private final ConfigOverrideRepository overrideRepository;
    private final ConfigOverrideHistoryRepository historyRepository;
    private final ConfigChangeRepository changeRepository;
    private Long lastProcessedChangeId = 0L;
    private final ConfigRolloutRepository rolloutRepository;
    private final ConfigSnapshotService snapshotService;
    private final ConfigEventService configEventService;
    private final ConcurrentMap<String, ConfigOverrideEntity> overrideCache =
        new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ConfigDefinitionEntity> definitionCache =
        new ConcurrentHashMap<>();

private final ConcurrentMap<String, ConfigRolloutEntity> rolloutCache =
        new ConcurrentHashMap<>();

    private String createCacheKey(
        String configKey,
        ConfigLevel level,
        String tenantId,
        String officeId,
        String shiftId) {

    return configKey + "|" + level + "|" +
            tenantId + "|" + officeId + "|" + shiftId;
}



@PostConstruct
public void loadOverridesIntoCache() {

    boolean restored = restoreSnapshot();

    if (restored) {
        System.out.println("Loaded configuration from local snapshot.");
        return;
    }

    System.out.println("No snapshot found. Loading from database.");

    // Load configuration definitions
    List<ConfigDefinitionEntity> definitions =
            definitionRepository.findAll();

    for (ConfigDefinitionEntity definition : definitions) {
        definitionCache.put(
                definition.getConfigKey(),
                definition
        );
    }

    // Load configuration overrides
    List<ConfigOverrideEntity> overrides =
            overrideRepository.findAll();

    for (ConfigOverrideEntity override : overrides) {

        String key = createCacheKey(
                override.getConfigKey(),
                override.getLevel(),
                override.getTenantId(),
                override.getOfficeId(),
                override.getShiftId()
        );

        overrideCache.put(key, override);
    }

    // Load staged rollouts
    List<ConfigRolloutEntity> rollouts =
            rolloutRepository.findAll();

    for (ConfigRolloutEntity rollout : rollouts) {

        ConfigRolloutEntity existing =
                rolloutCache.get(rollout.getConfigKey());

        if (existing == null ||
                rollout.getCreatedAt().isAfter(existing.getCreatedAt())) {

            rolloutCache.put(
                    rollout.getConfigKey(),
                    rollout
            );
        }
    }

    saveCurrentSnapshot();
}



   public ConfigService(
        ConfigDefinitionRepository definitionRepository,
        ConfigOverrideRepository overrideRepository,
        ConfigOverrideHistoryRepository historyRepository,
        ConfigChangeRepository changeRepository,
        ConfigRolloutRepository rolloutRepository,
        ConfigSnapshotService snapshotService,
ConfigEventService configEventService) {

    this.definitionRepository = definitionRepository;
    this.overrideRepository = overrideRepository;
    this.historyRepository = historyRepository;
    this.changeRepository = changeRepository;
    this.rolloutRepository = rolloutRepository;
    this.snapshotService = snapshotService;
    this.configEventService = configEventService;
}

    // Check whether a configuration definition exists
    public boolean definitionExists(String configKey) {
        return definitionRepository.existsByConfigKey(configKey);
    }

    // Add a new configuration definition
    public void addDefinition(ConfigDefinition definition) {
        registerDefinition(definition);
    }

    // Save override only if the same hierarchy entry does not exist

@Transactional
public void addOverride(ConfigOverrideEntity override, String author,
        String reason) {

    Optional<ConfigDefinitionEntity> definition =
            definitionRepository.findByConfigKey(override.getConfigKey());

    if (definition.isEmpty()) {
        throw new IllegalArgumentException(
                "Configuration not found: " + override.getConfigKey());
    }

    validateOverrideValue(override.getValue(), definition.get());

    List<ConfigOverrideEntity> existingOverrides =
            overrideRepository.findByConfigKeyAndLevel(
                    override.getConfigKey(),
                    override.getLevel()
            );

    Optional<ConfigOverrideEntity> existingOverride =
            existingOverrides.stream()
                    .filter(existing ->
                            same(existing.getTenantId(), override.getTenantId()) &&
                            same(existing.getOfficeId(), override.getOfficeId()) &&
                            same(existing.getShiftId(), override.getShiftId()))
                    .findFirst();

    String oldValue = null;

    if (existingOverride.isPresent()) {

        ConfigOverrideEntity existing = existingOverride.get();

        oldValue = existing.getValue();

        // No history entry needed if value has not changed
        if (oldValue.equals(override.getValue())) {
            return;
        }

        existing.setValue(override.getValue());
        overrideRepository.save(existing);

    } else {
        overrideRepository.save(override);
    }

    // Find the previous versions for this configuration
    List<ConfigOverrideHistoryEntity> history =
            historyRepository.findByConfigKeyOrderByVersionNumberDesc(
                    override.getConfigKey());

    int nextVersion = history.stream()
            .filter(h -> h.getLevel() == override.getLevel())
            .filter(h -> same(h.getTenantId(), override.getTenantId()))
            .filter(h -> same(h.getOfficeId(), override.getOfficeId()))
            .filter(h -> same(h.getShiftId(), override.getShiftId()))
            .mapToInt(ConfigOverrideHistoryEntity::getVersionNumber)
            .max()
            .orElse(0) + 1;

    ConfigOverrideHistoryEntity historyEntry =
        new ConfigOverrideHistoryEntity(
                override.getConfigKey(),
                override.getLevel(),
                override.getTenantId(),
                override.getOfficeId(),
                override.getShiftId(),
                oldValue,
                override.getValue(),
                nextVersion,
                author,
                reason
        );
    historyRepository.save(historyEntry);
    // Record this change for other application instances
ConfigChangeEntity change = new ConfigChangeEntity(
        override.getConfigKey(),
        override.getLevel(),
        override.getTenantId(),
        override.getOfficeId(),
        override.getShiftId()
);

changeRepository.save(change);

// Update in-memory cache immediately
String cacheKey = createCacheKey(
        override.getConfigKey(),
        override.getLevel(),
        override.getTenantId(),
        override.getOfficeId(),
        override.getShiftId()
);

TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                overrideCache.put(cacheKey, override);
            }
        }
);
}


@Transactional
public void applyChangeSet(ConfigChangeSetRequest request) {

    if (request.getChanges() == null || request.getChanges().isEmpty()) {
        throw new IllegalArgumentException(
                "Change set cannot be empty"
        );
    }

    for (ConfigOverrideRequest change : request.getChanges()) {

        ConfigOverrideEntity override = new ConfigOverrideEntity(
                change.getConfigKey(),
                change.getLevel(),
                change.getTenantId(),
                change.getOfficeId(),
                change.getShiftId(),
                change.getValue()
        );

        addOverride(
                override,
                request.getAuthor(),
                request.getReason()
        );
    }
}




@Transactional
public void applyStagedRollout(StagedRolloutRequest request) {

    ConfigOverrideRequest config = request.getConfiguration();

    if (config == null) {
        throw new IllegalArgumentException(
                "Configuration details are required");
    }

    if (config.getLevel() != ConfigLevel.GLOBAL ||
            config.getTenantId() != null ||
            config.getOfficeId() != null ||
            config.getShiftId() != null) {

        throw new IllegalArgumentException(
                "Staged rollout configuration must be GLOBAL");
    }

    if (request.getOfficeIds() == null ||
            request.getOfficeIds().isEmpty()) {

        throw new IllegalArgumentException(
                "At least one target office is required");
    }

    List<String> officeIds = request.getOfficeIds().stream()
            .filter(id -> id != null && !id.isBlank())
            .map(String::trim)
            .distinct()
            .toList();

    if (officeIds.isEmpty()) {
        throw new IllegalArgumentException(
                "Valid office IDs are required");
    }

    Optional<ConfigDefinitionEntity> definition =
            definitionRepository.findByConfigKey(
                    config.getConfigKey());

    if (definition.isEmpty()) {
        throw new IllegalArgumentException(
                "Configuration not found: "
                        + config.getConfigKey());
    }

    validateOverrideValue(
            config.getValue(),
            definition.get());

    ConfigRolloutEntity rollout = new ConfigRolloutEntity(
            config.getConfigKey(),
            config.getValue(),
            officeIds,
            config.getAuthor(),
            config.getReason()
    );

    ConfigRolloutEntity savedRollout =
            rolloutRepository.save(rollout);

    ConfigChangeEntity change = new ConfigChangeEntity(
            config.getConfigKey(),
            ConfigLevel.GLOBAL,
            null,
            null,
            null
    );

    changeRepository.save(change);

    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rolloutCache.put(
                            savedRollout.getConfigKey(),
                            savedRollout);
                }
            }
    );
}



    // Register configuration definition in MySQL
   

@Transactional
public void registerDefinition(ConfigDefinition definition) {

    if (definitionRepository.existsByConfigKey(definition.getKey())) {
        throw new IllegalArgumentException(
                "Configuration already exists: " + definition.getKey());
    }

    if (definition.getMinValue() > definition.getMaxValue()) {
        throw new IllegalArgumentException(
                "Minimum value cannot exceed maximum value");
    }

    String allowedValues = null;

    if (definition.getType() == ConfigType.ENUM) {
        if (definition.getAllowedValues() == null
                || definition.getAllowedValues().isEmpty()) {
            throw new IllegalArgumentException(
                    "ENUM configuration requires allowedValues");
        }

        allowedValues = String.join(",", definition.getAllowedValues());

        if (!definition.getAllowedValues().contains(
                String.valueOf(definition.getDefaultValue()))) {
            throw new IllegalArgumentException(
                    "Default value must be one of the allowed ENUM values");
        }
    }

    ConfigDefinitionEntity entity = new ConfigDefinitionEntity(
            definition.getKey(),
            definition.getType(),
            String.valueOf(definition.getDefaultValue()),
            definition.getMinValue(),
            definition.getMaxValue(),
            allowedValues
    );

    validateOverrideValue(entity.getDefaultValue(), entity);

    ConfigDefinitionEntity savedDefinition =
            definitionRepository.save(entity);

    ConfigChangeEntity change = new ConfigChangeEntity(
            definition.getKey(),
            null,
            null,
            null,
            null
    );

    changeRepository.save(change);

    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    definitionCache.put(
                            savedDefinition.getConfigKey(),
                            savedDefinition
                    );
                }
            }
    );
}




private void saveCurrentSnapshot() {

    ConfigSnapshot snapshot = new ConfigSnapshot();

    snapshot.setDefinitions(
            List.copyOf(definitionCache.values())
    );

    snapshot.setOverrides(
            List.copyOf(overrideCache.values())
    );

    snapshot.setRollouts(
            List.copyOf(rolloutCache.values())
    );

    snapshot.setLastProcessedChangeId(lastProcessedChangeId);

    snapshot.setCreatedAt(System.currentTimeMillis());

    snapshotService.saveSnapshot(snapshot);
}


private boolean restoreSnapshot() {

    Optional<ConfigSnapshot> savedSnapshot =
            snapshotService.loadSnapshot();

    if (savedSnapshot.isEmpty()) {
        System.out.println("No previous snapshot found.");
        return false;
    }

    ConfigSnapshot snapshot = savedSnapshot.get();

    // Restore configuration definitions
    definitionCache.clear();

    for (ConfigDefinitionEntity definition :
            snapshot.getDefinitions()) {

        definitionCache.put(
                definition.getConfigKey(),
                definition
        );
    }

    // Restore configuration overrides
    overrideCache.clear();

    for (ConfigOverrideEntity override :
            snapshot.getOverrides()) {

        String key = createCacheKey(
                override.getConfigKey(),
                override.getLevel(),
                override.getTenantId(),
                override.getOfficeId(),
                override.getShiftId()
        );

        overrideCache.put(key, override);
    }

    // Restore staged rollouts
    rolloutCache.clear();

    for (ConfigRolloutEntity rollout :
            snapshot.getRollouts()) {

        rolloutCache.put(
                rollout.getConfigKey(),
                rollout
        );
    }

    lastProcessedChangeId =
            snapshot.getLastProcessedChangeId() != null
                    ? snapshot.getLastProcessedChangeId()
                    : 0L;

    System.out.println("Configuration caches restored from snapshot.");

    return true;
}



    // Resolve configuration using hierarchy priority
   
public Object resolve(
        String configKey,
        String tenantId,
        String officeId,
        String shiftId) {

    // Get definition directly from in-memory cache
    ConfigDefinitionEntity definition =
            definitionCache.get(configKey);

    if (definition == null) {
        throw new IllegalArgumentException(
                "Configuration not found: " + configKey);
    }

    ConfigType type = definition.getType();

    // 1. SHIFT level
    Optional<ConfigOverrideEntity> shiftOverride =
            findOverride(configKey, ConfigLevel.SHIFT,
                    tenantId, officeId, shiftId);

    if (shiftOverride.isPresent()) {
        return convertValue(shiftOverride.get().getValue(), type);
    }

    // 2. OFFICE level
    Optional<ConfigOverrideEntity> officeOverride =
            findOverride(configKey, ConfigLevel.OFFICE,
                    tenantId, officeId, null);

    if (officeOverride.isPresent()) {
        return convertValue(officeOverride.get().getValue(), type);
    }

    // 3. CLIENT level
    Optional<ConfigOverrideEntity> clientOverride =
            findOverride(configKey, ConfigLevel.CLIENT,
                    tenantId, null, null);

    if (clientOverride.isPresent()) {
        return convertValue(clientOverride.get().getValue(), type);
    }

    // 4. STAGED ROLLOUT from in-memory cache
    
// 4. OFFICE-SPECIFIC STAGED ROLLOUT
ConfigRolloutEntity rollout = rolloutCache.get(configKey);

if (rollout != null
        && !rollout.isPromoted()
        && officeId != null
        && rollout.getOfficeIdList().contains(officeId)) {

    return convertValue(rollout.getNewValue(), type);
}


    // 5. GLOBAL level
    Optional<ConfigOverrideEntity> globalOverride =
            findOverride(configKey, ConfigLevel.GLOBAL,
                    null, null, null);

    if (globalOverride.isPresent()) {
        return convertValue(globalOverride.get().getValue(), type);
    }

    // 6. DEFAULT value
    return convertValue(definition.getDefaultValue(), type);
}


    // Find matching override in the database
   private Optional<ConfigOverrideEntity> findOverride(
        String configKey,
        ConfigLevel level,
        String tenantId,
        String officeId,
        String shiftId) {

    String cacheKey = createCacheKey(
            configKey,
            level,
            tenantId,
            officeId,
            shiftId
    );

    return Optional.ofNullable(overrideCache.get(cacheKey));
}

    // Convert database String into the actual configuration data type
   // Convert database String into the actual configuration data type
private Object convertValue(String value, ConfigType type) {

    switch (type) {

        case INTEGER:
            return Integer.parseInt(value);

        case DOUBLE:
            return Double.parseDouble(value);

        case BOOLEAN:
            if (!value.equalsIgnoreCase("true") &&
                    !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(
                        "Invalid BOOLEAN value: " + value);
            }
            return Boolean.parseBoolean(value);

        case STRING:
            return value;

        case DURATION:
            return Duration.parse(value);

        case TIME_WINDOW: {
            String[] parts = value.split("-", -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "TIME_WINDOW must use HH:mm-HH:mm format");
            }

            LocalTime start = LocalTime.parse(parts[0]);
            LocalTime end = LocalTime.parse(parts[1]);

            return Map.of(
                    "start", start.toString(),
                    "end", end.toString()
            );
        }

        case ENUM:
            return value;

        case LIST:
            try {
                ObjectMapper mapper = new ObjectMapper();

                return mapper.readValue(
                        value,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Object>>() {}
                );

            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalArgumentException(
                        "LIST value must contain valid JSON");
            }

        default:
            throw new IllegalArgumentException(
                    "Unsupported configuration type");
    }
}

    // Compare hierarchy identifiers, including null values
    private boolean same(String first, String second) {

        if (first == null) {
            return second == null;
        }

        return first.equals(second);
    }
    
            

private void validateOverrideValue(
        String value,
        ConfigDefinitionEntity definition) {

    if (value == null) {
        throw new IllegalArgumentException(
                "Configuration value cannot be null");
    }

    try {
        switch (definition.getType()) {

            case INTEGER: {
                int number = Integer.parseInt(value);

                if (number < definition.getMinValue()
                        || number > definition.getMaxValue()) {
                    throw new IllegalArgumentException(
                            "Value must be between "
                                    + definition.getMinValue()
                                    + " and "
                                    + definition.getMaxValue());
                }
                break;
            }

            case DOUBLE: {
                double number = Double.parseDouble(value);

                if (!Double.isFinite(number)
                        || number < definition.getMinValue()
                        || number > definition.getMaxValue()) {
                    throw new IllegalArgumentException(
                            "Value is outside the permitted range");
                }
                break;
            }

            case BOOLEAN:
                if (!value.equalsIgnoreCase("true")
                        && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException(
                            "Value must be true or false");
                }
                break;

            case STRING:
                break;

            case DURATION:
                Duration.parse(value);
                break;

            case TIME_WINDOW: {
                String[] parts = value.split("-", -1);

                if (parts.length != 2) {
                    throw new IllegalArgumentException(
                            "TIME_WINDOW must use HH:mm-HH:mm format");
                }

                LocalTime.parse(parts[0]);
                LocalTime.parse(parts[1]);
                break;
            }

            case ENUM:
                if (definition.getAllowedValues() == null
                        || !Arrays.asList(
                                definition.getAllowedValues().split(","))
                                .contains(value)) {
                    throw new IllegalArgumentException(
                            "Value must be one of: "
                                    + definition.getAllowedValues());
                }
                break;

            case LIST:
                ObjectMapper mapper = new ObjectMapper();

                if (!mapper.readTree(value).isArray()) {
                    throw new IllegalArgumentException(
                            "LIST value must be a JSON array");
                }
                break;

            default:
                throw new IllegalArgumentException(
                        "Unsupported configuration type");
        }

    } catch (NumberFormatException e) {
        throw new IllegalArgumentException(
                "Invalid numeric value for " + definition.getType());

    } catch (java.time.format.DateTimeParseException e) {
        throw new IllegalArgumentException(
                "Invalid time or duration format");

    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalArgumentException(
                "LIST value must contain valid JSON");
    }
}


@Transactional
public void rollbackConfig(ConfigRollbackRequest request) {

    // Retrieve all history for this configuration
    List<ConfigOverrideHistoryEntity> history =
            historyRepository.findByConfigKeyOrderByVersionNumberDesc(
                    request.getConfigKey());

    // Find the requested version for the exact hierarchy entry
    Optional<ConfigOverrideHistoryEntity> targetVersion =
            history.stream()
                    .filter(h -> h.getLevel() == request.getLevel())
                    .filter(h -> same(h.getTenantId(), request.getTenantId()))
                    .filter(h -> same(h.getOfficeId(), request.getOfficeId()))
                    .filter(h -> same(h.getShiftId(), request.getShiftId()))
                    .filter(h -> h.getVersionNumber().equals(
                            request.getVersionNumber()))
                    .findFirst();

    if (targetVersion.isEmpty()) {
        throw new IllegalArgumentException(
                "Requested configuration version not found");
    }

    // Restore the value saved in the selected historical version
    String valueToRestore = targetVersion.get().getNewValue();

    ConfigOverrideEntity override = new ConfigOverrideEntity(
            request.getConfigKey(),
            request.getLevel(),
            request.getTenantId(),
            request.getOfficeId(),
            request.getShiftId(),
            valueToRestore
    );

    // Reuse addOverride so validation and new history recording happen
    // Reuse addOverride so validation and new history recording happen
addOverride(
        override,
        "system",
        "Rollback to version " + request.getVersionNumber()
);
}


@Transactional
public void promoteStagedRollout(
        Long rolloutId,
        String author,
        String reason) {

    ConfigRolloutEntity rollout =
            rolloutRepository.findById(rolloutId)
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "Staged rollout not found: "
                                            + rolloutId));

    if (rollout.isPromoted()) {
        throw new IllegalArgumentException(
                "This staged rollout has already been promoted");
    }

    // Create a GLOBAL override so every office receives the value.
    ConfigOverrideEntity globalOverride =
            new ConfigOverrideEntity(
                    rollout.getConfigKey(),
                    ConfigLevel.GLOBAL,
                    null,
                    null,
                    null,
                    rollout.getNewValue()
            );

    addOverride(
            globalOverride,
            author,
            reason
    );
    
    rollout.setPromoted(true);

    ConfigRolloutEntity savedRollout =
            rolloutRepository.save(rollout);

    TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rolloutCache.put(
                            savedRollout.getConfigKey(),
                            savedRollout);
                }
            }
    );
}




@Scheduled(fixedDelay = 1000)
public void refreshCacheFromDatabase() {

    try {

        // Fetch all changes that have not yet been processed
        List<ConfigChangeEntity> changes =
                changeRepository.findByIdGreaterThanOrderByIdAsc(
                        lastProcessedChangeId
                );

        for (ConfigChangeEntity change : changes) {

            // 1. Handle configuration definition changes
            if (change.getLevel() == null) {

                Optional<ConfigDefinitionEntity> definition =
                        definitionRepository.findByConfigKey(
                                change.getConfigKey()
                        );

                definition.ifPresent(config ->
                        definitionCache.put(
                                config.getConfigKey(),
                                config
                        )
                );

            } else {

                // 2. Refresh configuration override cache
                List<ConfigOverrideEntity> overrides =
                        overrideRepository.findByConfigKeyAndLevel(
                                change.getConfigKey(),
                                change.getLevel()
                        );

                Optional<ConfigOverrideEntity> matchingOverride =
                        overrides.stream()
                                .filter(o -> same(
                                        o.getTenantId(), change.getTenantId()))
                                .filter(o -> same(
                                        o.getOfficeId(), change.getOfficeId()))
                                .filter(o -> same(
                                        o.getShiftId(), change.getShiftId()))
                                .findFirst();

                if (matchingOverride.isPresent()) {

                    ConfigOverrideEntity override =
                            matchingOverride.get();

                    String cacheKey = createCacheKey(
                            override.getConfigKey(),
                            override.getLevel(),
                            override.getTenantId(),
                            override.getOfficeId(),
                            override.getShiftId()
                    );

                    overrideCache.put(cacheKey, override);
                }

                // 3. Refresh staged rollout cache
                if (change.getLevel() == ConfigLevel.GLOBAL
                        && change.getTenantId() == null
                        && change.getOfficeId() == null
                        && change.getShiftId() == null) {

                    Optional<ConfigRolloutEntity> latestRollout =
                            rolloutRepository
                                    .findFirstByConfigKeyOrderByCreatedAtDesc(
                                            change.getConfigKey()
                                    );

                    latestRollout.ifPresent(rollout ->
                            rolloutCache.put(
                                    rollout.getConfigKey(),
                                    rollout
                            )
                    );
                }
            }

            // 4. Mark change as processed
            configEventService.publish(
    String.valueOf(change.getId()),
    "Configuration changed: " + change.getConfigKey()
    + ", Change ID: " + change.getId(),
    change.getTenantId(),
    change.getTenantId() == null
);
            lastProcessedChangeId = change.getId();
        }

        // 5. Save updated configuration snapshot
        if (!changes.isEmpty()) {
            saveCurrentSnapshot();
        }

    } catch (DataAccessException e) {

        // Keep serving the last good in-memory configuration
        System.err.println(
                "Database unavailable. Continuing with last good configuration snapshot: "
                        + e.getMessage()
        );
    }
}





public ConfigExplainResponse explainConfig(
        String configKey,
        String tenantId,
        String officeId,
        String shiftId) {

    Optional<ConfigDefinitionEntity> definition =
            definitionRepository.findByConfigKey(configKey);

    if (definition.isEmpty()) {
        throw new IllegalArgumentException(
                "Configuration not found: " + configKey);
    }

    ConfigType type = definition.get().getType();

    // 1. Check SHIFT level
    Optional<ConfigOverrideEntity> override =
            findOverride(configKey, ConfigLevel.SHIFT,
                    tenantId, officeId, shiftId);

    if (override.isPresent()) {
        return new ConfigExplainResponse(
                configKey,
                convertValue(override.get().getValue(), type),
                "SHIFT", tenantId, officeId, shiftId);
    }

    // 2. Check OFFICE level
    override = findOverride(configKey, ConfigLevel.OFFICE,
            tenantId, officeId, null);

    if (override.isPresent()) {
        return new ConfigExplainResponse(
                configKey,
                convertValue(override.get().getValue(), type),
                "OFFICE", tenantId, officeId, null);
    }

    // 3. Check CLIENT level
    override = findOverride(configKey, ConfigLevel.CLIENT,
            tenantId, null, null);

    if (override.isPresent()) {
        return new ConfigExplainResponse(
                configKey,
                convertValue(override.get().getValue(), type),
                "CLIENT", tenantId, null, null);
    }

    
// 4. Check OFFICE-SPECIFIC STAGED ROLLOUT
ConfigRolloutEntity stagedRollout =
        rolloutCache.get(configKey);

if (stagedRollout != null
        && !stagedRollout.isPromoted()
        && officeId != null
        && stagedRollout.getOfficeIdList().contains(officeId)) {

    return new ConfigExplainResponse(
            configKey,
            convertValue(stagedRollout.getNewValue(), type),
            "STAGED_ROLLOUT",
            tenantId,
            officeId,
            null
    );
}


    // 5. Check GLOBAL level
    override = findOverride(configKey, ConfigLevel.GLOBAL,
            null, null, null);

    if (override.isPresent()) {
        return new ConfigExplainResponse(
                configKey,
                convertValue(override.get().getValue(), type),
                "GLOBAL", null, null, null);
    }

    // 6. Use default value
    return new ConfigExplainResponse(
            configKey,
            convertValue(definition.get().getDefaultValue(), type),
            "DEFAULT", null, null, null);
}


public List<ConfigOverrideHistoryEntity> getConfigHistory(
        String configKey) {

    // Administrator can see all tenant histories
    return historyRepository
            .findByConfigKeyOrderByVersionNumberDesc(configKey);
}

public List<ConfigOverrideHistoryEntity> getConfigHistory(
        String configKey,
        String tenantId,
        boolean admin) {

    if (admin) {
        return getConfigHistory(configKey);
    }

    if (tenantId == null || tenantId.isBlank()) {
        throw new IllegalArgumentException(
                "Tenant ID is required");
    }

    // USER can see only their tenant's history and GLOBAL history
    return historyRepository.findVisibleHistoryForTenant(
            configKey,
            tenantId);
}



}
