package com.securevault.repository;

import com.securevault.entity.DocumentVersion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNumberDesc(Long documentId);

    Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, Integer versionNumber);

    Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(Long documentId);

    List<DocumentVersion> findByOcrStatusAndCreatedAtBefore(com.securevault.entity.OcrStatus ocrStatus, java.time.LocalDateTime dateTime);
 
    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(SUM(v.fileSize), 0) FROM DocumentVersion v WHERE v.document.deleted = false"
    )
    long sumStorageUsedBytes();

    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(SUM(v.fileSize), 0) FROM DocumentVersion v WHERE v.document.deleted = false AND v.document.owner.id = :ownerId"
    )
    long sumStorageUsedBytesByOwnerId(@org.springframework.data.repository.query.Param("ownerId") Long ownerId);
}
