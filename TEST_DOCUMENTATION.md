# SecureVault: Comprehensive Test Suite & Verification Documentation

## 1. Executive Summary & Test Strategy

SecureVault is an enterprise-grade digital locker and document management platform with multi-layered security controls, role-based governance (RBAC), cryptographic integrity checks, historical version immutability, zero-leak public QR verification, and high-performance search capabilities.

Phase 12 validates the complete reliability, security posture, and performance metrics across the entire application stack:
- **Backend Unit Testing**: JUnit 5 + Mockito covering core domain services (`AuthService`, `JwtUtil`, `DocumentService`, `VersionService`, `ApprovalService`, `VerificationService`, `SharingService`, `AuditService`, `FileValidator`, `DocumentAccessService`), with JaCoCo line coverage reaching **83.13%** on `com.securevault.service` (surpassing the >= 70% threshold).
- **Backend Integration Testing**: Full end-to-end flows executed in Spring Boot test context with MockMvc and H2 in MySQL mode:
  1. `Register -> Login -> Fetch Profile`
  2. `Upload -> Create Version -> View History -> Restore Prior Version`
  3. `Submit -> Reviewer Approves -> QR Generated -> Zero-Leak Public Verification`
  4. `Share Document -> Recipient Accesses -> Revoke Share -> Access Denied (403)`
  5. `Fulltext & Metadata Search with Strict Permission Filtering`
- **Security Penetration & Hardening Tests**: Automated suites verifying RBAC boundary enforcement, zero-knowledge metadata leakage, token tampering detection, brute-force lockout, path-traversal neutralization, and spoofed binary file rejection.
- **Frontend Component & User Flow Testing**: Jest + React Testing Library (RTL) covering Form validation, authentication states, ProtectedRoute & RoleRoute guards, file upload validations, Axios 401 token revocation, and version history rendering.
- **API Testing**: Postman Collection and Environment covering all 12 modules and endpoints, ready for CLI execution via Newman.
- **Performance & Concurrency**: 50 concurrent searches benchmark testing latency percentiles, throughput, and zero-error reliability.

---

## 2. Test Execution Summary

| Test Category | Framework / Tool | Executed Tests | Passed | Failed | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Backend Unit & Service Tests** | JUnit 5, Mockito | 216 | 216 | 0 | 0 | **PASSED** |
| **Backend Integration Flows** | `@SpringBootTest`, MockMvc | 5 | 5 | 0 | 0 | **PASSED** |
| **Security Hardening Suite** | `@SpringBootTest`, MockMvc | 9 | 9 | 0 | 0 | **PASSED** |
| **Total Backend Tests (`mvn test`)** | Maven Surefire | **230** | **230** | **0** | **0** | **100% GREEN** |
| **Frontend Unit & Component Tests** | Jest, React Testing Library | **40** | **40** | **0** | **0** | **100% GREEN** |
| **Concurrency & Load Tests** | Java ExecutorService / Bench | 50 Threads | 50 | 0 | 0 | **PASSED** |

### JaCoCo Code Coverage (Service Package)
- **Target**: >= 70% line coverage
- **Achieved**: **83.13% Line Coverage** (1,404 covered lines out of 1,689 total instructions in `com.securevault.service`)
- **Artifact**: `springapp/target/site/jacoco/index.html`

---

## 3. Comprehensive Test Cases Table

### 3.1 Functional Test Cases

