-- =======================================================================
-- SecureVault Database Schema Definition (Phase 1)
-- Target System: MySQL 8.0+
-- =======================================================================

CREATE DATABASE IF NOT EXISTS document_management;
USE document_management;

-- 1. Roles Table
CREATE TABLE IF NOT EXISTS roles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed initial 4 roles
INSERT IGNORE INTO roles (id, name) VALUES
(1, 'ADMIN'),
(2, 'EMPLOYEE'),
(3, 'REVIEWER'),
(4, 'VERIFIER');

-- 2. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. User Roles (Many-to-Many Join Table)
CREATE TABLE IF NOT EXISTS user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Documents Table
CREATE TABLE IF NOT EXISTS documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    category VARCHAR(100) DEFAULT 'GENERAL',
    description TEXT,
    owner_id BIGINT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'ACTIVE') NOT NULL DEFAULT 'ACTIVE',
    current_version INT NOT NULL DEFAULT 1,
    verification_id VARCHAR(100) NULL UNIQUE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_documents_owner (owner_id),
    INDEX idx_documents_status (status),
    INDEX idx_documents_verification (verification_id),
    FULLTEXT INDEX idx_doc_fulltext (title, description),
    CONSTRAINT fk_documents_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. Document Versions Table
CREATE TABLE IF NOT EXISTS document_versions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    version_number INT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT NOT NULL,
    content_type VARCHAR(150) NOT NULL,
    checksum VARCHAR(64) NOT NULL, -- SHA-256
    uploaded_by BIGINT NOT NULL,
    change_description VARCHAR(255),
    ocr_text LONGTEXT NULL,
    ocr_status ENUM('PENDING', 'DONE', 'FAILED', 'SKIPPED') NOT NULL DEFAULT 'PENDING',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_doc_version (document_id, version_number),
    INDEX idx_doc_version_doc (document_id),
    INDEX idx_doc_version_uploader (uploaded_by),
    FULLTEXT INDEX idx_version_ocr_fulltext (ocr_text),
    CONSTRAINT fk_doc_version_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_doc_version_uploader FOREIGN KEY (uploaded_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. Document Tags Table
CREATE TABLE IF NOT EXISTS document_tags (
    document_id BIGINT NOT NULL,
    tag VARCHAR(50) NOT NULL,
    PRIMARY KEY (document_id, tag),
    INDEX idx_document_tags_tag (tag),
    CONSTRAINT fk_document_tags_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. Approvals Table
CREATE TABLE IF NOT EXISTS approvals (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    comments TEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_approvals_doc (document_id),
    INDEX idx_approvals_reviewer (reviewer_id),
    CONSTRAINT fk_approvals_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_approvals_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. Document Shares Table
CREATE TABLE IF NOT EXISTS document_shares (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    shared_by BIGINT NOT NULL,
    shared_with BIGINT NOT NULL,
    permission ENUM('VIEW', 'DOWNLOAD') NOT NULL DEFAULT 'VIEW',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE KEY uk_doc_shared_with (document_id, shared_with),
    INDEX idx_shares_doc (document_id),
    INDEX idx_shares_recipient (shared_with),
    CONSTRAINT fk_shares_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE,
    CONSTRAINT fk_shares_sender FOREIGN KEY (shared_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_shares_recipient FOREIGN KEY (shared_with) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT NULL,
    ip_address VARCHAR(50) NULL,
    details TEXT,
    timestamp DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_timestamp (timestamp),
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. Verification Records Table
CREATE TABLE IF NOT EXISTS verification_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    verification_id VARCHAR(100) NOT NULL UNIQUE,
    qr_path VARCHAR(500) NULL,
    status ENUM('VALID', 'REVOKED') NOT NULL DEFAULT 'VALID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_verification_id (verification_id),
    CONSTRAINT fk_verification_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. Legacy Support: Folders Table
CREATE TABLE IF NOT EXISTS folder (
    fol_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    fol_name VARCHAR(255) NOT NULL,
    owner_id BIGINT NULL,
    created_at DATE NULL,
    description VARCHAR(255) NULL,
    CONSTRAINT fk_folder_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. Security Support: Revoked Token Table
CREATE TABLE IF NOT EXISTS revoked_token (
    token VARCHAR(500) PRIMARY KEY,
    revoked_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
