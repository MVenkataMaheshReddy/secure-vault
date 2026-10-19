package com.securevault.repository;

import com.securevault.entity.Approval;
import com.securevault.entity.ApprovalStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApprovalRepository extends JpaRepository<Approval, Long> {

    List<Approval> findByDocumentId(Long documentId);

    List<Approval> findByReviewerId(Long reviewerId);

    Page<Approval> findByStatus(ApprovalStatus status, Pageable pageable);

    List<Approval> findByDocumentIdOrderByCreatedAtDesc(Long documentId);

    Optional<Approval> findFirstByDocumentIdAndStatus(Long documentId, ApprovalStatus status);

    Optional<Approval> findFirstByDocumentIdOrderByCreatedAtDesc(Long documentId);

    boolean existsByDocumentIdAndStatus(Long documentId, ApprovalStatus status);

    long countByStatus(ApprovalStatus status);

    long countByReviewerIdAndStatus(Long reviewerId, ApprovalStatus status);

    long countByDocumentOwnerIdAndStatus(Long ownerId, ApprovalStatus status);

    @org.springframework.data.jpa.repository.Query(
        "SELECT YEAR(a.updatedAt), MONTH(a.updatedAt), a.status, COUNT(a) FROM Approval a " +
        "WHERE a.status IN (com.securevault.entity.ApprovalStatus.APPROVED, com.securevault.entity.ApprovalStatus.REJECTED) " +
        "AND a.updatedAt >= :since " +
        "GROUP BY YEAR(a.updatedAt), MONTH(a.updatedAt), a.status " +
        "ORDER BY YEAR(a.updatedAt) ASC, MONTH(a.updatedAt) ASC"
    )
    List<Object[]> countApprovalsByMonth(@org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since);

    @org.springframework.data.jpa.repository.Query(
        "SELECT YEAR(a.updatedAt), MONTH(a.updatedAt), a.status, COUNT(a) FROM Approval a " +
        "WHERE a.reviewer.id = :reviewerId " +
        "AND a.status IN (com.securevault.entity.ApprovalStatus.APPROVED, com.securevault.entity.ApprovalStatus.REJECTED) " +
        "AND a.updatedAt >= :since " +
        "GROUP BY YEAR(a.updatedAt), MONTH(a.updatedAt), a.status " +
        "ORDER BY YEAR(a.updatedAt) ASC, MONTH(a.updatedAt) ASC"
    )
    List<Object[]> countApprovalsByMonthAndReviewerId(
        @org.springframework.data.repository.query.Param("reviewerId") Long reviewerId,
        @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since
    );

    @org.springframework.data.jpa.repository.Query(
        "SELECT YEAR(a.updatedAt), MONTH(a.updatedAt), a.status, COUNT(a) FROM Approval a " +
        "WHERE a.document.owner.id = :ownerId " +
        "AND a.status IN (com.securevault.entity.ApprovalStatus.APPROVED, com.securevault.entity.ApprovalStatus.REJECTED) " +
        "AND a.updatedAt >= :since " +
        "GROUP BY YEAR(a.updatedAt), MONTH(a.updatedAt), a.status " +
        "ORDER BY YEAR(a.updatedAt) ASC, MONTH(a.updatedAt) ASC"
    )
    List<Object[]> countApprovalsByMonthAndOwnerId(
        @org.springframework.data.repository.query.Param("ownerId") Long ownerId,
        @org.springframework.data.repository.query.Param("since") java.time.LocalDateTime since
    );
}