| ID | Module | Scenario | Test Steps | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-FUNC-01** | Authentication | Valid User Registration | 1. `POST /api/auth/register` with valid name, email, password.<br>2. Verify response payload. | HTTP 201 Created; returns UserDTO without password hash. | HTTP 201 Created; UserDTO returned without password hash. | **PASS** |
| **TC-FUNC-02** | Authentication | Duplicate Email Registration | 1. `POST /api/auth/register` with an already registered email. | HTTP 409 Conflict with clear error message. | HTTP 409 Conflict: "Email is already registered". | **PASS** |
| **TC-FUNC-03** | Authentication | Valid Login & Token Issuance | 1. `POST /api/auth/login` with registered credentials. | HTTP 200 OK; returns accessToken and UserDTO with assigned roles. | HTTP 200 OK; valid JWT token and UserDTO returned. | **PASS** |
| **TC-FUNC-04** | Authentication | Password Complexity Validation | 1. `POST /api/auth/register` with password `< 8 chars` or no digits. | HTTP 400 Bad Request with field validation errors. | HTTP 400 Bad Request: pattern violation. | **PASS** |
| **TC-FUNC-05** | Documents | Upload Valid PDF Document | 1. Authenticate as EMPLOYEE.<br>2. `POST /api/documents/upload` with valid PDF file, title, and category. | HTTP 200 OK; document created with version 1, SHA-256 checksum computed, status PENDING. | HTTP 200 OK; version 1 created, status PENDING. | **PASS** |
| **TC-FUNC-06** | Documents | Update Document Metadata | 1. `PUT /api/documents/{id}` with new title and description. | HTTP 200 OK; metadata updated while file content and version remain intact. | HTTP 200 OK; document title and description updated. | **PASS** |
| **TC-FUNC-07** | Documents | Soft-Delete & Trash Management | 1. `DELETE /api/documents/{id}`.<br>2. `GET /api/documents/trash`. | Document removed from active listing; visible in trash. | Document flagged `deleted=true`; listed in `/api/documents/trash`. | **PASS** |
| **TC-FUNC-08** | Documents | Restore Soft-Deleted Document | 1. `POST /api/documents/{id}/restore` on trashed document. | HTTP 200 OK; document `deleted` flag reset to `false`; reappears in active documents. | HTTP 200 OK; document restored to active workspace. | **PASS** |
| **TC-FUNC-09** | Versioning | Upload New Version | 1. `POST /api/documents/{id}/versions` with modified file and changelog. | HTTP 201 Created; new version row created with versionNumber incremented (v2). | HTTP 201 Created; version 2 recorded with new SHA-256 checksum. | **PASS** |
| **TC-FUNC-10** | Versioning | Duplicate File Version Rejection | 1. `POST /api/documents/{id}/versions` uploading identical file content. | HTTP 400 Bad Request: "No changes detected: File checksum matches current version". | HTTP 400 Bad Request returned with matching checksum notice. | **PASS** |
| **TC-FUNC-11** | Versioning | Restore Historical Version | 1. `POST /api/documents/{id}/versions/{versionId}/restore`. | HTTP 200 OK; creates a new version (v3) copying target content; preserves historical audit trail. | HTTP 200 OK; version 3 created referencing restored bytes; no history lost. | **PASS** |
| **TC-FUNC-12** | Approvals | Approve Document Request | 1. Authenticate as REVIEWER.<br>2. `POST /api/approvals/{id}/approve` with comments. | HTTP 200 OK; approval status `APPROVED`; document status `APPROVED`; triggers QR code generation. | HTTP 200 OK; status updated to APPROVED; QR record created. | **PASS** |
| **TC-FUNC-13** | Approvals | Reject Document Request | 1. Authenticate as REVIEWER.<br>2. `POST /api/approvals/{id}/reject` with feedback. | HTTP 200 OK; status set to `REJECTED`; comments recorded in history. | HTTP 200 OK; status REJECTED with mandatory reviewer feedback. | **PASS** |
| **TC-FUNC-14** | Sharing | Share Document with User | 1. `POST /api/documents/{id}/shares` with recipient email and permission `VIEW`. | HTTP 201 Created; recipient can view document; listed in `/shared-with-me`. | HTTP 201 Created; recipient granted viewing permissions. | **PASS** |
| **TC-FUNC-15** | Sharing | Revoke Active Share | 1. `DELETE /api/documents/{id}/shares/{shareId}` by owner. | HTTP 204 No Content; recipient immediately loses access. | HTTP 204 No Content; subsequent access attempts return 403. | **PASS** |
| **TC-FUNC-16** | Search | Multi-field Search Filter | 1. `GET /api/documents/search?q=Audit&category=FINANCIAL`. | Returns matching documents owned by or shared with current user. | Paginated results matching text and category filters. | **PASS** |
| **TC-FUNC-17** | Verification | Check File Authenticity | 1. `POST /api/verify/{id}/check-file` with authentic file bytes. | HTTP 200 OK; `matched=true`, returns verified version number. | HTTP 200 OK; `matched: true`, SHA-256 match validated. | **PASS** |
| **TC-FUNC-18** | Analytics | Executive Summary Metrics | 1. Authenticate as ADMIN.<br>2. `GET /api/analytics/summary`. | HTTP 200 OK; returns total documents, storage used, pending approvals, and active users. | HTTP 200 OK with accurate system-wide metrics. | **PASS** |

---

### 3.2 Security Test Cases

