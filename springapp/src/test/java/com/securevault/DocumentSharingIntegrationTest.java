package com.securevault;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securevault.dto.ApprovalDecisionRequest;
import com.securevault.dto.ShareRequest;
import com.securevault.dto.UpdateDocumentRequest;
import com.securevault.entity.Document;
import com.securevault.entity.DocumentStatus;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.SharePermission;
import com.securevault.entity.User;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.JwtUtil;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
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
@DisplayName("Document Sharing & Permissions Integration Tests")
public class DocumentSharingIntegrationTest {

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

    private User owner;
    private User recipient;
    private User outsider;
    private User reviewer;

    private String ownerToken;
    private String recipientToken;
    private String outsiderToken;
    private String reviewerToken;

    private Long approvedDocId;

    @BeforeEach
    void setUp() throws Exception {
        Role employeeRole = roleRepository.findByName(RoleName.EMPLOYEE)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.EMPLOYEE)));
        Role reviewerRole = roleRepository.findByName(RoleName.REVIEWER)
                .orElseGet(() -> roleRepository.save(new Role(RoleName.REVIEWER)));

        long timestamp = System.currentTimeMillis();

        owner = createTestUser("owner_" + timestamp + "@securevault.com", "Document Owner", employeeRole);
        recipient = createTestUser("recipient_" + timestamp + "@securevault.com", "Share Recipient", employeeRole);
        outsider = createTestUser("outsider_" + timestamp + "@securevault.com", "Outsider ThirdParty", employeeRole);
        reviewer = createTestUser("reviewer_" + timestamp + "@securevault.com", "Compliance Reviewer", reviewerRole);

        ownerToken = jwtUtil.generateToken(owner);
        recipientToken = jwtUtil.generateToken(recipient);
        outsiderToken = jwtUtil.generateToken(outsider);
        reviewerToken = jwtUtil.generateToken(reviewer);

        // Upload and approve a base document owned by owner
        approvedDocId = uploadAndApproveDocument(ownerToken, reviewerToken, "Confidential Financial Report");
    }

    private User createTestUser(String email, String name, Role role) {
        User u = new User(name, email, passwordEncoder.encode("Password123"));
        u.setEnabled(true);
        u.setRoles(new HashSet<>(Collections.singletonList(role)));
        return userRepository.save(u);
    }

    private Long uploadAndApproveDocument(String userToken, String revToken, String title) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", VALID_PDF_BYTES
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("title", title)
                        .param("category", "FINANCE")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        long docId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        // Find approval and approve
        MvcResult approvalsResult = mockMvc.perform(get("/api/approvals?status=PENDING")
                        .header("Authorization", "Bearer " + revToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode pageNode = objectMapper.readTree(approvalsResult.getResponse().getContentAsString());
        long approvalId = -1;
        for (JsonNode app : pageNode.get("content")) {
            if (app.get("documentId").asLong() == docId) {
                approvalId = app.get("id").asLong();
                break;
            }
        }
        assertTrue(approvalId > 0, "Approval record must be created for uploaded document");

        ApprovalDecisionRequest decision = new ApprovalDecisionRequest("Approved for sharing test");
        mockMvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", "Bearer " + revToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(decision)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        return docId;
    }

    /**
     * Requirement 1: owner shares VIEW -> recipient can view but download returns 403
     */
    @Test
    @DisplayName("1. Owner shares VIEW -> Recipient can view details and inline, but download returns 403 Forbidden")
    void testOwnerSharesView_RecipientCanView_DownloadReturns403() throws Exception {
        ShareRequest shareRequest = new ShareRequest(recipient.getEmail(), SharePermission.VIEW, null);

        // Owner shares with VIEW
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("VIEW"))
                .andExpect(jsonPath("$.sharedWithEmail").value(recipient.getEmail()));

        // Recipient can view details
        mockMvc.perform(get("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(approvedDocId));

        // Recipient can view inline
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/view")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk());

        // Recipient CANNOT download (must return 403 Forbidden)
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/download")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Requirement 2: DOWNLOAD works (owner shares DOWNLOAD -> recipient can download)
     */
    @Test
    @DisplayName("2. Owner shares DOWNLOAD -> Recipient can view and download successfully")
    void testOwnerSharesDownload_RecipientCanDownload() throws Exception {
        ShareRequest shareRequest = new ShareRequest(recipient.getEmail(), SharePermission.DOWNLOAD, null);

        // Owner shares with DOWNLOAD
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("DOWNLOAD"));

        // Recipient can download
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/download")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk());

        // Recipient can also view
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/view")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk());
    }

    /**
     * Requirement 3: third user with no share gets 403 on view, download, versions and QR
     */
    @Test
    @DisplayName("3. Outsider with no share gets 403 on details, view, download, versions, and QR")
    void testOutsiderWithNoShare_Gets403AcrossAllEndpoints() throws Exception {
        // Outsider attempts details
        mockMvc.perform(get("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // Outsider attempts view
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/view")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // Outsider attempts download
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/download")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // Outsider attempts versions
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/versions")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());

        // Outsider attempts QR
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/qr")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Requirement 4: revoke -> recipient immediately gets 403
     */
    @Test
    @DisplayName("4. Revoke share -> Recipient access is immediately revoked with 403")
    void testRevokeShare_AccessStopsImmediately() throws Exception {
        // 1. Share with DOWNLOAD
        ShareRequest shareRequest = new ShareRequest(recipient.getEmail(), SharePermission.DOWNLOAD, null);
        MvcResult shareResult = mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        long shareId = objectMapper.readTree(shareResult.getResponse().getContentAsString()).get("id").asLong();

        // 2. Verify recipient has access
        mockMvc.perform(get("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk());

        // 3. Owner revokes share
        mockMvc.perform(delete("/api/documents/" + approvedDocId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // 4. Recipient access stops immediately (403 Forbidden)
        mockMvc.perform(get("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents/" + approvedDocId + "/download")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Requirement 5: expired share -> 403
     */
    @Test
    @DisplayName("5. Expired share -> Recipient receives 403 at access time")
    void testExpiredShare_RecipientGets403() throws Exception {
        // Share with expiration 2 hours in the past
        ShareRequest shareRequest = new ShareRequest(
                recipient.getEmail(),
                SharePermission.DOWNLOAD,
                LocalDateTime.now().minusHours(2)
        );

        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated());

        // Access at access time fails with 403
        mockMvc.perform(get("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/documents/" + approvedDocId + "/download")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Requirement 6: non-owner cannot share (403)
     */
    @Test
    @DisplayName("6. Non-owner cannot share document -> 403 Forbidden")
    void testNonOwnerCannotShare_Gets403() throws Exception {
        ShareRequest shareRequest = new ShareRequest(recipient.getEmail(), SharePermission.VIEW, null);

        // Outsider attempts to share owner's document
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isForbidden());
    }

    /**
     * Requirement 7: cannot share a PENDING document (409 Conflict)
     */
    @Test
    @DisplayName("7. Cannot share a PENDING document -> 409 Conflict")
    void testCannotSharePendingDocument_Returns409Conflict() throws Exception {
        // Upload a new document without approving it (status stays PENDING)
        MockMultipartFile file = new MockMultipartFile(
                "file", "draft.pdf", "application/pdf", VALID_PDF_BYTES
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/documents/upload")
                        .file(file)
                        .param("title", "Pending Unapproved Draft")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();

        long pendingDocId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("id").asLong();

        ShareRequest shareRequest = new ShareRequest(recipient.getEmail(), SharePermission.VIEW, null);

        // Attempting to share PENDING document must return 409 Conflict
        mockMvc.perform(post("/api/documents/" + pendingDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Only APPROVED documents can be shared")));
    }

    /**
     * Requirement 8: shared user cannot delete or edit (403)
     */
    @Test
    @DisplayName("8. Shared user cannot delete, edit metadata, upload new versions, or restore versions -> 403")
    void testSharedUserCannotDeleteOrEdit_Returns403() throws Exception {
        // Grant recipient DOWNLOAD permission (highest share permission)
        ShareRequest shareRequest = new ShareRequest(recipient.getEmail(), SharePermission.DOWNLOAD, null);
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(shareRequest)))
                .andExpect(status().isCreated());

        // 1. Recipient cannot edit metadata
        UpdateDocumentRequest updateRequest = new UpdateDocumentRequest();
        updateRequest.setTitle("Malicious Title Update");
        mockMvc.perform(put("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + recipientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());

        // 2. Recipient cannot delete document
        mockMvc.perform(delete("/api/documents/" + approvedDocId)
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        // 3. Recipient cannot upload new version
        MockMultipartFile newVersionFile = new MockMultipartFile(
                "file", "new_v2.pdf", "application/pdf",
                "%PDF-1.5\n%\n2 0 obj\n<<>>\nendobj\ntrailer\n<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII)
        );
        mockMvc.perform(multipart("/api/documents/" + approvedDocId + "/versions")
                        .file(newVersionFile)
                        .param("changeDescription", "Unauthorized version")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());

        // 4. Recipient cannot restore a version
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/versions/1/restore")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isForbidden());
    }

    /**
     * Requirement 9: Re-sharing un-revokes existing row
     */
    @Test
    @DisplayName("9. Re-sharing after revocation un-revokes existing row and restores access")
    void testReShareUnrevokesExistingRow() throws Exception {
        ShareRequest viewShare = new ShareRequest(recipient.getEmail(), SharePermission.VIEW, null);

        // Share VIEW
        MvcResult res1 = mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewShare)))
                .andExpect(status().isCreated())
                .andReturn();

        long shareId = objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asLong();

        // Revoke
        mockMvc.perform(delete("/api/documents/" + approvedDocId + "/shares/" + shareId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNoContent());

        // Re-share with DOWNLOAD
        ShareRequest downloadShare = new ShareRequest(recipient.getEmail(), SharePermission.DOWNLOAD, null);
        MvcResult res2 = mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(downloadShare)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.permission").value("DOWNLOAD"))
                .andExpect(jsonPath("$.revoked").value(false))
                .andReturn();

        long newShareId = objectMapper.readTree(res2.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(shareId, newShareId, "Re-sharing must update and un-revoke the existing row (same ID)");

        // Recipient can access again
        mockMvc.perform(get("/api/documents/" + approvedDocId + "/download")
                        .header("Authorization", "Bearer " + recipientToken))
                .andExpect(status().isOk());
    }

    /**
     * Requirement 10: Validation - Recipient not found returns 404, self share returns 400
     */
    @Test
    @DisplayName("10. Validation checks: Recipient not found -> 404, Self-sharing -> 400")
    void testValidation_NonExistentUserAndSelfSharing() throws Exception {
        // Unknown user
        ShareRequest unknownUserReq = new ShareRequest("ghost_user_999@securevault.com", SharePermission.VIEW, null);
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(unknownUserReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("User not found")));

        // Self-sharing
        ShareRequest selfReq = new ShareRequest(owner.getEmail(), SharePermission.VIEW, null);
        mockMvc.perform(post("/api/documents/" + approvedDocId + "/shares")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(selfReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Cannot share document with yourself")));
    }
}
