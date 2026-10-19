package com.securevault.repository;

import com.securevault.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByUserIdOrderByTimestampDesc(Long userId);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByTimestampDesc(String entityType, Long entityId);

    List<AuditLog> findAllByOrderByTimestampDesc();

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(DISTINCT a.user.id) FROM AuditLog a WHERE a.user IS NOT NULL AND a.timestamp >= :since"
    )
    long countDistinctActiveUsers(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    List<AuditLog> findTop10ByOrderByTimestampDesc();

    List<AuditLog> findTop10ByUserIdOrderByTimestampDesc(Long userId);

    List<AuditLog> findTop10ByActionInOrderByTimestampDesc(List<String> actions);
}
