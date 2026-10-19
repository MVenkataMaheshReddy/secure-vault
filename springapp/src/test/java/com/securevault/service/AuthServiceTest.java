package com.securevault.service;

import com.securevault.dto.AuthResponse;
import com.securevault.dto.LoginRequest;
import com.securevault.dto.RegisterRequest;
import com.securevault.dto.UserDTO;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.exception.UnauthorizedException;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import com.securevault.security.JwtUtil;
import com.securevault.security.LoginAttemptService;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private AuditService auditService;

    @Mock
    private LoginAttemptService loginAttemptService;

    private AuthService authService;

    private Role employeeRole;

    @BeforeEach
    void setUp() {
        employeeRole = new Role(RoleName.EMPLOYEE);
        employeeRole.setId(2L);

        authService = new AuthService(
                userRepository,
                roleRepository,
                passwordEncoder,
                jwtUtil,
                auditService,
                loginAttemptService
        );
    }

    @Test
    void testRegister_Ok() {
        RegisterRequest request = new RegisterRequest("David Miller", "david@example.com", "Password123");

        when(userRepository.existsByEmail("david@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode("Password123")).thenReturn("encodedPassword123");

        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(101L);
            return saved;
        });

        UserDTO result = authService.register(request);

        assertNotNull(result);
        assertEquals(101L, result.getId());
        assertEquals("David Miller", result.getName());
        assertEquals("david@example.com", result.getEmail());
        assertTrue(result.getRoles().contains("EMPLOYEE"));
        verify(userRepository, times(1)).save(any(User.class));
        verify(auditService, times(1)).log(eq(101L), eq("REGISTER"), eq("USER"), eq(101L), anyString());
    }

    @Test
    void testRegister_BlankNameThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest("   ", "david@example.com", "Password123");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Name is required"));
    }

    @Test
    void testRegister_InvalidEmailThrowsBadRequest() {
        RegisterRequest request = new RegisterRequest("David", "not-an-email", "Password123");
        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("valid email"));
    }

    @Test
    void testRegister_WeakPasswordThrowsBadRequest() {
        RegisterRequest request1 = new RegisterRequest("David", "david@example.com", "short");
        assertThrows(BadRequestException.class, () -> authService.register(request1));

        RegisterRequest request2 = new RegisterRequest("David", "david@example.com", "alllettersnodigits");
        assertThrows(BadRequestException.class, () -> authService.register(request2));

        RegisterRequest request3 = new RegisterRequest("David", "david@example.com", "1234567890");
        assertThrows(BadRequestException.class, () -> authService.register(request3));
    }

    @Test
    void testRegister_DuplicateEmail() {
        RegisterRequest request = new RegisterRequest("David Miller", "david@example.com", "Password123");

        when(userRepository.existsByEmail("david@example.com")).thenReturn(true);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> authService.register(request));
        assertTrue(ex.getMessage().contains("Email is already registered"));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testLogin_Ok() {
        LoginRequest request = new LoginRequest("david@example.com", "Password123");

        User user = new User("David Miller", "david@example.com", "encodedPassword123");
        user.setId(101L);
        user.setEnabled(true);
        user.setRoles(new HashSet<>(Collections.singletonList(employeeRole)));

        when(loginAttemptService.isBlocked("david@example.com", "192.168.1.1")).thenReturn(false);
        when(userRepository.findByEmail("david@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "encodedPassword123")).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("mockJwtToken123");

        AuthResponse response = authService.login(request, "192.168.1.1");

        assertNotNull(response);
        assertEquals("mockJwtToken123", response.getToken());
        assertNotNull(response.getUser());
        assertEquals(101L, response.getUser().getId());
        assertEquals("david@example.com", response.getUser().getEmail());
        verify(loginAttemptService, times(1)).loginSucceeded("david@example.com", "192.168.1.1");
    }

    @Test
    void testLogin_NullCredentials() {
        LoginRequest request = new LoginRequest(null, null);

        assertThrows(UnauthorizedException.class, () -> authService.login(request, "127.0.0.1"));
    }

    @Test
    void testLogin_AccountLockedDueToBruteForce() {
        LoginRequest request = new LoginRequest("victim@example.com", "Password123");
        when(loginAttemptService.isBlocked("victim@example.com", "1.2.3.4")).thenReturn(true);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(request, "1.2.3.4"));
        assertTrue(ex.getMessage().contains("Too many failed login attempts"));
    }

    @Test
    void testLogin_UserNotFound() {
        LoginRequest request = new LoginRequest("ghost@example.com", "Password123");

        when(loginAttemptService.isBlocked("ghost@example.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
        verify(loginAttemptService, times(1)).loginFailed("ghost@example.com", "127.0.0.1");
    }

    @Test
    void testLogin_UserDisabled() {
        LoginRequest request = new LoginRequest("disabled@example.com", "Password123");

        User user = new User("Disabled", "disabled@example.com", "encoded");
        user.setId(200L);
        user.setEnabled(false);

        when(loginAttemptService.isBlocked("disabled@example.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(request));
        assertEquals("User account is disabled", ex.getMessage());
    }

    @Test
    void testLogin_WrongPassword() {
        LoginRequest request = new LoginRequest("david@example.com", "WrongPassword999");

        User user = new User("David Miller", "david@example.com", "encodedPassword123");
        user.setId(101L);
        user.setEnabled(true);

        when(loginAttemptService.isBlocked("david@example.com", "127.0.0.1")).thenReturn(false);
        when(userRepository.findByEmail("david@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword999", "encodedPassword123")).thenReturn(false);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class, () -> authService.login(request));
        assertEquals("Invalid email or password", ex.getMessage());
        verify(loginAttemptService, times(1)).loginFailed("david@example.com", "127.0.0.1");
    }

    @Test
    void testGetCurrentUser_Success() {
        User user = new User("Alice", "alice@example.com", "pass");
        user.setId(1L);
        user.setEnabled(true);
        user.setRoles(Collections.singleton(employeeRole));

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        UserDTO dto = authService.getCurrentUser("alice@example.com");

        assertNotNull(dto);
        assertEquals("Alice", dto.getName());
        assertEquals("alice@example.com", dto.getEmail());
    }

    @Test
    void testGetCurrentUser_NotFoundThrowsException() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByName("unknown@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> authService.getCurrentUser("unknown@example.com"));
    }
}
