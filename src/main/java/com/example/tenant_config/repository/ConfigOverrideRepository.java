
package com.example.tenant_config.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.tenant_config.entity.ConfigOverrideEntity;
import com.example.tenant_config.model.ConfigLevel;

@Repository
public interface ConfigOverrideRepository
        extends JpaRepository<ConfigOverrideEntity, Long> {

    List<ConfigOverrideEntity> findByConfigKeyAndLevel(
            String configKey,
            ConfigLevel level
    );

    List<ConfigOverrideEntity> findByConfigKey(String configKey);
}
