package com.securevault.controller;

import com.securevault.dto.AuthResponse;
import com.securevault.dto.LoginRequest;
import com.securevault.dto.RegisterRequest;
import com.securevault.dto.UserDTO;
import com.securevault.entity.RevokedToken;
import com.securevault.repository.RevokedTokenRepository;
import com.securevault.service.AuditService;
import com.securevault.service.AuthService;
import java.util.HashMap;
import java.util.Map;
import javax.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. Authentication", description = "Endpoints for user registration, login authentication, current user retrieval, and token revocation")
@RestController
@RequestMapping({"/api/auth", "/auth"})
public class AuthController {

    private final AuthService authService;
    private final RevokedTokenRepository revokedTokenRepository;
    private final AuditService auditService;

    public AuthController(AuthService authService,
                          RevokedTokenRepository revokedTokenRepository) {
        this(authService, revokedTokenRepository, null);
    }

    @Autowired
    public AuthController(AuthService authService,
                          RevokedTokenRepository revokedTokenRepository,
                          @Autowired(required = false) AuditService auditService) {
        this.authService = authService;
        this.revokedTokenRepository = revokedTokenRepository;
        this.auditService = auditService;
    }

    @Operation(summary = "Register new user", description = "Registers a new user account with default EMPLOYEE role and returns created user profile")
    @PostMapping("/register")
    public ResponseEntity<UserDTO> register(@Valid @RequestBody RegisterRequest request) {
        UserDTO userDTO = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(userDTO);
    }

    @Operation(summary = "Authenticate user credentials", description = "Validates user credentials, increments brute-force counter on failure, and issues JWT access token on success")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            javax.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        AuthResponse response = authService.login(request, clientIp);
        return ResponseEntity.ok(response);
    }

    private String getClientIp(javax.servlet.http.HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1";
    }

    @Operation(summary = "Get current authenticated user profile", description = "Retrieves user details for the active JWT Bearer principal")
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UserDTO currentUser = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(currentUser);
    }

    @Operation(summary = "Logout user and revoke token", description = "Revokes the active JWT access token by adding its signature to the revocation table")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @RequestHeader(value = "Authorization", required = false) String tokenHeader,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication) {
        String token = null;
        if (tokenHeader != null && tokenHeader.startsWith("Bearer ")) {
            token = tokenHeader.substring(7).trim();
        } else if (body != null && body.containsKey("refreshToken")) {
            token = body.get("refreshToken");
        } else if (body != null && body.containsKey("token")) {
            token = body.get("token");
        }

        if (token != null && !token.isBlank()) {
            revokedTokenRepository.save(new RevokedToken(token));
        }

        if (auditService != null) {
            String userEmail = authentication != null ? authentication.getName() : null;
            auditService.log(userEmail, "LOGOUT", "USER", null, "User logged out");
        }

        Map<String, String> resp = new HashMap<>();
        resp.put("message", "Logged out successfully");
        return ResponseEntity.ok(resp);
    }
}
