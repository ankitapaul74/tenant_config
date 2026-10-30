
package com.example.tenant_config.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.tenant_config.entity.ConfigOverrideHistoryEntity;

public interface ConfigOverrideHistoryRepository
        extends JpaRepository<ConfigOverrideHistoryEntity, Long> {

    // All history entries for administrators
    List<ConfigOverrideHistoryEntity>
            findByConfigKeyOrderByVersionNumberDesc(String configKey);

    // Tenant-specific history plus GLOBAL history
    @Query("""
        SELECT h
        FROM ConfigOverrideHistoryEntity h
        WHERE h.configKey = :configKey
          AND (h.tenantId = :tenantId OR h.tenantId IS NULL)
        ORDER BY h.changedAt DESC
        """)
    List<ConfigOverrideHistoryEntity> findVisibleHistoryForTenant(
            @Param("configKey") String configKey,
            @Param("tenantId") String tenantId);

    List<ConfigOverrideHistoryEntity>
            findByConfigKeyAndTenantIdAndOfficeIdAndShiftIdOrderByVersionNumberDesc(
                    String configKey,
                    String tenantId,
                    String officeId,
                    String shiftId);
}
