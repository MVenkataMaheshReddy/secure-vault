# 🎬 SecureVault — Live Demonstration Script (8–10 Minutes)

**Target Duration:** 8 to 10 Minutes  
**Demonstration Mode:** Live Web Application Presentation  
**Target Audience:** Technical Evaluators, Project Review Committee, Academic Viva Examiners  

---

## 📋 1. Pre-Demo Checklist & Preparation

### 1.1 Accounts Required
Prepare four separate browser windows or browser profiles (e.g., Chrome Normal, Chrome Incognito, Firefox Normal, Edge / Brave) to avoid constant logging out:

| Persona | Role | Credentials | Browser Profile Suggestion |
|---|---|---|---|
| **Alex** | `EMPLOYEE` | `employee@securevault.com` / `Employee@12345` *(Register before demo)* | Window 1: Chrome Standard |
| **Rachel** | `REVIEWER` | `reviewer@securevault.com` / `Reviewer@12345` *(Assigned by Admin)* | Window 2: Chrome Incognito |
| **Victor** | `VERIFIER` | `verifier@securevault.com` / `Verifier@12345` *(Assigned by Admin)* | Window 3: Firefox |
| **Arthur** | `ADMIN` | `admin@securevault.com` / `Admin@12345` *(Default seed account)* | Window 4: Edge / Safari |

### 1.2 Sample Files to Prepare on Desktop
Create a folder on your desktop named `demo_files/` containing:
1. `Employment_Contract_v1.pdf` — A standard readable PDF document with text (e.g. "SecureVault Employment Agreement 2026").
2. `Employment_Contract_v2.pdf` — The same document with an updated salary or revision date.
3. `Spoofed_Script.pdf` — A plain shell script (`#!/bin/bash echo hacked`) renamed with a `.pdf` extension to demonstrate Apache Tika magic-byte rejection.
4. `Tampered_Contract.pdf` — A copy of `Employment_Contract_v1.pdf` where 1 character or byte has been changed (or saved with a different stamp) to demonstrate the cryptographic anti-tamper detection.

### 1.3 Service Pre-Flight Checks
- [ ] Backend running: `cd springapp && mvn spring-boot:run` (Verify `http://localhost:8080/swagger-ui`)
- [ ] Frontend running: `cd reactapp && npm start` (Verify `http://localhost:3000`)
- [ ] Database active: MySQL running on port 3306 with `document_management` database seeded.

---

## ⏱️ 2. Detailed Presentation Flow

```
[0:00 - 1:00]  Act 1: Problem Statement & Architectural Overview
[1:00 - 2:30]  Act 2: Employee Portal — Secure Upload, Magic-Byte Defense & Async OCR
[2:30 - 4:00]  Act 3: Delta Versioning, SHA-256 Fingerprinting & Safe Rollback
[4:00 - 5:15]  Act 4: Reviewer Portal — Approval Queue & Cryptographic QR Issuance
[5:15 - 6:45]  Act 5: Public Verifier Portal — Zero-Knowledge Verification & Anti-Tamper Check
[6:45 - 8:15]  Act 6: Admin Portal — Role Governance, Audit Trails & CSV Compliance Export
[8:15 - 9:30]  Act 7: Search Concurrency Performance, SDG Mapping & Closing
```

---

### Act 1: Problem Statement & Architecture (0:00 – 1:00)

- **Browser View:** Slide or Welcome Landing Page (`http://localhost:3000/login`).
- **Presenter Actions:**
  - Stand in front of the screen or share the full browser window.
  - Show the clean login interface with modern typography and dark accents.
