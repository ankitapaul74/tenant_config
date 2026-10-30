package com.example.tenant_config.repository;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.tenant_config.entity.ConfigDefinitionEntity;

@Repository
public interface ConfigDefinitionRepository
        extends JpaRepository<ConfigDefinitionEntity, Long> {

    Optional<ConfigDefinitionEntity> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}