package com.securevault.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.LoginRequest;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.DocumentVersion;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.entity.VerificationRecord;
import com.securevault.entity.VerificationStatus;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.DocumentVersionRepository;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.repository.VerificationRecordRepository;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("SecuritySuiteTest: Phase 12 Security Hardening Verification Tests")
public class SecuritySuiteTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private VerificationRecordRepository verificationRecordRepository;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private ObjectMapper objectMapper;

    private User employeeUser;
    private User victimUser;
    private String employeeToken;
    private Document victimDoc;
    private VerificationRecord publicRecord;

    @BeforeEach
    void setUp() {
        // Create or fetch EMPLOYEE role
        Role empRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.EMPLOYEE)));

        // Create Employee User
        employeeUser = userRepository.findByEmail("emp_security_test@securevault.com")
                .orElseGet(() -> {
                    User u = new User("Emp Security", "emp_security_test@securevault.com", "hash123");
                    u.setEnabled(true);
                    u.setRoles(new HashSet<>(Collections.singletonList(empRole)));
                    return userRepository.save(u);
                });
        employeeToken = jwtUtil.generateToken(employeeUser);

        // Create Victim User (owner of private document)
        victimUser = userRepository.findByEmail("victim_security_test@securevault.com")
                .orElseGet(() -> {
                    User u = new User("Victim User", "victim_security_test@securevault.com", "hash123");
                    u.setEnabled(true);
                    u.setRoles(new HashSet<>(Collections.singletonList(empRole)));
                    return userRepository.save(u);
                });

        // Create private document owned by victimUser
        victimDoc = new Document("Confidential Financials", "confidential.pdf", "FINANCIAL", "Private records", victimUser);
        victimDoc.setStatus(DocumentStatus.PENDING);
        victimDoc = documentRepository.save(victimDoc);

        // Version for victim doc
        Path dummyPath = Paths.get("target/test-storage/documents/dummy_victim.pdf");
        try {
            Files.createDirectories(dummyPath.getParent());
            Files.writeString(dummyPath, "%PDF-1.5 test content");
        } catch (Exception ignored) {}

        DocumentVersion version = new DocumentVersion(
                victimDoc,
                1,
                dummyPath.toString(),
                100L,
                "application/pdf",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                victimUser,
                "Initial upload"
        );
        documentVersionRepository.save(version);

        // Public verification record for privacy leakage test
        publicRecord = verificationRecordRepository.findByVerificationId("SV-SEC-TEST-999")
                .orElseGet(() -> {
                    VerificationRecord vr = new VerificationRecord(
                            victimDoc,
                            "SV-SEC-TEST-999",
                            "target/test-storage/qr/dummy.png",
                            VerificationStatus.VALID
                    );
                    return verificationRecordRepository.save(vr);
                });
    }

    // =========================================================================
    // 1. EMPLOYEE -> Admin API = 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("Security 1: EMPLOYEE role accessing Admin API returns 403 Forbidden")
    void testEmployeeAccessAdminApi_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 2. EMPLOYEE approves document = 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("Security 2: EMPLOYEE role attempting to approve document returns 403 Forbidden")
    void testEmployeeApproveDocument_Returns403() throws Exception {
        ApprovalDecisionRequest request = new ApprovalDecisionRequest();
        request.setComments("Unauthorized approval attempt");

        mockMvc.perform(post("/api/approvals/1/approve")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/approvals/1/reject")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 3. User A downloads User B's private doc = 403 Forbidden
    // =========================================================================
    @Test
    @DisplayName("Security 3: User A attempting to download User B's private unshared doc returns 403 Forbidden")
    void testUserADownloadsUserBPrivateDoc_Returns403() throws Exception {
        mockMvc.perform(get("/api/documents/" + victimDoc.getId() + "/download")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // 4. Expired JWT = 401 Unauthorized
    // =========================================================================
    @Test
    @DisplayName("Security 4: Request with expired JWT token returns 401 Unauthorized")
    void testExpiredJwt_Returns401() throws Exception {
        JwtUtil expiredJwtUtil = new JwtUtil(
                "0123456789012345678901234567890123456789012345678901234567890123",
                -10000L // Expired 10 seconds ago
        );
        String expiredToken = expiredJwtUtil.generateToken(employeeUser);

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 5. Tampered JWT = 401 Unauthorized
    // =========================================================================
    @Test
    @DisplayName("Security 5: Request with tampered JWT signature returns 401 Unauthorized")
    void testTamperedJwt_Returns401() throws Exception {
        String[] parts = employeeToken.split("\\.");
        assertEquals(3, parts.length);
        String tamperedToken = parts[0] + "." + parts[1] + ".invalidTamperedSignature123";

        mockMvc.perform(get("/api/documents")
                        .header("Authorization", "Bearer " + tamperedToken))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 6. Invalid / Spoofed File Upload = 400 Bad Request
    // =========================================================================
    @Test
    @DisplayName("Security 6: Uploading spoofed executable file disguised as PDF returns 400 Bad Request")
    void testInvalidOrSpoofedFileUpload_Returns400() throws Exception {
        // Disguised DOS/Windows Executable header 'MZ' disguised with .pdf filename
        byte[] spoofedExe = new byte[]{'M', 'Z', (byte) 0x90, 0x00, 0x03, 0x00};
        MockMultipartFile spoofedFile = new MockMultipartFile(
                "file", "malware.pdf", "application/pdf", spoofedExe
        );

        mockMvc.perform(multipart("/api/documents/upload")
                        .file(spoofedFile)
                        .param("title", "Malicious Document")
                        .param("category", "GENERAL")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 7. Path-Traversal Filename is Neutralized
    // =========================================================================
    @Test
    @DisplayName("Security 7: Path traversal in filename is rejected or neutralized without escaping base dir")
    void testPathTraversalFilenameIsNeutralized() throws Exception {
        byte[] validPdf = "%PDF-1.5\n%\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile traversalFile = new MockMultipartFile(
                "file", "../../../../etc/passwd.pdf", "application/pdf", validPdf
        );

        // Should either be rejected with 400 or successfully stored with sanitized UUID path without escaping storage dir
        MvcResult result = mockMvc.perform(multipart("/api/documents/upload")
                        .file(traversalFile)
                        .param("title", "Traversal Test")
                        .param("category", "GENERAL")
                        .header("Authorization", "Bearer " + employeeToken))
                .andReturn();

        int statusCode = result.getResponse().getStatus();
        assertTrue(statusCode == 400 || statusCode == 200 || statusCode == 201);

        // Verify no file was created in /etc or escaped directory
        assertFalse(new File("/etc/passwd.pdf").exists());
    }

    // =========================================================================
    // 8. Brute-Force Lockout
    // =========================================================================
    @Test
    @DisplayName("Security 8: Brute-force protection locks account after repeated failed login attempts")
    void testBruteForceLockout() throws Exception {
        String testEmail = "brute_force_victim@securevault.com";
        String ip = "10.0.0.99";

        LoginRequest badRequest = new LoginRequest(testEmail, "WrongPassword@999");

        // Execute 5 failed login attempts
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", ip)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isUnauthorized());
        }

        // 6th attempt should be blocked with lock message
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", ip)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("Too many failed login attempts")));
    }

    // =========================================================================
    // 9. Public Verify Leaks No Sensitive Field
    // =========================================================================
    @Test
    @DisplayName("Security 9: Public verify endpoint leaks no sensitive fields (passwords, file paths, etc.)")
    void testPublicVerifyLeaksNoSensitiveField() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/verify/" + publicRecord.getVerificationId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(publicRecord.getVerificationId()))
                .andExpect(jsonPath("$.documentTitle").value(victimDoc.getTitle()))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.checksum").exists())
                .andReturn();

        String responseBody = mvcResult.getResponse().getContentAsString();

        // Strict assertions against sensitive data leaks
        assertFalse(responseBody.contains("password"), "Response must not contain password");
        assertFalse(responseBody.contains("passwordHash"), "Response must not contain passwordHash");
        assertFalse(responseBody.contains("filePath"), "Response must not contain filePath");
        assertFalse(responseBody.contains("storagePath"), "Response must not contain storagePath");
        assertFalse(responseBody.contains("secret"), "Response must not contain secret");
        assertFalse(responseBody.contains("ROLE_"), "Response must not contain user security roles");
    }
}