- **Spoken Dialogue:**
  > *"Good morning, esteemed panel. Today, digital records face critical vulnerabilities: unauthorized tampering, credential stuffing, unmanaged version conflicts, and forged paper documents. We engineered **SecureVault**—an enterprise-grade digital locker designed with defense-in-depth security.*
  >
  > *SecureVault is built on Spring Boot 2.7, React 18, and MySQL 8.0. It features stateless HMAC-SHA256 JWT security, Apache Tika magic-byte file validation, asynchronous Tesseract OCR indexing, sequential delta versioning, and zero-knowledge QR code verification.*
  >
  > *Over the next 8 minutes, I will walk you through a complete document lifecycle across our four primary personas: Employee, Reviewer, Verifier, and Administrator."*

---

### Act 2: Employee Portal — Ingestion, Magic-Byte Defense & OCR (1:00 – 2:30)

- **Browser View:** Window 1 (Employee account: `employee@securevault.com`).
- **Presenter Actions:**
  1. Log in as `employee@securevault.com`. Point out the immediate transition to the Employee Dashboard showing personal storage meters.
  2. Click **Upload Document** on the sidebar.
  3. First, drag and drop `Spoofed_Script.pdf` (the fake PDF that is actually a bash script). Click **Upload**.
  4. Observe the immediate validation rejection toast: *"Upload failed: File content does not match declared MIME type"*.
  5. Next, drag and drop `Employment_Contract_v1.pdf`. Enter title *"Executive Employment Agreement"*, Category *"Legal"*, Tags *"HR, Contract, 2026"*. Click **Upload Document**.
  6. The upload succeeds instantly! Navigate to **Documents** to show the new card with status badge `ACTIVE` and version `v1`.
- **Spoken Dialogue:**
  > *"First, let's explore secure document ingestion. Notice what happens when an attacker attempts to bypass file extensions by disguising an executable bash script as a PDF.*
  >
  > *Our backend does not rely on naive file extension strings. Using Apache Tika, it inspects the raw file header magic bytes. The system immediately catches the anomaly and halts the upload with an HTTP 400 Bad Request.*
  >
  > *Now, when uploading our legitimate contract, the file is saved outside the web root under a random UUID. Simultaneously, the backend calculates a SHA-256 fingerprint and initiates an asynchronous Apache Tesseract OCR extraction thread in the background, ensuring zero latency for the user."*

---

### Act 3: Delta Versioning, SHA-256 Fingerprinting & Rollback (2:30 – 4:00)

- **Browser View:** Window 1 (Employee).
- **Presenter Actions:**
  1. Click on the newly uploaded document card to open the Document Details view.
  2. Click the **Upload New Version** button.
  3. Select `Employment_Contract_v2.pdf`, enter description *"Updated compensation section for Q2"*, and submit.
  4. Point out the version badge immediately updates from `v1` to `v2`.
  5. Click the **Version History** button to reveal the chronological version drawer.
  6. Highlight the distinct SHA-256 hash digests for both `v1` and `v2`.
  7. Click **Restore v1**. Notice that the active version updates back to `v1` content, but the version chain creates a non-destructive restoration record `v3 (Restored from v1)`.
- **Spoken Dialogue:**
  > *"In enterprise environments, document revisions frequently lead to accidental overwrites and lost audit trails. SecureVault implements immutable delta versioning.*
  >
  > *When we upload version 2, the original version is never overwritten. Each version maintains its own cryptographic SHA-256 digest and timestamped change log.*
  >
  > *If an erroneous change occurs, the employee can revert with a single click. Notice that SecureVault never destroys historical states; instead, it appends a clean rollback record to ensure complete non-repudiation."*

---

### Act 4: Reviewer Portal — Approval Workflow & QR Issuance (4:00 – 5:15)

- **Browser View:** Window 1 (Employee) -> Window 2 (Reviewer).
- **Presenter Actions:**
  1. In Window 1 (Employee), click **Submit for Review** on the document. The document status transitions to `PENDING_REVIEW`.
  2. Switch to Window 2 (Reviewer: `reviewer@securevault.com`). Log in and navigate to `/approvals`.
  3. Show the pending approval queue. Click **Review** on the *"Executive Employment Agreement"*.
  4. View the document preview and version metadata.
  5. In the Reviewer Comment box, type *"All clauses verified against corporate legal guidelines. Approved."*
  6. Click the green **Approve** button.
  7. Show that the status transitions to `APPROVED`. Point out that a unique `Verification ID` has been generated and a high-contrast ZXing QR code is rendered immediately.
