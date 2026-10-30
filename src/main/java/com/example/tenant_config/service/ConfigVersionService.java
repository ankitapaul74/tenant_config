package com.example.tenant_config.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.tenant_config.entity.ConfigChangeEntity;
import com.example.tenant_config.repository.ConfigChangeRepository;

@Service
public class ConfigVersionService {

    private final ConfigChangeRepository changeRepository;

    public ConfigVersionService(ConfigChangeRepository changeRepository) {
        this.changeRepository = changeRepository;
    }

    public Long getCurrentVersion() {

        List<ConfigChangeEntity> changes =
                changeRepository.findByIdGreaterThanOrderByIdAsc(0L);

        if (changes.isEmpty()) {
            return 0L;
        }

        return changes.get(changes.size() - 1).getId();
    }

    public boolean isUpdateAvailable(Long clientVersion) {
        return getCurrentVersion() > clientVersion;
    }
}