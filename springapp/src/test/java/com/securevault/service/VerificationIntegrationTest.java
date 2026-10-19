package com.securevault.service;

import com.securevault.controller.DocumentController;
import com.securevault.controller.VerificationController;
import com.securevault.dto.FileCheckResponseDTO;
import com.securevault.dto.PublicVerificationDTO;
import com.securevault.dto.VerificationRecordDTO;
import com.securevault.entity.VerificationStatus;
import com.securevault.security.CustomAccessDeniedHandler;
import com.securevault.security.CustomAuthenticationEntryPoint;
import com.securevault.repository.DocumentRepository;
import com.securevault.repository.RevokedTokenRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.CustomUserDetailsService;
import com.securevault.security.JwtAuthenticationFilter;
import com.securevault.security.JwtUtil;
import com.securevault.security.SecurityConfig;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {VerificationController.class, DocumentController.class})
@Import({SecurityConfig.class, CustomAuthenticationEntryPoint.class, CustomAccessDeniedHandler.class})
public class VerificationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VerificationService verificationService;

    @MockBean
    private DocumentService documentService;

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

    @Test
    @DisplayName("Public verification endpoint is accessible without authentication and returns safe metadata")
    void testPublicVerificationEndpointPermitAll() throws Exception {
        PublicVerificationDTO dto = new PublicVerificationDTO(
                "SV-12345678",
                "Official Degree Certificate",
                "EDUCATION",
                "Alice Wonder",
                "VALID",
                LocalDateTime.now(),
                1,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        );

        when(verificationService.getPublicVerification(eq("SV-12345678"), anyString()))
                .thenReturn(dto);

        // Call without any Authorization header
        mockMvc.perform(get("/api/verify/SV-12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value("SV-12345678"))
                .andExpect(jsonPath("$.documentTitle").value("Official Degree Certificate"))
                .andExpect(jsonPath("$.uploadedBy").value("Alice Wonder"))
                .andExpect(jsonPath("$.status").value("VALID"))
                .andExpect(jsonPath("$.checksum").value("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
                // Ensure no email, no file paths, no internal storage dirs
                .andExpect(jsonPath("$.ownerEmail").doesNotExist())
                .andExpect(jsonPath("$.filePath").doesNotExist())
                .andExpect(jsonPath("$.fileContent").doesNotExist());
    }

    @Test
    @DisplayName("Unknown or malformed ID returns 404")
    void testUnknownVerificationIdReturns404() throws Exception {
        when(verificationService.getPublicVerification(eq("SV-UNKNOWN1"), anyString()))
                .thenThrow(new com.securevault.exception.ResourceNotFoundException("Verification reference not found"));

        mockMvc.perform(get("/api/verify/SV-UNKNOWN1"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Public check-file endpoint is accessible without authentication")
    void testPublicCheckFileEndpointPermitAll() throws Exception {
        FileCheckResponseDTO dto = new FileCheckResponseDTO(
                "SV-12345678",
                true,
                1,
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "VALID",
                "Document copy is authentic! Matches version 1."
        );

        when(verificationService.checkFileAuthenticity(eq("SV-12345678"), any(), anyString()))
                .thenReturn(dto);

        MockMultipartFile file = new MockMultipartFile("file", "copy.pdf", "application/pdf", "test".getBytes());

        mockMvc.perform(multipart("/api/verify/SV-12345678/check-file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches").value(true))
                .andExpect(jsonPath("$.matchedVersion").value(1))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can generate verification manually")
    void testAdminCanGenerateVerification() throws Exception {
        VerificationRecordDTO record = new VerificationRecordDTO(
                1L, 100L, "Transcript", "ACADEMIC", "SV-12345678",
                VerificationStatus.VALID, 1, "Admin", LocalDateTime.now()
        );

        when(verificationService.generateVerification(eq(100L), any(), eq(true)))
                .thenReturn(record);

        mockMvc.perform(post("/api/documents/100/verification"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationId").value("SV-12345678"))
                .andExpect(jsonPath("$.status").value("VALID"));
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("Employee cannot generate verification (returns 403)")
    void testEmployeeCannotGenerateVerification() throws Exception {
        mockMvc.perform(post("/api/documents/100/verification"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Admin can revoke verification")
    void testAdminCanRevoke() throws Exception {
        VerificationRecordDTO record = new VerificationRecordDTO(
                1L, 100L, "Transcript", "ACADEMIC", "SV-12345678",
                VerificationStatus.REVOKED, 1, "Admin", LocalDateTime.now()
        );

        when(verificationService.revokeVerification(eq(100L), any(), eq(true)))
                .thenReturn(record);

        mockMvc.perform(put("/api/documents/100/verification/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    @WithMockUser(roles = "VERIFIER")
    @DisplayName("Verifier cannot revoke verification (ADMIN only, returns 403)")
    void testVerifierCannotRevoke() throws Exception {
        mockMvc.perform(put("/api/documents/100/verification/revoke"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "EMPLOYEE")
    @DisplayName("Employee cannot revoke verification (returns 403)")
    void testEmployeeCannotRevoke() throws Exception {
        mockMvc.perform(put("/api/documents/100/verification/revoke"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VERIFIER")
    @DisplayName("Verifier can access verification records dashboard endpoint")
    void testVerifierCanAccessRecords() throws Exception {
        when(verificationService.getAllVerifications(any(), eq(true)))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/verify/records"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