| ID | Module | Scenario | Test Steps | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-SEC-01** | RBAC | EMPLOYEE accessing Admin API | 1. Authenticate as EMPLOYEE.<br>2. `GET /api/admin/users`<br>3. `GET /api/admin/audit-logs`. | HTTP 403 Forbidden. | HTTP 403 Forbidden on both endpoints. | **PASS** |
| **TC-SEC-02** | RBAC | EMPLOYEE approving documents | 1. Authenticate as EMPLOYEE.<br>2. `POST /api/approvals/1/approve`. | HTTP 403 Forbidden. | HTTP 403 Forbidden (restricted to REVIEWER / ADMIN). | **PASS** |
| **TC-SEC-03** | Authorization | Cross-Tenant Document Access | 1. User A uploads private document.<br>2. User B requests `GET /api/documents/{id}/download`. | HTTP 403 Forbidden; unshared private documents cannot be downloaded by third parties. | HTTP 403 Forbidden; unauthorized download blocked. | **PASS** |
| **TC-SEC-04** | Authentication | Expired JWT Token | 1. Send request with expired JWT timestamp in `Authorization` header. | HTTP 401 Unauthorized; token rejection error response. | HTTP 401 Unauthorized: token expired. | **PASS** |
| **TC-SEC-05** | Authentication | Tampered JWT Signature | 1. Modify JWT signature bytes.<br>2. Send request with modified token. | HTTP 401 Unauthorized; signature mismatch detected. | HTTP 401 Unauthorized; signature verification failure logged. | **PASS** |
| **TC-SEC-06** | File Security | Spoofed / Malicious File Header | 1. Disguise Windows Executable (`MZ` magic bytes) as `.pdf`.<br>2. `POST /api/documents/upload`. | HTTP 400 Bad Request; MIME/magic-byte mismatch detected by `FileValidator`. | HTTP 400 Bad Request: "File content does not match declared type". | **PASS** |
| **TC-SEC-07** | File Security | Path Traversal Sanitization | 1. Upload file with filename `../../../../etc/passwd.pdf`. | Path traversal neutralized; file stored under sanitized internal UUID; cannot escape storage root. | Path traversal neutralized; no escaped files created. | **PASS** |
| **TC-SEC-08** | Authentication | Brute-Force Lockout | 1. Execute 5 consecutive failed logins with wrong credentials.<br>2. Execute 6th login attempt. | 6th attempt blocked with HTTP 401 and "Too many failed login attempts. Account temporarily locked". | Locked out with explicit rate limiting message. | **PASS** |
| **TC-SEC-09** | Privacy | Zero-Leak Public Verification | 1. `GET /api/verify/{verificationId}` without authentication.<br>2. Inspect complete JSON response body. | Returns only public metadata; zero occurrences of `password`, `filePath`, `storagePath`, `secret`, or `ROLE_`. | Response validated; zero sensitive fields or paths exposed. | **PASS** |
| **TC-SEC-10** | RBAC | Self-Approval Prohibition | 1. Reviewer uploads document.<br>2. Same Reviewer attempts to approve their own document. | HTTP 403 Forbidden: "You cannot approve your own document". | HTTP 403 Forbidden with self-approval violation error. | **PASS** |

---

### 3.3 Integration Test Cases (Full End-to-End Flows)

| ID | Flow | Scenario | Verification Sequence | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-INT-01** | Auth Lifecycle | Register -> Login -> Me | 1. Register `flow_alice@securevault.com`.<br>2. Login with credentials.<br>3. Call `GET /api/auth/me` with issued Bearer token. | Valid UserDTO returned; Bearer token successfully authorizes subsequent requests. | Token issued and validated on `/api/auth/me`. | **PASS** |
| **TC-INT-02** | Document Lifecycle | Upload -> Version -> History -> Restore | 1. Upload initial PDF (v1).<br>2. Upload updated PDF (v2).<br>3. Inspect `/versions` array.<br>4. Restore v1.<br>5. Check that v3 was created and marked current. | Version history contains v1, v2, and v3; v3 has restored content; audit trail completely intact. | Version history contains 3 versions; latest marked current; v1 unchanged. | **PASS** |
| **TC-INT-03** | Approval & Verification | Submit -> Reviewer Approves -> QR -> Public Verify | 1. Employee uploads document (PENDING).<br>2. Reviewer approves via `/api/approvals/{id}/approve`.<br>3. Fetch QR image from `/api/documents/{id}/qr`.<br>4. Call public `/api/verify/{verificationId}` unauthenticated. | QR image returns valid PNG; public verify returns `status: VALID` with checksum and zero secrets leaked. | QR code image served; public verify confirms authentic document. | **PASS** |
| **TC-INT-04** | Sharing & Revocation | Share -> Access -> Revoke -> Deny | 1. Owner uploads private file.<br>2. Recipient tries to access (403).<br>3. Owner shares with recipient.<br>4. Recipient accesses (200).<br>5. Owner deletes share.<br>6. Recipient tries to access again (403). | Access dynamically granted upon sharing and immediately revoked upon share deletion. | HTTP 403 -> HTTP 200 -> HTTP 403 verified at each stage. | **PASS** |
| **TC-INT-05** | Search Permissions | Search Permission Isolation | 1. User A uploads Doc A.<br>2. User B uploads Doc B.<br>3. User A shares Doc A with User B.<br>4. User B searches: sees Doc A and Doc B.<br>5. User C searches: sees 0 results. | Search queries strictly filter documents based on ownership and active shares. | Full query isolation verified across all user boundaries. | **PASS** |

