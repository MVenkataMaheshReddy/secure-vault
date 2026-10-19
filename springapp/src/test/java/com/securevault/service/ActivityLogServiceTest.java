package com.securevault.service;

import com.securevault.dto.ActivityLogDTO;
import com.securevault.entity.AuditLog;
import com.securevault.entity.User;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActivityLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActivityLogService activityLogService;

    private User sampleUser;
    private AuditLog sampleLog;

    @BeforeEach
    void setUp() {
        sampleUser = new User("Alice", "alice@example.com", "hash");
        sampleUser.setId(10L);

        sampleLog = new AuditLog(sampleUser, "DOWNLOAD", "DOCUMENT", 100L, "127.0.0.1", "Downloaded file");
        sampleLog.setId(50L);
        sampleLog.setTimestamp(LocalDateTime.now());
    }

    @Test
    void testGetAllLogs_ReturnsMappedList() {
        when(auditLogRepository.findAllByOrderByTimestampDesc()).thenReturn(Arrays.asList(sampleLog));

        List<ActivityLogDTO> logs = activityLogService.getAllLogs();

        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals(50L, logs.get(0).getAct_id());
        assertEquals(10L, logs.get(0).getUserId());
        assertEquals(100L, logs.get(0).getDocumentId());
        assertEquals("DOWNLOAD", logs.get(0).getAction());
        verify(auditLogRepository, times(1)).findAllByOrderByTimestampDesc();
    }

    @Test
    void testSaveLog_WithUserIdAndTimestamp() {
        ActivityLogDTO inputDto = new ActivityLogDTO(null, 10L, 100L, "VIEW", LocalDateTime.now());

        when(userRepository.findById(10L)).thenReturn(Optional.of(sampleUser));
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog al = inv.getArgument(0);
            al.setId(99L);
            return al;
        });

        ActivityLogDTO savedDto = activityLogService.saveLog(inputDto);

        assertNotNull(savedDto);
        assertEquals(99L, savedDto.getAct_id());
        assertEquals(10L, savedDto.getUserId());
        assertEquals(100L, savedDto.getDocumentId());
        assertEquals("VIEW", savedDto.getAction());
        verify(userRepository, times(1)).findById(10L);
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testSaveLog_WithNullUserAndNullTimestamp() {
        ActivityLogDTO inputDto = new ActivityLogDTO(null, null, 101L, null, null);

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(inv -> {
            AuditLog al = inv.getArgument(0);
            al.setId(100L);
            return al;
        });

        ActivityLogDTO savedDto = activityLogService.saveLog(inputDto);

        assertNotNull(savedDto);
        assertEquals(100L, savedDto.getAct_id());
        assertNull(savedDto.getUserId());
        assertEquals("ACTION", savedDto.getAction());
        assertNotNull(savedDto.getTimestamp());
        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void testDeleteLog_Success() {
        when(auditLogRepository.existsById(50L)).thenReturn(true);
        doNothing().when(auditLogRepository).deleteById(50L);

        activityLogService.deleteLog(50L);

        verify(auditLogRepository, times(1)).deleteById(50L);
    }

    @Test
    void testDeleteLog_NotFoundThrowsException() {
        when(auditLogRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> activityLogService.deleteLog(999L));
        verify(auditLogRepository, never()).deleteById(anyLong());
    }
}