- **Spoken Dialogue:**
  > *"Now let's examine our governance workflow. An employee cannot unilaterally publish an official document. They must submit it to a Reviewer.*
  >
  > *Logging in as Rachel, our Reviewer, we access the dedicated Review Queue. Reviewers inspect document versions, verify compliance, and provide mandatory notes.*
  >
  > *Upon approval, an automated event is triggered: the system generates a globally unique UUID verification key and uses the ZXing cryptographic engine to generate a high-contrast verification QR code. This QR code bridges physical paper and digital integrity."*

---

### Act 5: Public Verifier Portal — Zero-Knowledge Verification (5:15 – 6:45)

- **Browser View:** Window 3 (Firefox / Incognito — Completely Logged Out).
- **Presenter Actions:**
  1. Copy the Verification URL or Verification ID from the Reviewer screen (e.g. `http://localhost:3000/verify/vrf-7f8e...`).
  2. Paste it into an unauthenticated browser window.
  3. Show the **Public Verification Portal**:
     - Green shield icon: *"OFFICIALLY VERIFIED & ACTIVE"*
     - Document Title: *"Executive Employment Agreement"*
     - Issue Date, Issuing Authority, and Recorded SHA-256 Hash.
     - **Point out what is NOT visible:** No owner email, no employee ID, no download button for the private file (Zero-Knowledge proof).
  4. Scroll down to the **Cryptographic Tamper Check** dropzone.
  5. Drag and drop the authentic file `Employment_Contract_v1.pdf`. Click **Verify File Integrity**.
     - Display: *"MATCH CONFIRMED: Document is 100% authentic and unaltered."*
  6. Now, drag and drop `Tampered_Contract.pdf` (where 1 character was altered). Click **Verify File Integrity**.
     - Display: Red Alert banner: *"TAMPER DETECTED: The uploaded file's SHA-256 hash does not match the official record!"*
- **Spoken Dialogue:**
  > *"Here is one of SecureVault's most powerful innovations: Zero-Knowledge Public Verification.*
  >
  > *Anyone who scans the printed QR code arrives at this public endpoint without requiring an account. Notice the strict data privacy: the public can verify the document's authenticity, its issuance date, and its cryptographic fingerprint, but sensitive personal information and raw files are never exposed.*
  >
  > *Furthermore, if an employer or bank receives a physical or digital copy, they can drop it directly into our File Authenticity Checker. When we submit the authentic document, its SHA-256 digest matches identically.*
  >
  > *However, if a malicious actor modifies even a single comma or dollar figure, the mathematical digest alters completely. The system instantly detects the forgery and flags a Tamper Alert."*

---

### Act 6: Admin Portal — Role Governance & Compliance Audit Export (6:45 – 8:15)

- **Browser View:** Window 4 (Admin: `admin@securevault.com`).
- **Presenter Actions:**
  1. Log in as `admin@securevault.com` and navigate to `/admin`.
  2. Show the **User Management Console**: demonstrate toggling account status (enabled/disabled) and assigning roles (`REVIEWER`, `VERIFIER`, `EMPLOYEE`).
  3. Mention the self-demote and self-disable safeguards built into the backend.
  4. Click the **Audit Logs** tab. Show the real-time stream of audit events captured during our demo (Upload, Version Create, Approval, Verification Query).
  5. Filter by Action: `APPROVE`. Show filtered results.
  6. Click **Export to CSV**. Open the downloaded CSV file to show the structured RFC-4180 audit trail with user IDs, IP addresses, entity references, and timestamps.
