package com.securevault.repository;

import com.securevault.entity.VerificationRecord;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VerificationRecordRepository extends JpaRepository<VerificationRecord, Long> {

    Optional<VerificationRecord> findByVerificationId(String verificationId);

    Optional<VerificationRecord> findByDocumentId(Long documentId);

    boolean existsByVerificationId(String verificationId);

    java.util.List<VerificationRecord> findAllByOrderByCreatedAtDesc();
}
