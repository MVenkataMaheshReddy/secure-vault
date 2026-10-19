package com.securevault.repository;

import com.securevault.entity.Document;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByDeletedFalse();

    Page<Document> findAllByDeletedFalse(Pageable pageable);

    Optional<Document> findByIdAndDeletedFalse(Long id);

    List<Document> findByOwnerIdAndDeletedFalse(Long ownerId);

    List<Document> findByOwnerEmailAndDeletedFalse(String email);

    Page<Document> findByOwnerEmailAndDeletedFalse(String email, Pageable pageable);

    List<Document> findAllByDeletedTrue();

    List<Document> findByOwnerEmailAndDeletedTrue(String email);

    Optional<Document> findByIdAndDeletedTrue(Long id);

    Optional<Document> findByVerificationId(String verificationId);

    boolean existsByVerificationId(String verificationId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT DISTINCT d FROM Document d " +
        "LEFT JOIN d.versions v ON (v.versionNumber = d.currentVersion) " +
        "LEFT JOIN d.tags t " +
        "WHERE d.deleted = false " +
        "AND (:category IS NULL OR LOWER(d.category) = LOWER(:category)) " +
        "AND (:status IS NULL OR d.status = :status) " +
        "AND (:owner IS NULL OR LOWER(d.owner.email) = LOWER(:owner) OR LOWER(d.owner.name) = LOWER(:owner)) " +
        "AND (:tag IS NULL OR LOWER(t) = LOWER(:tag)) " +
        "AND (:fromDate IS NULL OR d.createdAt >= :fromDate) " +
        "AND (:toDate IS NULL OR d.createdAt <= :toDate) " +
        "AND (:q IS NULL OR (" +
        "  LOWER(d.title) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        "  LOWER(d.description) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        "  LOWER(d.originalFileName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        "  LOWER(t) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
        "  (v.ocrText IS NOT NULL AND LOWER(v.ocrText) LIKE LOWER(CONCAT('%', :q, '%')))" +
        "))"
    )
    List<Document> searchDocumentsLike(
        @org.springframework.data.repository.query.Param("q") String q,
        @org.springframework.data.repository.query.Param("category") String category,
        @org.springframework.data.repository.query.Param("status") com.securevault.entity.DocumentStatus status,
        @org.springframework.data.repository.query.Param("owner") String owner,
        @org.springframework.data.repository.query.Param("tag") String tag,
        @org.springframework.data.repository.query.Param("fromDate") java.time.LocalDateTime fromDate,
        @org.springframework.data.repository.query.Param("toDate") java.time.LocalDateTime toDate
    );

    @org.springframework.data.jpa.repository.Query(
        value = "SELECT d.id FROM documents d " +
                "JOIN document_versions v ON v.document_id = d.id AND v.version_number = d.current_version " +
                "WHERE d.deleted = 0 AND (" +
                "MATCH(d.title, d.description) AGAINST (?1 IN BOOLEAN MODE) " +
                "OR MATCH(v.ocr_text) AGAINST (?1 IN BOOLEAN MODE))",
        nativeQuery = true
    )
    List<Object> searchFulltextNative(String query);

    // ==================== PHASE 10: ANALYTICS AGGREGATIONS ====================

    long countByDeletedFalse();

    long countByOwnerIdAndDeletedFalse(Long ownerId);

    long countByStatusAndDeletedFalse(com.securevault.entity.DocumentStatus status);

    long countByStatusAndOwnerIdAndDeletedFalse(com.securevault.entity.DocumentStatus status, Long ownerId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(d) FROM Document d WHERE d.deleted = false AND d.verificationId IS NOT NULL AND d.status = com.securevault.entity.DocumentStatus.APPROVED"
    )
    long countVerifiedDocuments();

    @org.springframework.data.jpa.repository.Query(
        "SELECT COUNT(d) FROM Document d WHERE d.deleted = false AND d.verificationId IS NOT NULL AND d.status = com.securevault.entity.DocumentStatus.APPROVED AND d.owner.id = :ownerId"
    )
    long countVerifiedDocumentsByOwnerId(@org.springframework.data.repository.query.Param("ownerId") Long ownerId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(d.category, 'UNCATEGORIZED'), COUNT(d) FROM Document d WHERE d.deleted = false GROUP BY d.category ORDER BY COUNT(d) DESC"
    )
    List<Object[]> countDocumentsByCategory();

    @org.springframework.data.jpa.repository.Query(
        "SELECT COALESCE(d.category, 'UNCATEGORIZED'), COUNT(d) FROM Document d WHERE d.deleted = false AND d.owner.id = :ownerId GROUP BY d.category ORDER BY COUNT(d) DESC"
    )
    List<Object[]> countDocumentsByCategoryAndOwnerId(@org.springframework.data.repository.query.Param("ownerId") Long ownerId);

    @org.springframework.data.jpa.repository.Query(
        "SELECT YEAR(d.createdAt), MONTH(d.createdAt), COUNT(d) FROM Document d WHERE d.deleted = false AND d.createdAt >= :since GROUP BY YEAR(d.createdAt), MONTH(d.createdAt) ORDER BY YEAR(d.createdAt) ASC, MONTH(d.createdAt) ASC"
    )
    List<Object[]> countUploadsByMonth(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Query(
        "SELECT YEAR(d.createdAt), MONTH(d.createdAt), COUNT(d) FROM Document d WHERE d.deleted = false AND d.owner.id = :ownerId AND d.createdAt >= :since GROUP BY YEAR(d.createdAt), MONTH(d.createdAt) ORDER BY YEAR(d.createdAt) ASC, MONTH(d.createdAt) ASC"
    )
    List<Object[]> countUploadsByMonthAndOwnerId(
        @org.springframework.data.repository.query.Param("ownerId") Long ownerId,
        @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since
    );
}
