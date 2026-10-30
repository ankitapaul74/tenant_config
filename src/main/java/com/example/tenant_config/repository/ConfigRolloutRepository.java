
package com.example.tenant_config.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.tenant_config.entity.ConfigRolloutEntity;

public interface ConfigRolloutRepository
        extends JpaRepository<ConfigRolloutEntity, Long> {

    List<ConfigRolloutEntity> findByConfigKey(String configKey);

    Optional<ConfigRolloutEntity>
            findFirstByConfigKeyOrderByCreatedAtDesc(String configKey);
}
