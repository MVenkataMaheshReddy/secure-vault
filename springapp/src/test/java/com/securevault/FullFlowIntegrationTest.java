package com.securevault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.LoginRequest;
import com.securevault.dto.RegisterRequest;
import com.securevault.dto.ShareRequest;
import com.securevault.entity.Document;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.SharePermission;
import com.securevault.entity.User;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@DisplayName("FullFlowIntegrationTest: End-to-End System Flows")
public class FullFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private static final byte[] VALID_PDF_BYTES =
            "%PDF-1.5\n%\n1 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    private User reviewerUser;
    private String reviewerToken;

    @BeforeEach
    void setUp() {
        Role reviewerRole = roleRepository.findByName(RoleName.REVIEWER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.REVIEWER)));

        reviewerUser = userRepository.findByEmail("reviewer_flow@securevault.com")
                .orElseGet(() -> {
                    User u = new User("Flow Reviewer", "reviewer_flow@securevault.com", passwordEncoder.encode("Password123"));
                    u.setEnabled(true);
                    u.setRoles(new HashSet<>(Collections.singletonList(reviewerRole)));
                    return userRepository.save(u);
                });

        reviewerToken = jwtUtil.generateToken(reviewerUser);
    }

    // =========================================================================
    // Flow 1: Register -> Login -> Authenticated API Call
    // =========================================================================
    @Test
    @DisplayName("Flow 1: User Registration, Login, and Authenticated User Info Fetch")
    void testFlow1_RegisterAndLogin() throws Exception {
        String userEmail = "flow_alice_" + System.currentTimeMillis() + "@securevault.com";
        RegisterRequest registerRequest = new RegisterRequest("Alice Flow", userEmail, "Password123");

        // 1. Register new user
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.name").value("Alice Flow"));

        // 2. Login with credentials
        LoginRequest loginRequest = new LoginRequest(userEmail, "Password123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.user.email").value(userEmail))
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.get("accessToken").asText();
        assertNotNull(token);

        // 3. Make authenticated request using issued token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(userEmail))
                .andExpect(jsonPath("$.name").value("Alice Flow"));
    }

    // =========================================================================
    // Flow 2: Upload -> New Version -> Version History -> Restore
    // =========================================================================
    @Test
    @DisplayName("Flow 2: Upload Document -> Create Version -> View History -> Restore Prior Version")
    void testFlow2_UploadVersionRestore() throws Exception {
        String email = "flow_bob_" + System.currentTimeMillis() + "@securevault.com";
        User bob = createTestUser(email, "Bob Builder", RoleName.EMPLOYEE);
        String bobToken = jwtUtil.generateToken(bob);

        // 1. Upload initial document (Version 1)
        MockMultipartFile fileV1 = new MockMultipartFile(
                "file", "architecture_spec.pdf", "application/pdf", VALID_PDF_BYTES
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(fileV1)
                        .param("title", "Architecture Spec")
                        .param("category", "TECHNICAL")
                        .param("description", "Initial architectural design")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.currentVersion").value(1))
                .andReturn();

        JsonNode docNode = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        long docId = docNode.get("id").asLong();

        // 2. Upload new version (Version 2) with distinct content
        byte[] v2Bytes = "%PDF-1.5\n%\n2 0 obj\n<< /Version 2 /DocId 100 >>\nendobj\ntrailer\n<<>>\n%%EOF"
                .getBytes(StandardCharsets.US_ASCII);
        MockMultipartFile fileV2 = new MockMultipartFile(
                "file", "architecture_spec_v2.pdf", "application/pdf", v2Bytes
        );

        mockMvc.perform(multipart("/api/documents/" + docId + "/versions")
                        .file(fileV2)
                        .param("changeDescription", "Added sequence and component diagrams")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.versionNumber").value(2));

        // 3. Fetch version history
        MvcResult versionsResult = mockMvc.perform(get("/api/documents/" + docId + "/versions")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();

        JsonNode versionsArray = objectMapper.readTree(versionsResult.getResponse().getContentAsString());
        // Find version 1 record ID
        long version1Id = -1;
        for (JsonNode v : versionsArray) {
            if (v.get("versionNumber").asInt() == 1) {
                version1Id = v.get("id").asLong();
                break;
            }
        }
        assertTrue(version1Id > 0, "Version 1 ID must be found");

        // 4. Restore version 1
        mockMvc.perform(post("/api/documents/" + docId + "/versions/" + version1Id + "/restore")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionNumber").value(3))
                .andExpect(jsonPath("$.changeDescription", containsString("Restored from v")));

        // 5. Verify version count is now 3 and latest version is 3
        mockMvc.perform(get("/api/documents/" + docId + "/versions")
                        .header("Authorization", "Bearer " + bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].versionNumber").value(3))
                .andExpect(jsonPath("$[0].current").value(true));
    }

    // =========================================================================
    // Flow 3: Approve -> QR Generation -> Public Verification
    // =========================================================================
    @Test
    @DisplayName("Flow 3: Reviewer Approves Document -> QR Generated -> Public Verifies Without Auth")
    void testFlow3_ApproveQrPublicVerify() throws Exception {
        String email = "flow_author_" + System.currentTimeMillis() + "@securevault.com";
        User author = createTestUser(email, "Doc Author", RoleName.EMPLOYEE);
        String authorToken = jwtUtil.generateToken(author);

        // 1. Author uploads document (created with PENDING status)
        MockMultipartFile certFile = new MockMultipartFile(
                "file", "security_certificate.pdf", "application/pdf", VALID_PDF_BYTES
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(certFile)
                        .param("title", "ISO Security Certification")
                        .param("category", "LEGAL")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        long docId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        // 2. Reviewer finds pending approval
        MvcResult approvalsResult = mockMvc.perform(get("/api/approvals?status=PENDING")
                        .header("Authorization", "Bearer " + reviewerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pageNode = objectMapper.readTree(approvalsResult.getResponse().getContentAsString());
        JsonNode content = pageNode.get("content");
        long approvalId = -1;
        for (JsonNode app : content) {
            if (app.get("documentId").asLong() == docId) {
                approvalId = app.get("id").asLong();
                break;
            }
        }
        assertTrue(approvalId > 0, "Approval request for document must exist");

        // 3. Reviewer approves document
        ApprovalDecisionRequest decision = new ApprovalDecisionRequest("Approved after audit");
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + reviewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // 4. Verify QR endpoint serves QR image
        mockMvc.perform(get("/api/documents/" + docId + "/qr")
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/")));

        // 5. Get document to retrieve verificationId
        MvcResult docResult = mockMvc.perform(get("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").exists())
                .andReturn();

        String verificationId = objectMapper.readTree(docResult.getResponse().getContentAsString())
                .get("verificationId").asText();
        assertNotNull(verificationId);
        assertTrue(verificationId.startsWith("SV-"));

        // 6. Public verification (NO Authentication header provided)
        MvcResult verifyResult = mockMvc.perform(get("/api/verify/" + verificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value(verificationId))
                .andExpect(jsonPath("$.documentTitle").value("ISO Security Certification"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.checksum").exists())
                .andReturn();

        String verifyBody = verifyResult.getResponse().getContentAsString();
        assertFalse(verifyBody.contains("password"), "Must not leak password");
        assertFalse(verifyBody.contains("filePath"), "Must not leak internal filePath");
    }

    // =========================================================================
    // Flow 4: Share Document -> Recipient Accesses -> Revoke Share -> Access Denied
    // =========================================================================
    @Test
    @DisplayName("Flow 4: Document Sharing, Recipient Access, Share Revocation, and Forbidden Check")
    void testFlow4_ShareAccessRevoke() throws Exception {
        String ownerEmail = "flow_owner_" + System.currentTimeMillis() + "@securevault.com";
        String recipientEmail = "flow_recipient_" + System.currentTimeMillis() + "@securevault.com";

        User owner = createTestUser(ownerEmail, "Owner User", RoleName.EMPLOYEE);
        User recipient = createTestUser(recipientEmail, "Recipient User", RoleName.EMPLOYEE);

        String ownerToken = jwtUtil.generateToken(owner);
        String recipientToken = jwtUtil.generateToken(recipient);

        // 1. Owner uploads private document
        MockMultipartFile file = new MockMultipartFile(
                "file", "q3_strategy.pdf", "application/pdf", VALID_PDF_BYTES
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("title", "Q3 Strategy Document")
                        .param("category", "MANAGEMENT")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();

        long docId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        // 1b. Document must be APPROVED to be shared
        Document doc = documentRepository.findById(docId).orElseThrow();
        doc.setStatus(com.securevault.entity.DocumentStatus.APPROVED);
        documentRepository.save(doc);

        // 2. Recipient cannot access document yet (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        // 3. Owner shares document with recipient
        ShareRequest shareRequest = new ShareRequest(recipientEmail, SharePermission.VIEW, null);
        MvcResult shareResult = mockMvc.perform(post("/api/documents/" + docId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        long shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asLong();

        // 4. Recipient views shared documents and can now access document details
        mockMvc.perform(get("/api/documents/shared-with-me")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))));

        mockMvc.perform(get("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Q3 Strategy Document"));

        // 5. Owner revokes share
        mockMvc.perform(delete("/api/documents/" + docId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // 6. Recipient cannot access document anymore (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + docId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());
    }

    // =========================================================================
    // Flow 5: Search Permission Filtering
    // =========================================================================
    @Test
    @DisplayName("Flow 5: Search Results Strictly Filtered According to Ownership and Shares")
    void testFlow5_SearchPermissionFiltering() throws Exception {
        String userAEmail = "flow_search_a_" + System.currentTimeMillis() + "@securevault.com";
        String userBEmail = "flow_search_b_" + System.currentTimeMillis() + "@securevault.com";
        String userCEmail = "flow_search_c_" + System.currentTimeMillis() + "@securevault.com";

        User userA = createTestUser(userAEmail, "Search User A", RoleName.EMPLOYEE);
        User userB = createTestUser(userBEmail, "Search User B", RoleName.EMPLOYEE);
        User userC = createTestUser(userCEmail, "Search User C", RoleName.EMPLOYEE);

        String tokenA = jwtUtil.generateToken(userA);
        String tokenB = jwtUtil.generateToken(userB);
        String tokenC = jwtUtil.generateToken(userC);

        // 1. User A uploads Document A
        MockMultipartFile fileA = new MockMultipartFile(
                "file", "apollo_report.pdf", "application/pdf", VALID_PDF_BYTES
        );
        MvcResult docAResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(fileA)
                        .param("title", "Project Apollo Discovery")
                        .param("category", "RESEARCH")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andReturn();
        long docAId = objectMapper.readTree(docAResult.getResponse().getContentAsString()).get("id").asLong();

        // Approve docA so it is eligible to be shared
        Document docA = documentRepository.findById(docAId).orElseThrow();
        docA.setStatus(com.securevault.entity.DocumentStatus.APPROVED);
        documentRepository.save(docA);

        // 2. User B uploads Document B
        MockMultipartFile fileB = new MockMultipartFile(
                "file", "beta_report.pdf", "application/pdf", VALID_PDF_BYTES
        );
        mockMvc.perform(multipart("/api/documents/upload")
                        .file(fileB)
                        .param("title", "Beta Testing Results")
                        .param("category", "QA")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk());

        // 3. User A shares Document A with User B
        ShareRequest shareRequest = new ShareRequest(userBEmail, SharePermission.VIEW, null);
        mockMvc.perform(post("/api/documents/" + docAId + "/shares")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated());

        // 4. User B searches for "Apollo": can see Document A (shared with User B)
        mockMvc.perform(get("/api/documents/search")
                        .param("q", "Apollo")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", containsString("Apollo")));

        // 5. User B searches for "Beta": can see Document B (owned by User B)
        mockMvc.perform(get("/api/documents/search")
                        .param("q", "Beta")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title", containsString("Beta")));

        // 6. User C searches for "Apollo": CANNOT see Document A (not owned, not shared)
        mockMvc.perform(get("/api/documents/search")
                        .param("q", "Apollo")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));

        // 7. User C searches for "Beta": CANNOT see Document B (not owned, not shared)
        mockMvc.perform(get("/api/documents/search")
                        .param("q", "Beta")
                        .header("Authorization", "Bearer " + tokenC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    private User createTestUser(String email, String name, RoleName roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseGet(() -> roleRepository.save(new Role(roleName)));
        User u = new User(name, email, passwordEncoder.encode("Password123"));
        u.setEnabled(true);
        u.setRoles(new HashSet<>(Collections.singletonList(role)));
        return userRepository.save(u);
    }
}
