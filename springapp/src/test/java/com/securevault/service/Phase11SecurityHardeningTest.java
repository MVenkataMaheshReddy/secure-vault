package com.securevault.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.securevault.controller.UserController;
import com.securevault.dto.RegisterDTO;
import com.securevault.dto.UserDTO;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.security.JwtUtil;
import com.securevault.security.LoginAttemptService;
import com.securevault.util.FileValidationUtil;
import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@DisplayName("Phase 11: Security Hardening Verification Tests")
class Phase11SecurityHardeningTest {

    private LoginAttemptService loginAttemptService;
    private FileValidationUtil fileValidationUtil;
    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        loginAttemptService = new LoginAttemptService(5, 15);
        fileValidationUtil = new FileValidationUtil(10 * 1024 * 1024L, 5 * 1024 * 1024L);
        fileStorageService = new FileStorageService("target/test-storage-" + System.currentTimeMillis(), fileValidationUtil);
    }

    // =========================================================================
    // 1. AUTH & BRUTE FORCE PROTECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Brute Force: Locks account after 5 failed login attempts per email+IP")
    void testBruteForceLockoutAfterMaxAttempts() {
        String email = "victim@securevault.com";
        String ip = "192.168.1.100";

        assertFalse(loginAttemptService.isBlocked(email, ip));
        assertEquals(5, loginAttemptService.getRemainingAttempts(email, ip));

        for (int i = 1; i <= 4; i++) {
            loginAttemptService.loginFailed(email, ip);
            assertFalse(loginAttemptService.isBlocked(email, ip), "Should not be blocked after attempt " + i);
            assertEquals(5 - i, loginAttemptService.getRemainingAttempts(email, ip));
        }

        // 5th failed attempt triggers lockout
        loginAttemptService.loginFailed(email, ip);
        assertTrue(loginAttemptService.isBlocked(email, ip), "Should be blocked on 5th failed attempt");
        assertEquals(0, loginAttemptService.getRemainingAttempts(email, ip));
    }

    @Test
    @DisplayName("Brute Force: Successful login resets failed attempt counter")
    void testBruteForceResetOnSuccess() {
        String email = "user@securevault.com";
        String ip = "10.0.0.5";

        loginAttemptService.loginFailed(email, ip);
        loginAttemptService.loginFailed(email, ip);
        loginAttemptService.loginFailed(email, ip);
        assertEquals(2, loginAttemptService.getRemainingAttempts(email, ip));

        // Successful login resets state
        loginAttemptService.loginSucceeded(email, ip);
        assertFalse(loginAttemptService.isBlocked(email, ip));
        assertEquals(5, loginAttemptService.getRemainingAttempts(email, ip));
    }

    @Test
    @DisplayName("Brute Force: Different IP does not block legitimate user from other IP")
    void testBruteForceIpIsolation() {
        String email = "target@securevault.com";
        String attackerIp = "203.0.113.42";
        String legitimateIp = "198.51.100.1";

        for (int i = 0; i < 5; i++) {
            loginAttemptService.loginFailed(email, attackerIp);
        }

        assertTrue(loginAttemptService.isBlocked(email, attackerIp));
        assertFalse(loginAttemptService.isBlocked(email, legitimateIp), "Legitimate IP should not be blocked by attacker IP attempts");
    }

    @Test
    @DisplayName("JWT: Rejects weak secrets (< 256 bits / 32 bytes) on startup")
    void testJwtRejectsWeakSecret() {
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil("too-short-secret-key"));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(""));
        assertThrows(IllegalArgumentException.class, () -> new JwtUtil(null));
    }

    @Test
    @DisplayName("JWT: Accepts 256-bit (>= 32 bytes) secret key")
    void testJwtAcceptsStrongSecret() {
        String strongSecret = "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8S9t0U1v2W3x4";
        JwtUtil util = new JwtUtil(strongSecret);
        assertNotNull(util);
    }

    // =========================================================================
    // 2. FILE SECURITY & MACRO / EXTENSION REJECTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Files: Rejects .doc and .docm macro files with BadRequestException")
    void testRejectsMacroFormats() {
        MockMultipartFile docFile = new MockMultipartFile(
                "file", "invoice.doc", "application/msword", new byte[]{1, 2, 3}
        );
        assertThrows(BadRequestException.class, () -> fileValidationUtil.validateFile(docFile));

        MockMultipartFile docmFile = new MockMultipartFile(
                "file", "payload.docm", "application/vnd.ms-word.document.macroEnabled.12", new byte[]{1, 2, 3}
        );
        assertThrows(BadRequestException.class, () -> fileValidationUtil.validateFile(docmFile));
    }

    @Test
    @DisplayName("Files: Rejects path traversal characters in uploaded filename")
    void testRejectsPathTraversalInFilename() {
        MockMultipartFile traversalFile = new MockMultipartFile(
                "file", "../../etc/passwd.pdf", "application/pdf", new byte[]{1, 2, 3}
        );
        assertThrows(BadRequestException.class, () -> fileValidationUtil.validateFile(traversalFile));
    }

    @Test
    @DisplayName("Files: Storage loadFileAsResource blocks canonical path traversal")
    void testStorageBlocksPathTraversalOnLoad() {
        assertThrows(BadRequestException.class, () -> {
            fileStorageService.loadFileAsResource("/etc/passwd");
        });
        assertThrows(BadRequestException.class, () -> {
            fileStorageService.loadFileAsBytes("../../../etc/shadow");
        });
    }

    // =========================================================================
    // 3. IDOR & ACCESS CONTROL TESTS
    // =========================================================================

    @Test
    @DisplayName("Access Control: UserController blocks regular user from accessing another user's profile (IDOR)")
    void testUserControllerPreventsIdor() {
        UserService mockUserService = mock(UserService.class);
        UserController userController = new UserController(mockUserService);

        UserDTO victim = new UserDTO(42L, "Victim User", "victim@securevault.com", "ROLE_EMPLOYEE", Set.of("EMPLOYEE"), true);
        when(mockUserService.getUserById(42L)).thenReturn(victim);

        Authentication attackerAuth = mock(Authentication.class);
        when(attackerAuth.getName()).thenReturn("attacker@securevault.com");
        when(attackerAuth.getAuthorities()).thenReturn((java.util.Collection) Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        // Attempting to read another user's profile must fail with ForbiddenException
        assertThrows(ForbiddenException.class, () -> {
            userController.getUserById(42L, attackerAuth);
        });

        // Attempting to update another user's profile must fail with ForbiddenException
        RegisterDTO updateDto = new RegisterDTO("Hacked Name", "victim@securevault.com", "newpass123", "ADMIN");
        assertThrows(ForbiddenException.class, () -> {
            userController.updateUser(42L, updateDto, attackerAuth);
        });
    }

    @Test
    @DisplayName("Access Control: UserController prevents non-admin privilege escalation during profile update")
    void testUserControllerPreventsPrivilegeEscalation() {
        UserService mockUserService = mock(UserService.class);
        UserController userController = new UserController(mockUserService);

        UserDTO self = new UserDTO(10L, "Self User", "self@securevault.com", "ROLE_EMPLOYEE", Set.of("EMPLOYEE"), true);
        when(mockUserService.getUserById(10L)).thenReturn(self);

        Authentication selfAuth = mock(Authentication.class);
        when(selfAuth.getName()).thenReturn("self@securevault.com");
        when(selfAuth.getAuthorities()).thenReturn((java.util.Collection) Collections.singletonList(new SimpleGrantedAuthority("ROLE_EMPLOYEE")));

        RegisterDTO updateDto = new RegisterDTO("Self Updated", "self@securevault.com", "newpass123", "ADMIN");
        userController.updateUser(10L, updateDto, selfAuth);

        // Role should have been stripped to null to prevent self-elevation to ADMIN
        assertEquals(null, updateDto.getRole(), "Non-admin role update attempt must be stripped to prevent privilege escalation");
    }
}
