package com.securevault.service;

import com.securevault.dto.AuditLogDTO;
import com.securevault.entity.AuditLog;
import com.securevault.entity.User;
import com.securevault.repository.AuditLogRepository;
import com.securevault.repository.UserRepository;
import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.criteria.Predicate;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditService {

    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    private static final DateTimeFormatter CSV_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    public AuditService(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    /**
     * Resolves client IP address safely from current HTTP request, respecting X-Forwarded-For.
     */
    public String getClientIp() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String xfHeader = request.getHeader("X-Forwarded-For");
                if (xfHeader != null && !xfHeader.trim().isEmpty()) {
                    String candidate = xfHeader.split(",")[0].trim();
                    if (isValidIp(candidate)) {
                        return candidate;
                    }
                }
                String remoteAddr = request.getRemoteAddr();
                if (remoteAddr != null && !remoteAddr.trim().isEmpty()) {
                    return remoteAddr.trim();
                }
            }
        } catch (Exception e) {
            logger.debug("Could not resolve client IP: {}", e.getMessage());
        }
        return "127.0.0.1";
    }

    private boolean isValidIp(String ip) {
        if (ip == null || ip.isBlank() || ip.length() > 45) {
            return false;
        }
        // Basic check against injection or corrupted headers
        return ip.matches("^[0-9a-fA-F:.]+$");
    }

    /**
     * Audit log persistence with REQUIRES_NEW propagation.
     * Guaranteed to never throw an exception or interrupt user workflow.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(Long userId, String action, String entityType, Long entityId, String details) {
        try {
            User user = null;
            if (userId != null) {
                user = userRepository.findById(userId).orElse(null);
            }
            String ip = getClientIp();
            AuditLog auditLog = new AuditLog(user, action, entityType, entityId, ip, details);
            auditLogRepository.save(auditLog);
            logger.debug("AuditLog written: action={}, entityType={}, entityId={}, user={}",
                    action, entityType, entityId, user != null ? user.getEmail() : "anonymous");
        } catch (Exception ex) {
            logger.error("Failed to write audit log: action={}, entityType={}, entityId={}. Error: {}",
                    action, entityType, entityId, ex.getMessage(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String userEmail, String action, String entityType, Long entityId, String details) {
        try {
            User user = null;
            if (userEmail != null && !userEmail.isBlank()) {
                user = userRepository.findByEmail(userEmail).orElse(null);
            }
            String ip = getClientIp();
            AuditLog auditLog = new AuditLog(user, action, entityType, entityId, ip, details);
            auditLogRepository.save(auditLog);
            logger.debug("AuditLog written: action={}, entityType={}, entityId={}, user={}",
                    action, entityType, entityId, userEmail);
        } catch (Exception ex) {
            logger.error("Failed to write audit log by email: action={}, entityType={}, entityId={}. Error: {}",
                    action, entityType, entityId, ex.getMessage(), ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logPublic(String action, String entityType, Long entityId, String details) {
        log((User) null, action, entityType, entityId, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(User user, String action, String entityType, Long entityId, String details) {
        try {
            String ip = getClientIp();
            AuditLog auditLog = new AuditLog(user, action, entityType, entityId, ip, details);
            auditLogRepository.save(auditLog);
            logger.debug("AuditLog written: action={}, entityType={}, entityId={}, user={}",
                    action, entityType, entityId, user != null ? user.getEmail() : "anonymous");
        } catch (Exception ex) {
            logger.error("Failed to write audit log with user object: action={}, entityType={}, entityId={}. Error: {}",
                    action, entityType, entityId, ex.getMessage(), ex);
        }
    }

    /**
     * Admin query method for audit logs with flexible criteria and pagination.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogDTO> getAuditLogs(String userEmail, String action, String entityType,
                                          LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Specification<AuditLog> spec = createSpecification(userEmail, action, entityType, from, to);
        return auditLogRepository.findAll(spec, pageable).map(this::mapToDTO);
    }

    /**
     * Admin export to CSV format.
     */
    @Transactional(readOnly = true)
    public byte[] exportAuditLogsToCsv(String userEmail, String action, String entityType,
                                       LocalDateTime from, LocalDateTime to) {
        Specification<AuditLog> spec = createSpecification(userEmail, action, entityType, from, to);
        List<AuditLog> logs = auditLogRepository.findAll(spec);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, true, StandardCharsets.UTF_8)) {
            // Write UTF-8 BOM for Excel compatibility
            out.write(0xEF);
            out.write(0xBB);
            out.write(0xBF);

            writer.println("ID,Timestamp,User Name,User Email,Action,Entity Type,Entity ID,Client IP,Details");
            for (AuditLog log : logs) {
                String userName = log.getUser() != null ? log.getUser().getName() : "Anonymous/Public";
                String email = log.getUser() != null ? log.getUser().getEmail() : "N/A";
                String timeStr = log.getTimestamp() != null ? log.getTimestamp().format(CSV_DATE_FORMAT) : "";
                String entityIdStr = log.getEntityId() != null ? String.valueOf(log.getEntityId()) : "";

                writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s",
                        escapeCsv(String.valueOf(log.getId())),
                        escapeCsv(timeStr),
                        escapeCsv(userName),
                        escapeCsv(email),
                        escapeCsv(log.getAction()),
                        escapeCsv(log.getEntityType()),
                        escapeCsv(entityIdStr),
                        escapeCsv(log.getIpAddress() != null ? log.getIpAddress() : ""),
                        escapeCsv(log.getDetails() != null ? log.getDetails() : "")
                ));
            }
            writer.flush();
        } catch (Exception e) {
            logger.error("Failed to generate CSV for audit logs", e);
        }
        return out.toByteArray();
    }

    private Specification<AuditLog> createSpecification(String userEmail, String action,
                                                        String entityType, LocalDateTime from,
                                                        LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userEmail != null && !userEmail.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("user").get("email")), userEmail.trim().toLowerCase()));
            }

            if (action != null && !action.trim().isEmpty() && !action.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(cb.upper(root.get("action")), action.trim().toUpperCase()));
            }

            if (entityType != null && !entityType.trim().isEmpty() && !entityType.equalsIgnoreCase("ALL")) {
                predicates.add(cb.equal(cb.upper(root.get("entityType")), entityType.trim().toUpperCase()));
            }

            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("timestamp"), from));
            }

            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("timestamp"), to));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public AuditLogDTO mapToDTO(AuditLog log) {
        User u = log.getUser();
        return new AuditLogDTO(
                log.getId(),
                u != null ? u.getId() : null,
                u != null ? u.getName() : "Anonymous/System",
                u != null ? u.getEmail() : null,
                log.getAction(),
                log.getEntityType(),
                log.getEntityId(),
                log.getIpAddress(),
                log.getDetails(),
                log.getTimestamp()
        );
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        String clean = value.replace("\"", "\"\"");
        return "\"" + clean + "\"";
    }
}