- **Spoken Dialogue:**
  > *"Now let us switch to our Administrator, Arthur. SecureVault provides comprehensive enterprise governance.*
  >
  > *In the Admin Console, administrators manage user lifecycles and role assignments with built-in safeguards that prevent accidental administrator self-lockout.*
  >
  > *Every single operation performed in SecureVault—every upload, login attempt, approval decision, and file access—generates an immutable audit record. For external auditors and compliance officers (such as ISO 27001 or GDPR audits), administrators can filter audit logs and export the full ledger as an RFC-4180 compliant CSV spreadsheet in one second."*

---

### Act 7: Search Performance, SDG Alignment & Conclusion (8:15 – 9:30)

- **Browser View:** Window 1 (Employee) -> Search Page (`/search`).
- **Presenter Actions:**
  1. Navigate to `/search`.
  2. Type *"Agreement"* or *"Salary"* into the search box.
  3. Point out how results appear instantly as you type (debounced natural language search), matching OCR-extracted text deep inside scanned files.
  4. Highlight the automated test suite results from Phase 12:
     - 232 automated backend unit and integration tests passing.
     - 40 frontend component tests passing.
     - 83.09% JaCoCo line coverage.
     - 50-thread concurrent benchmark achieving **961.54 RPS** with **22ms** median latency.
  5. Conclude with SDG 9 (Infrastructure & Paperless Innovation) and SDG 16 (Anti-Forgery & Transparent Institutions).
- **Spoken Dialogue:**
  > *"To round out the platform, our Full-Text Search Hub queries deep into documents using MySQL FULLTEXT indexes on Tesseract OCR extracted content.*
  >
  > *Our engineering rigor is validated by comprehensive automated testing: 232 backend tests, 40 frontend tests, 83% JaCoCo coverage, and a 50-thread concurrent search benchmark delivering nearly 1,000 requests per second at a 22ms median response time.*
  >
  > *SecureVault directly supports UN Sustainable Development Goal 9 by modernizing paperless digital infrastructure and SDG 16 by eliminating credential forgery and ensuring institutional transparency.*
  >
  > *Thank you. We are now open for your questions."*

---

## 🛡️ 3. Contingency & Backup Plans

Even in a live presentation, unexpected environmental issues can occur. Follow these scripted contingencies:

| Failure Scenario | Immediate Detection | Backup Procedure & Scripted Transition |
|---|---|---|
| **Tesseract OCR is Slow or Fails** | OCR status remains `PENDING` or `FAILED` due to missing language pack or high CPU load. | **What to say:** *"OCR text extraction runs in an asynchronous daemon thread so it never blocks client uploads. While Tesseract finishes parsing the high-resolution scan, let me demonstrate our title and tag search which utilizes our primary InnoDB index."* |
| **Network Latency or Slow Page Load** | Axios request spinner takes longer than 2 seconds. | **What to say:** *"Our frontend Axios interceptor features retry logic and state caching via Redux Toolkit. While this request completes, let's look at the database schema supporting this transaction."* (Quickly switch to ER diagram slide). |
| **Accidental Bad Credentials Entry** | Locked out by `LoginAttemptService` during demo. | **What to say:** *"Notice our dual-keyed brute force defense in action! Because invalid attempts were entered, the IP and email key is automatically locked for 15 minutes. Let me log in with our secondary pre-provisioned Reviewer account to show you how administrators can immediately unlock accounts."* |
| **Browser Camera or QR Scanner Glitch** | Laptop webcam permission denied or camera blur prevents QR scanning. | **Backup Action:** Do not fumble with camera settings. Directly click the **Verification URL** link rendered below the QR code card or copy the 8-character Verification Code into the manual input box. |
| **File Tamper Demo Mismatch Error** | The unmodified file accidentally triggers a mismatch due to OS metadata modification. | **Backup Action:** Use the exact original file freshly downloaded from the SecureVault UI download button (`/api/documents/{id}/download`), which guarantees byte-for-byte fidelity with the stored version. |

---

*Demo script validated on SecureVault v1.0.0.*