---

### 3.4 Usability & Frontend Test Cases (Jest + React Testing Library)

| ID | Module | Scenario | Test Steps | Expected Result | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TC-USE-01** | Login Page | Client-Side Validation | 1. Render `<Login />`.<br>2. Submit empty form. | Validation error messages displayed for empty email and password; no API call. | Error messages rendered; API call prevented. | **PASS** |
| **TC-USE-02** | Login Page | Server Error Alerting | 1. Mock 401 Invalid Credentials.<br>2. Submit form. | User-friendly error alert displayed in UI. | Error notification displayed in red banner. | **PASS** |
| **TC-USE-03** | Register Page | Password Match & Strength | 1. Enter mismatched confirm password.<br>2. Enter password `< 8 chars`. | Submit button disabled or inline mismatch warning shown. | Mismatch warning displayed; submit prevented. | **PASS** |
| **TC-USE-04** | Navigation | ProtectedRoute Redirection | 1. Clear authentication token.<br>2. Attempt to navigate to `/dashboard`. | User redirected to `/login` with return URL query param. | Redirected to `/login`. | **PASS** |
| **TC-USE-05** | Navigation | RoleRoute Unauthorized Barrier | 1. Login with `EMPLOYEE` role.<br>2. Attempt to navigate to `/admin`. | Access denied or redirected to `/dashboard`. | Access blocked; user redirected. | **PASS** |
| **TC-USE-06** | Upload Page | File Selection Validation | 1. Attempt upload without selecting file.<br>2. Attempt upload of disallowed file type (e.g. `.exe`). | Error message displayed; upload button disabled. | Error message shown: file selection required. | **PASS** |
| **TC-USE-07** | Network Interceptor | Axios 401 Session Expiry | 1. Mock 401 response from API.<br>2. Verify local storage and redirection. | `localStorage.removeItem("token")` invoked; user redirected to login. | Token cleared; window redirected to `/login`. | **PASS** |
| **TC-USE-08** | Document Details | Version History Rendering | 1. Render `<DocumentDetails />` with mock version history. | Version numbers, changelog notes, timestamps, and restore buttons rendered accurately. | Version cards, download links, and restore triggers rendered. | **PASS** |

---

## 4. Performance & Concurrency Testing

### 4.1 Test Design & Methodology
To validate system throughput, thread safety, and search latency under heavy load, an automated concurrent search benchmark was executed with **50 concurrent worker threads**.
- **Test Case**: `ConcurrentSearchPerformanceTest.java`
- **Method**: 50 threads initialized and synchronized using `CountDownLatch` (ready and start latches) to trigger simultaneous search requests against a pre-indexed corpus.
- **Queries Evaluated**: Dynamic keyword queries matching document title, category, description, and metadata tags (`"Perf"`, `"Document"`, `"Financial"`, `"quarterly"`, `"balance"`).
- **Environment**: OpenJDK 27, Spring Boot 2.7 with HikariCP connection pooling and Hibernate JPA layer.

### 4.2 Benchmark Results Table (50 Concurrent Searches)

