package com.securevault.security;

import com.securevault.controller.AdminController;
import com.securevault.controller.AnalyticsController;
import com.securevault.controller.DocumentController;
import com.securevault.dto.DocumentDTO;
import com.securevault.dto.UserDTO;
import com.securevault.exception.ForbiddenException;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.RevokedTokenRepository;
import com.securevault.repository.UserRepository;
import com.securevault.service.DocumentService;
import com.securevault.service.UserService;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {AdminController.class, DocumentController.class, AnalyticsController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
public class RbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private DocumentService documentService;

    @MockBean
    private com.securevault.service.VerificationService verificationService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private RevokedTokenRepository revokedTokenRepository;

    @MockBean
    private com.securevault.service.AnalyticsService analyticsService;

    // 1. EMPLOYEE calling admin API -> 403 Forbidden
    @Test
    @WithMockUser(username = "employee@example.com", roles = {"EMPLOYEE"})
    void testEmployee_CallingAdminApi_Returns403() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    // 2. ADMIN calling admin API -> 200 OK
    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdmin_CallingAdminApi_Returns200() throws Exception {
        when(userService.getAllUsers()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk());
    }

    // 3. REVIEWER uploading document -> 403 Forbidden
    @Test
    @WithMockUser(username = "reviewer@example.com", roles = {"REVIEWER"})
    void testReviewer_UploadingDocument_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF DATA".getBytes()
        );

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("userId", "1"))
                .andExpect(status().isForbidden());
    }

    // 4. VERIFIER uploading document -> 403 Forbidden
    @Test
    @WithMockUser(username = "verifier@example.com", roles = {"VERIFIER"})
    void testVerifier_UploadingDocument_Returns403() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF DATA".getBytes()
        );

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("userId", "1"))
                .andExpect(status().isForbidden());
    }

    // 5. EMPLOYEE uploading own document -> 200 OK
    @Test
    @WithMockUser(username = "employee@example.com", roles = {"EMPLOYEE"})
    void testEmployee_UploadingOwnDocument_Returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF DATA".getBytes()
        );

        DocumentDTO docDTO = new DocumentDTO();
        docDTO.setId(10L);
        docDTO.setTitle("test.pdf");

        when(documentService.saveDocument(any(), any(), any(), any(), any(), eq("employee@example.com")))
                .thenReturn(docDTO);

        mockMvc.perform(multipart("/documents/upload")
                        .file(file)
                        .param("userId", "1"))
                .andExpect(status().isOk());
    }

    // 6. EMPLOYEE deleting someone else's document -> 403 Forbidden
    @Test
    @WithMockUser(username = "employee@example.com", roles = {"EMPLOYEE"})
    void testEmployee_DeletingSomeoneElsesDocument_Returns403() throws Exception {
        when(documentService.deleteDocumentWithAuth(eq(99L), eq("employee@example.com"), eq(false)))
                .thenThrow(new ForbiddenException("You can only delete your own documents"));

        mockMvc.perform(delete("/documents/delete/99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("You can only delete your own documents"));
    }

    // 7. ADMIN deleting someone else's document -> 200 OK
    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdmin_DeletingAnyDocument_Returns200() throws Exception {
        when(documentService.deleteDocumentWithAuth(eq(99L), eq("admin@example.com"), eq(true)))
                .thenReturn(true);

        mockMvc.perform(delete("/documents/delete/99"))
                .andExpect(status().isOk())
                .andExpect(content().string("Document deleted successfully."));
    }

    // 8. Admin self-disable safeguard -> 403 Forbidden
    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdmin_SelfDisableSafeguard_Returns403() throws Exception {
        when(userService.updateUserEnabled(eq(1L), eq(false), eq("admin@example.com")))
                .thenThrow(new ForbiddenException("Admins cannot disable their own account"));

        mockMvc.perform(put("/api/admin/users/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Admins cannot disable their own account"));
    }

    // 9. Admin self-demote safeguard -> 403 Forbidden
    @Test
    @WithMockUser(username = "admin@example.com", roles = {"ADMIN"})
    void testAdmin_SelfDemoteSafeguard_Returns403() throws Exception {
        when(userService.updateUserRoles(eq(1L), any(), eq("admin@example.com")))
                .thenThrow(new ForbiddenException("Admins cannot demote themselves (cannot remove ADMIN role from your own account)"));

        mockMvc.perform(put("/api/admin/users/1/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\": [\"EMPLOYEE\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message").value("Admins cannot demote themselves (cannot remove ADMIN role from your own account)"));
    }

    // 10. VERIFIER calling analytics -> 403 Forbidden
    @Test
    @WithMockUser(username = "verifier@example.com", roles = {"VERIFIER"})
    void testVerifier_CallingAnalytics_Returns403() throws Exception {
        mockMvc.perform(get("/api/analytics"))
                .andExpect(status().isForbidden());
    }

    // 11. EMPLOYEE calling analytics -> 200 OK (limited scope)
    @Test
    @WithMockUser(username = "employee@example.com", roles = {"EMPLOYEE"})
    void testEmployee_CallingAnalytics_Returns200() throws Exception {
        when(documentRepository.findByOwnerEmailAndDeletedFalse("employee@example.com"))
                .thenReturn(Collections.emptyList());
        when(documentRepository.findAllByDeletedFalse())
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("LIMITED_USER"));
    }
}
