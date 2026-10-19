package com.securevault.service;

import com.securevault.dto.AuthResponse;
import com.securevault.dto.LoginRequest;
import com.securevault.dto.RegisterRequest;
import com.securevault.dto.UserDTO;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.exception.UnauthorizedException;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.JwtUtil;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final com.securevault.security.LoginAttemptService loginAttemptService;

    @Value("${app.admin.registration-code:${ADMIN_REGISTRATION_CODE:ADMIN_SECRET_2025}}")
    private String adminRegistrationCode = "ADMIN_SECRET_2025";

    public void setAdminRegistrationCode(String adminRegistrationCode) {
        this.adminRegistrationCode = adminRegistrationCode;
    }

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this(userRepository, roleRepository, passwordEncoder, jwtUtil, null, null);
    }

    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       AuditService auditService) {
        this(userRepository, roleRepository, passwordEncoder, jwtUtil, auditService, null);
    }

    @Autowired
    public AuthService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil,
                       @Autowired(required = false) AuditService auditService,
                       @Autowired(required = false) com.securevault.security.LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.auditService = auditService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public UserDTO register(RegisterRequest request) {
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BadRequestException("Name is required");
        }

        if (request.getEmail() == null || request.getEmail().trim().isEmpty() || !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            throw new BadRequestException("A valid email address is required");
        }

        String email = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email is already registered: " + email);
        }

        String password = request.getPassword();
        if (password == null || password.length() < 8 || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BadRequestException("Password must be at least 8 characters long and contain at least one letter and one number");
        }

        String requestedRole = request.getRole() != null && !request.getRole().isBlank()
                ? request.getRole().trim().toUpperCase().replace("ROLE_", "")
                : "EMPLOYEE";

        Role assignedRole;
        if ("ADMIN".equals(requestedRole)) {
            String adminCode = request.getAdminCode();
            if (adminCode == null || !adminCode.equals(adminRegistrationCode)) {
                throw new ForbiddenException("Invalid admin access code");
            }
            assignedRole = roleRepository.findByName(RoleName.ADMIN)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.ADMIN)));
        } else if ("EMPLOYEE".equals(requestedRole)) {
            assignedRole = roleRepository.findByName(RoleName.EMPLOYEE)
                    .orElseGet(() -> roleRepository.save(new Role(RoleName.EMPLOYEE)));
        } else {
            throw new BadRequestException("Invalid role selected: " + request.getRole());
        }

        User user = new User();
        user.setName(request.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);

        Set<Role> roles = new HashSet<>();
        roles.add(assignedRole);
        user.setRoles(roles);

        User savedUser = userRepository.save(user);

        if (auditService != null) {
            auditService.log(savedUser.getId(), "REGISTER", "USER", savedUser.getId(),
                    "User registered with email: " + savedUser.getEmail() + " as role: " + assignedRole.getName());
        }

        return mapToDTO(savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        return login(request, "127.0.0.1");
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, String clientIp) {
        String safeIp = (clientIp != null && !clientIp.isBlank()) ? clientIp.trim() : "127.0.0.1";

        if (request.getEmail() == null || request.getPassword() == null) {
            if (auditService != null) {
                auditService.log((Long) null, "LOGIN_FAILED", "USER", null, "Login attempt with missing credentials from IP: " + safeIp);
            }
            throw new UnauthorizedException("Invalid email or password");
        }

        String email = request.getEmail().trim().toLowerCase();

        // 1. Check if email + IP combination is locked out due to brute force attacks
        if (loginAttemptService != null && loginAttemptService.isBlocked(email, safeIp)) {
            if (auditService != null) {
                auditService.log((Long) null, "LOGIN_THROTTLED", "USER", null,
                        "Brute-force login blocked for email: " + email + " from IP: " + safeIp);
            }
            throw new UnauthorizedException("Too many failed login attempts. Account temporarily locked for 15 minutes. Please try again later.");
        }

        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isEmpty()) {
            if (loginAttemptService != null) {
                loginAttemptService.loginFailed(email, safeIp);
            }
            if (auditService != null) {
                auditService.log((Long) null, "LOGIN_FAILED", "USER", null, "Failed login attempt for email: " + email + " from IP: " + safeIp);
            }
            throw new UnauthorizedException("Invalid email or password");
        }

        User user = userOpt.get();

        if (!user.isEnabled()) {
            if (auditService != null) {
                auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), "Login attempted for disabled account: " + email);
            }
            throw new UnauthorizedException("User account is disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            if (loginAttemptService != null) {
                loginAttemptService.loginFailed(email, safeIp);
            }
            if (auditService != null) {
                auditService.log(user.getId(), "LOGIN_FAILED", "USER", user.getId(), "Failed login attempt: incorrect password from IP: " + safeIp);
            }
            throw new UnauthorizedException("Invalid email or password");
        }

        // On successful authentication, reset failed attempts counter
        if (loginAttemptService != null) {
            loginAttemptService.loginSucceeded(email, safeIp);
        }

        // Verify selected role if provided
        String requestedRole = request.getRole() != null && !request.getRole().isBlank()
                ? request.getRole().trim().toUpperCase().replace("ROLE_", "")
                : null;

        RoleName activeRole;
        if (requestedRole != null) {
            boolean hasRole = user.getRoles().stream()
                    .anyMatch(r -> r.getName().name().equalsIgnoreCase(requestedRole));
            if (!hasRole) {
                String roleMsg = request.getRole().trim();
                throw new ForbiddenException("You are not registered as " + roleMsg);
            }
            try {
                activeRole = RoleName.valueOf(requestedRole);
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("Invalid role: " + requestedRole);
            }
        } else {
            activeRole = user.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN)
                    ? RoleName.ADMIN : RoleName.EMPLOYEE;
        }

        List<String> authorities = Collections.singletonList("ROLE_" + activeRole.name());
        String token = (requestedRole != null)
                ? jwtUtil.generateToken(user.getEmail(), user.getId(), authorities)
                : jwtUtil.generateToken(user);
        UserDTO userDTO = mapToDTO(user);
        userDTO.setRole(activeRole.name());

        if (auditService != null) {
            auditService.log(user.getId(), "LOGIN_SUCCESS", "USER", user.getId(), "User logged in successfully as " + activeRole + " from IP: " + safeIp);
        }

        return new AuthResponse(token, userDTO);
    }

    @Transactional(readOnly = true)
    public UserDTO getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> userRepository.findByName(email)
                        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email)));

        return mapToDTO(user);
    }

    public UserDTO mapToDTO(User user) {
        String primaryRole = "ROLE_EMPLOYEE";
        Set<String> roleNames = new HashSet<>();

        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            roleNames = user.getRoles().stream()
                    .map(r -> r.getName().name())
                    .collect(Collectors.toSet());
            Role firstRole = user.getRoles().iterator().next();
            primaryRole = "ROLE_" + firstRole.getName().name();
        } else {
            roleNames.add("EMPLOYEE");
        }

        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                primaryRole,
                roleNames,
                user.isEnabled()
        );
    }
}