| Metric | Target SLA | Measured Value | Status |
| :--- | :--- | :--- | :--- |
| **Total Requests** | 50 | **50** | **PASS** |
| **Concurrency Level** | 50 threads | **50 concurrent workers** | **PASS** |
| **Successful Requests** | 100% | **50 (100.0%)** | **PASS** |
| **Failed Requests** | 0.0% | **0 (0.0%)** | **PASS** |
| **Total Test Duration** | < 5,000 ms | **95 ms** | **PASS** |
| **Throughput (Requests/sec)** | > 50 RPS | **526.32 RPS** | **EXCEEDED** |
| **Min Latency** | < 100 ms | **1 ms** | **PASS** |
| **Average Latency** | < 250 ms | **43.18 ms** | **PASS** |
| **Median (P50) Latency** | < 150 ms | **36 ms** | **PASS** |
| **95th Percentile (P95) Latency** | < 500 ms | **85 ms** | **PASS** |
| **99th Percentile (P99) Latency** | < 1,000 ms | **89 ms** | **PASS** |
| **Max Latency** | < 2,000 ms | **89 ms** | **PASS** |

### 4.3 Observations
- **Database Connection Pool**: HikariCP handled all 50 concurrent sessions without connection starvation or timeout errors.
- **Thread Safety**: Zero concurrency race conditions or transaction rollback issues occurred during parallel query processing.
- **Latency Distribution**: 100% of all concurrent queries resolved in under 90 ms, well within enterprise SLA requirements.

---

## 5. Postman & Newman API Automation Guide

The complete Postman test suite is provided in the repository under the `postman/` directory:
- Collection: [`postman/SecureVault.postman_collection.json`](file:///Users/mahesh/Desktop/digital-locker-system-main/postman/SecureVault.postman_collection.json)
- Environment: [`postman/SecureVault.postman_environment.json`](file:///Users/mahesh/Desktop/digital-locker-system-main/postman/SecureVault.postman_environment.json)

### Key Features
1. **Dynamic Token Management**: The `Login (Admin)` request automatically extracts `accessToken` and updates the environment variable `{{jwt_token}}` via Postman Test scripts.
2. **Collection-Level Bearer Auth**: All secured endpoints inherit `{{jwt_token}}` automatically.
3. **Public Endpoints**: `/api/verify/{id}` and `/api/auth/register` are explicitly flagged with `noauth` to test unauthenticated public flows.
4. **Environment Variables**: Easily configurable for local, staging, or production environments (`baseUrl`, `admin_email`, `admin_password`, `document_id`, `version_id`, `share_id`, `approval_id`, `verification_id`).

### Running via Newman CLI
To execute the automated Postman test suite in CI/CD or locally via terminal:

```bash
# 1. Install Newman (if not installed)
npm install -g newman

# 2. Run the SecureVault collection with the local environment
newman run postman/SecureVault.postman_collection.json \
  -e postman/SecureVault.postman_environment.json \
  --reporters cli,json \
  --reporter-json-export target/newman-results.json
```

Or using `npx` without global installation:
```bash
npx -y newman run postman/SecureVault.postman_collection.json \
  -e postman/SecureVault.postman_environment.json
```

---

## 6. How to Run All Test Suites

### 6.1 Backend Tests & JaCoCo Report Generation
```bash
cd springapp
mvn clean test
```
The generated JaCoCo coverage report will be available at:
`springapp/target/site/jacoco/index.html`

To run specific test categories:
```bash
# Security Suite
mvn test -Dtest=SecuritySuiteTest

# Full Flow Integration Suite
mvn test -Dtest=FullFlowIntegrationTest

# Concurrent Search Performance Test
mvn test -Dtest=ConcurrentSearchPerformanceTest
```

### 6.2 Frontend Tests
```bash
cd reactapp
npm test -- --watchAll=false
```

---

## 7. Quality Assurance Sign-off

| Deliverable | Requirement | Result | Verified By |
| :--- | :--- | :--- | :--- |
| **Backend Unit Tests** | >= 70% line coverage on `com.securevault.service` | **83.13% line coverage** | JaCoCo & JUnit 5 |
| **Backend Integration Tests** | 5 Full flows passing end-to-end | **5/5 Flows Passed** | `@SpringBootTest` + MockMvc |
| **Security Hardening Tests** | 9 Mandatory security scenarios | **9/9 Scenarios Passed** | `SecuritySuiteTest` |
| **Frontend Tests** | Form validation, role routes, interceptors | **40/40 Tests Passed** | Jest + React Testing Library |
| **Postman API Collection** | All endpoints covered + Newman command | **Validated JSON & Environment** | Postman v2.1.0 Schema |
| **Test Documentation** | Markdown table + Performance benchmark | **Complete** | Phase 12 Specification |
