package com.securevault.repository;

import com.securevault.entity.DocumentShare;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentShareRepository extends JpaRepository<DocumentShare, Long> {

    List<DocumentShare> findByDocumentId(Long documentId);

    List<DocumentShare> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    List<DocumentShare> findByDocumentIdAndRevokedFalseOrderByCreatedAtDesc(Long documentId);

    List<DocumentShare> findBySharedWithId(Long sharedWithId);

    Optional<DocumentShare> findByDocumentIdAndSharedWithId(Long documentId, Long sharedWithId);

    @Query("SELECT s FROM DocumentShare s WHERE s.document.id = :documentId AND LOWER(s.sharedWith.email) = LOWER(:email)")
    Optional<DocumentShare> findByDocumentIdAndSharedWithEmail(@Param("documentId") Long documentId, @Param("email") String email);

    @Query("SELECT s FROM DocumentShare s WHERE s.sharedWith.id = :sharedWithId AND s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > :now) AND s.document.deleted = false ORDER BY s.createdAt DESC")
    Page<DocumentShare> findActiveSharesForUser(@Param("sharedWithId") Long sharedWithId, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT s FROM DocumentShare s WHERE s.sharedWith.id = :sharedWithId AND s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > :now) AND s.document.deleted = false")
    List<DocumentShare> findAllActiveSharesForUser(@Param("sharedWithId") Long sharedWithId, @Param("now") LocalDateTime now);

    @Query("SELECT s.document.id FROM DocumentShare s WHERE s.sharedWith.id = :sharedWithId AND s.revoked = false AND (s.expiresAt IS NULL OR s.expiresAt > :now) AND s.document.deleted = false")
    List<Long> findActiveSharedDocumentIdsForUser(@Param("sharedWithId") Long sharedWithId, @Param("now") LocalDateTime now);
}
