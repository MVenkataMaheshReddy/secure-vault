package com.securevault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.LoginRequest;
import com.securevault.dto.RegisterRequest;
import com.securevault.entity.ApprovalStatus;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.repository.ApprovalRepository;
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
@DisplayName("Role Selection at Login/Registration & Admin Approval Flow Tests")
public class RoleAuthAndAdminApprovalTest {

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
    private ApprovalRepository approvalRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    private User adminUser;
    private User employeeUser;
    private String adminToken;
    private String employeeToken;

    @BeforeEach
    void setUp() {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN)));
        Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.EMPLOYEE)));

        // Create or reset standard Admin user
        adminUser = userRepository.findByEmail("admin.test@securevault.io").orElseGet(() -> {
            User u = new User();
            u.setName("Admin Tester");
            u.setEmail("admin.test@securevault.io");
            u.setPasswordHash(passwordEncoder.encode("AdminPass123!"));
            u.setEnabled(true);
            u.setRoles(new HashSet<>(Collections.singletonList(adminRole)));
            return userRepository.save(u);
        });

        // Create or reset standard Employee user
        employeeUser = userRepository.findByEmail("employee.test@securevault.io").orElseGet(() -> {
            User u = new User();
            u.setName("Employee Tester");
            u.setEmail("employee.test@securevault.io");
            u.setPasswordHash(passwordEncoder.encode("EmployeePass123!"));
            u.setEnabled(true);
            u.setRoles(new HashSet<>(Collections.singletonList(employeeRole)));
            return userRepository.save(u);
        });

        adminToken = jwtUtil.generateToken(adminUser.getEmail(), adminUser.getId(), Collections.singletonList("ROLE_ADMIN"));
        employeeToken = jwtUtil.generateToken(employeeUser.getEmail(), employeeUser.getId(), Collections.singletonList("ROLE_EMPLOYEE"));
    }

    /**
     * TEST 1: Registration - Wrong admin code returns 403 "Invalid admin access code"
     */
    @Test
    @DisplayName("Registration: Wrong admin access code returns 403 Forbidden")
    void testRegisterAdmin_WrongCode_Returns403() throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setName("Malicious User");
        req.setEmail("fakeadmin" + System.currentTimeMillis() + "@test.com");
        req.setPassword("Password123!");
        req.setRole("ADMIN");
        req.setAdminCode("WRONG_CODE_XYZ");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("Invalid admin access code")));
    }

    /**
     * TEST 2: Registration - Correct admin code successfully registers ADMIN
     */
    @Test
    @DisplayName("Registration: Correct admin access code registers ADMIN successfully")
    void testRegisterAdmin_CorrectCode_Success() throws Exception {
        String email = "realadmin" + System.currentTimeMillis() + "@test.com";
        RegisterRequest req = new RegisterRequest();
        req.setName("Real Admin");
        req.setEmail(email);
        req.setPassword("ValidAdminPass123!");
        req.setRole("ADMIN");
        req.setAdminCode("ADMIN_SECRET_2025");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email", is(email)))
                .andExpect(jsonPath("$.roles", hasItem("ADMIN")));
    }

    /**
     * TEST 3: Login - Login with a role the user doesn't hold returns 403
     */
    @Test
    @DisplayName("Login: User attempting to login with unheld role returns 403 Forbidden")
    void testLogin_UnheldRole_Returns403() throws Exception {
        // employeeUser holds only ROLE_EMPLOYEE, attempts login as ADMIN
        LoginRequest req = new LoginRequest("employee.test@securevault.io", "EmployeePass123!", "ADMIN");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("You are not registered as ADMIN")));
    }

    /**
     * TEST 4: Login - Bad credentials returns 401 Unauthorized (generic error)
     */
    @Test
    @DisplayName("Login: Bad credentials return generic 401 Unauthorized")
    void testLogin_BadCredentials_Returns401() throws Exception {
        LoginRequest req = new LoginRequest("employee.test@securevault.io", "WrongPassword999", "EMPLOYEE");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", is("Invalid email or password")));
    }

    /**
     * TEST 5: Employee calling Admin APIs returns 403 Forbidden
     */
    @Test
    @DisplayName("Security: Employee calling Admin Approvals API returns 403 Forbidden")
    void testEmployeeCallingAdminApi_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/approvals")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/approvals/pending-count")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/approvals/1/approve")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    /**
     * TEST 6: Approval Flow - Employee uploads -> PENDING, Admin approves -> APPROVED + QR, 409 on re-decide
     */
    @Test
    @DisplayName("Approval Flow: Employee upload -> PENDING -> Admin approve -> APPROVED + 409 conflict check")
    void testApprovalFlow_ApproveAndConflict() throws Exception {
        // 1. Employee uploads document
        MockMultipartFile file = new MockMultipartFile(
                "file", "flow-test.pdf", "application/pdf", "%PDF-1.4 test document content".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Flow Test Doc")
                        .param("category", "LEGAL")
                        .param("description", "Needs approval")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        Long docId = uploadJson.get("id").asLong();

        // 2. Admin retrieves approvals
        MvcResult listResult = mockMvc.perform(get("/api/admin/approvals?status=PENDING")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", not(empty())))
                .andReturn();

        JsonNode listJson = objectMapper.readTree(listResult.getResponse().getContentAsString());
        Long approvalId = null;
        for (JsonNode item : listJson.get("content")) {
            if (item.get("documentId").asLong() == docId) {
                approvalId = item.get("id").asLong();
                break;
            }
        }
        assertNotNull(approvalId, "Pending approval record must exist for uploaded document");

        // 3. Admin approves document
        ApprovalDecisionRequest approveReq = new ApprovalDecisionRequest();
        approveReq.setComments("All legal requirements met");

        mockMvc.perform(post("/api/admin/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.reviewerEmail", is(adminUser.getEmail())));

        // 4. Verify document status is now APPROVED in database and verification exists
        Document updatedDoc = documentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentStatus.APPROVED, updatedDoc.getStatus());
        assertNotNull(updatedDoc.getVerificationId(), "Verification ID should be generated on approve");

        // 5. Trying to decide again returns 409 Conflict
        mockMvc.perform(post("/api/admin/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Only PENDING approval requests can be decided")));
    }

    /**
     * TEST 7: Approval Flow - Reject with comments, 409 conflict, and Owner Resubmit
     */
    @Test
    @DisplayName("Approval Flow: Admin reject -> REJECTED -> 409 conflict -> Owner resubmit -> PENDING")
    void testApprovalFlow_RejectAndResubmit() throws Exception {
        // 1. Employee uploads document
        MockMultipartFile file = new MockMultipartFile(
                "file", "reject-test.pdf", "application/pdf", "%PDF-1.4 test document for rejection".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Reject Test Doc")
                        .param("category", "FINANCE")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")))
                .andReturn();

        JsonNode uploadJson = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        Long docId = uploadJson.get("id").asLong();

        // 2. Locate approval ID
        var approvalsList = approvalRepository.findByDocumentId(docId);
        assertFalse(approvalsList.isEmpty());
        Long approvalId = approvalsList.get(0).getId();

        // 3. Admin rejects without comments -> 400 Bad Request
        mockMvc.perform(post("/api/admin/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Comments are required")));

        // 4. Admin rejects with required comments -> 200 OK
        ApprovalDecisionRequest rejectReq = new ApprovalDecisionRequest();
        rejectReq.setComments("Document signature is missing on page 2");

        mockMvc.perform(post("/api/admin/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.comments", is("Document signature is missing on page 2")));

        // 5. Verify document is REJECTED in database
        Document rejectedDoc = documentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentStatus.REJECTED, rejectedDoc.getStatus());

        // 6. Deciding rejected item again returns 409 Conflict
        mockMvc.perform(post("/api/admin/approvals/" + approvalId + "/reject")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(rejectReq)))
                .andExpect(status().isConflict());

        // 7. Owner resubmits document -> becomes PENDING
        mockMvc.perform(post("/api/documents/" + docId + "/resubmit")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING")));

        Document resubmittedDoc = documentRepository.findById(docId).orElseThrow();
        assertEquals(DocumentStatus.PENDING, resubmittedDoc.getStatus());
    }

    /**
     * TEST 8: Self-approval prevention - User cannot approve own document = 403
     */
    @Test
    @DisplayName("Self-approval check: Admin cannot approve document they own -> 403 Forbidden")
    void testSelfApproval_Prevented_Returns403() throws Exception {
        // Admin uploads a document as employee or creates document owned by admin
        Document selfDoc = new Document();
        selfDoc.setTitle("Admin Self Doc");
        selfDoc.setOriginalFileName("self.pdf");
        selfDoc.setCategory("ADMIN");
        selfDoc.setOwner(adminUser);
        selfDoc.setStatus(DocumentStatus.PENDING);
        selfDoc.setCurrentVersion(1);
        Document savedDoc = documentRepository.save(selfDoc);

        var approval = new com.securevault.entity.Approval(savedDoc, ApprovalStatus.PENDING, "Pending self review");
        approval = approvalRepository.save(approval);

        // Admin attempts to approve their own document -> 403 Forbidden
        mockMvc.perform(post("/api/admin/approvals/" + approval.getId() + "/approve")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("You cannot approve your own document")));
    }

    /**
     * TEST 9: Admin uploads are auto-APPROVED
     */
    @Test
    @DisplayName("Admin Upload: Admin uploads are auto-APPROVED with verification generated")
    void testAdminUpload_AutoApproved() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "admin-policy.pdf", "application/pdf", "%PDF-1.4 official admin policy".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("title", "Official Policy")
                        .param("category", "POLICY")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("APPROVED")))
                .andExpect(jsonPath("$.verificationId", notNullValue()));
    }
}
