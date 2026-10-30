
package com.example.tenant_config.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.tenant_config.entity.ConfigChangeEntity;

public interface ConfigChangeRepository
        extends JpaRepository<ConfigChangeEntity, Long> {

    List<ConfigChangeEntity> findByIdGreaterThanOrderByIdAsc(Long id);
}
