package com.securevault.service;

import com.securevault.dto.ActivityLogDTO;
import com.securevault.entity.AuditLog;
import com.securevault.entity.User;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActivityLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public ActivityLogService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    private ActivityLogDTO mapToDTO(AuditLog log) {
        return new ActivityLogDTO(
                log.getId(),
                log.getUser() != null ? log.getUser().getId() : null,
                log.getEntityId(),
                log.getAction(),
                log.getTimestamp()
        );
    }

    @Transactional(readOnly = true)
    public List<ActivityLogDTO> getAllLogs() {
        return auditLogRepository.findAllByOrderByTimestampDesc().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional
    public ActivityLogDTO saveLog(ActivityLogDTO dto) {
        User user = null;
        if (dto.getUserId() != null) {
            user = userRepository.findById(dto.getUserId()).orElse(null);
        }

        AuditLog log = new AuditLog(
                user,
                dto.getAction() != null ? dto.getAction() : "ACTION",
                "DOCUMENT",
                dto.getDocumentId(),
                "127.0.0.1",
                "Activity recorded"
        );
        if (dto.getTimestamp() != null) {
            log.setTimestamp(dto.getTimestamp());
        } else {
            log.setTimestamp(LocalDateTime.now());
        }

        AuditLog saved = auditLogRepository.save(log);
        return mapToDTO(saved);
    }

    @Transactional
    public void deleteLog(Long act_id) {
        if (!auditLogRepository.existsById(act_id)) {
            throw new ResourceNotFoundException("Activity log not found with id: " + act_id);
        }
        auditLogRepository.deleteById(act_id);
    }
}
