package com.securevault.service;

import com.securevault.dto.AuditLogDTO;
import com.securevault.entity.AuditLog;
import com.securevault.entity.User;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    private AuditService auditService;

    @BeforeEach
    public void setup() {
        auditService = new AuditService(auditLogRepository, userRepository);
    }

    @Test
    public void testLog_SavesAuditRecordSuccessfully() {
        User user = new User();
        user.setId(1L);
        user.setName("John Doe");
        user.setEmail("john@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        auditService.log(1L, "UPLOAD", "DOCUMENT", 100L, "Uploaded passport.pdf");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertEquals(user, saved.getUser());
        assertEquals("UPLOAD", saved.getAction());
        assertEquals("DOCUMENT", saved.getEntityType());
        assertEquals(100L, saved.getEntityId());
        assertEquals("Uploaded passport.pdf", saved.getDetails());
        assertNotNull(saved.getIpAddress());
    }

    @Test
    public void testLogPublic_SavesWithNullUser() {
        auditService.logPublic("VERIFY", "VERIFICATION_RECORD", 42L, "Public verification check");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertNull(saved.getUser());
        assertEquals("VERIFY", saved.getAction());
        assertEquals("VERIFICATION_RECORD", saved.getEntityType());
        assertEquals(42L, saved.getEntityId());
    }

    @Test
    public void testAuditFailureDoesNotThrowOrBreakWorkflow() {
        when(userRepository.findById(1L)).thenThrow(new RuntimeException("DB Connection Timeout"));

        // Must not throw any exception
        assertDoesNotThrow(() ->
                auditService.log(1L, "LOGIN_FAILED", "USER", null, "Failed login"));
    }

    @Test
    public void testAuditRepositoryFailureDoesNotThrow() {
        User user = new User();
        user.setId(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("Disk full"));

        // Must not throw
        assertDoesNotThrow(() ->
                auditService.log(2L, "DELETE", "DOCUMENT", 50L, "Deleted document"));
    }

    @Test
    public void testGetAuditLogs_ReturnsMappedDTOPage() {
        User user = new User();
        user.setId(1L);
        user.setName("Alice");
        user.setEmail("alice@test.com");

        AuditLog log = new AuditLog(user, "DOWNLOAD", "DOCUMENT", 10L, "127.0.0.1", "Downloaded file");
        log.setId(1L);
        log.setTimestamp(LocalDateTime.now());

        Page<AuditLog> page = new PageImpl<>(List.of(log));
        when(auditLogRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

        Page<AuditLogDTO> result = auditService.getAuditLogs("alice@test.com", "DOWNLOAD", "DOCUMENT",
                null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("DOWNLOAD", result.getContent().get(0).getAction());
        assertEquals("alice@test.com", result.getContent().get(0).getUserEmail());
    }

    @Test
    public void testExportAuditLogsToCsv_ProducesValidCsv() {
        User user = new User();
        user.setId(1L);
        user.setName("Bob Reviewer");
        user.setEmail("bob@securevault.com");

        AuditLog log = new AuditLog(user, "APPROVE", "DOCUMENT_APPROVAL", 20L, "192.168.1.100", "Approved doc");
        log.setId(101L);
        log.setTimestamp(LocalDateTime.of(2026, 10, 4, 12, 0));

        when(auditLogRepository.findAll(any(Specification.class))).thenReturn(List.of(log));

        byte[] csv = auditService.exportAuditLogsToCsv(null, "APPROVE", null, null, null);
        assertNotNull(csv);
        String csvContent = new String(csv, StandardCharsets.UTF_8);

        assertTrue(csvContent.contains("ID,Timestamp,User Name,User Email,Action,Entity Type,Entity ID,Client IP,Details"));
        assertTrue(csvContent.contains("Bob Reviewer"));
        assertTrue(csvContent.contains("bob@securevault.com"));
        assertTrue(csvContent.contains("APPROVE"));
    }

    @Test
    public void testLog_ByEmail_SavesSuccessfully() {
        User user = new User();
        user.setId(2L);
        user.setName("Jane");
        user.setEmail("jane@example.com");

        when(userRepository.findByEmail("jane@example.com")).thenReturn(Optional.of(user));

        auditService.log("jane@example.com", "LOGIN", "AUTH", 2L, "User logged in");

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    public void testGetClientIp_DefaultWhenNoRequestContext() {
        String ip = auditService.getClientIp();
        assertEquals("127.0.0.1", ip);
    }
}
