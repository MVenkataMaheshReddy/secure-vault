package com.securevault.service;

import com.securevault.dto.RegisterDTO;
import com.securevault.dto.UserDTO;
import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.exception.BadRequestException;
import com.securevault.exception.ForbiddenException;
import com.securevault.exception.ResourceNotFoundException;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditService auditService;

    @InjectMocks
    private UserService userService;

    private User sampleUser;
    private User adminUser;
    private Role employeeRole;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        employeeRole = new Role(1L, RoleName.EMPLOYEE);
        adminRole = new Role(2L, RoleName.ADMIN);

        sampleUser = new User("alice", "alice@example.com", "encodedPass");
        sampleUser.setId(1L);
        sampleUser.setEnabled(true);
        sampleUser.addRole(employeeRole);

        adminUser = new User("admin", "admin@example.com", "encodedAdminPass");
        adminUser.setId(2L);
        adminUser.setEnabled(true);
        adminUser.addRole(adminRole);
    }

    @Test
    void testRegisterUser_Success() {
        RegisterDTO dto = new RegisterDTO("alice", "alice@example.com", "Secret123!", "EMPLOYEE");

        when(userRepository.existsByName("alice")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode("Secret123!")).thenReturn("encodedPass");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        UserDTO result = userService.registerUser(dto);

        assertNotNull(result);
        assertEquals("alice", result.getName());
        assertEquals("alice@example.com", result.getEmail());
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void testRegisterUser_DuplicateUsernameThrowsBadRequest() {
        RegisterDTO dto = new RegisterDTO("alice", "alice@example.com", "Secret123!", "EMPLOYEE");
        when(userRepository.existsByName("alice")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> userService.registerUser(dto));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testRegisterUser_DuplicateEmailThrowsBadRequest() {
        RegisterDTO dto = new RegisterDTO("newuser", "alice@example.com", "Secret123!", "EMPLOYEE");
        when(userRepository.existsByName("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class, () -> userService.registerUser(dto));
    }

    @Test
    void testGetAllUsers_ReturnsList() {
        when(userRepository.findAll()).thenReturn(Arrays.asList(sampleUser, adminUser));

        List<UserDTO> users = userService.getAllUsers();

        assertNotNull(users);
        assertEquals(2, users.size());
    }

    @Test
    void testGetUserById_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        UserDTO result = userService.getUserById(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("alice", result.getName());
    }

    @Test
    void testGetUserById_NotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> userService.getUserById(99L));
    }

    @Test
    void testUpdateUserRoles_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findByName(RoleName.REVIEWER)).thenReturn(Optional.of(new Role(3L, RoleName.REVIEWER)));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        Set<String> newRoles = Collections.singleton("REVIEWER");
        UserDTO updated = userService.updateUserRoles(1L, newRoles, "admin@example.com");

        assertNotNull(updated);
        assertTrue(updated.getRoles().contains("REVIEWER"));
    }

    @Test
    void testUpdateUserRoles_AdminSelfDemotionForbidden() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

        Set<String> newRoles = Collections.singleton("EMPLOYEE");
        assertThrows(ForbiddenException.class, () ->
                userService.updateUserRoles(2L, newRoles, "admin@example.com")
        );
    }

    @Test
    void testUpdateUserRoles_EmptyRolesThrowsBadRequest() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertThrows(BadRequestException.class, () ->
                userService.updateUserRoles(1L, Collections.emptySet(), "admin@example.com")
        );
    }

    @Test
    void testUpdateUserEnabled_DisableUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO result = userService.updateUserEnabled(1L, false, "admin@example.com");

        assertNotNull(result);
        assertFalse(result.isEnabled());
    }

    @Test
    void testUpdateUserEnabled_AdminSelfDisableForbidden() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

        assertThrows(ForbiddenException.class, () ->
                userService.updateUserEnabled(2L, false, "admin@example.com")
        );
    }

    @Test
    void testUpdateUser_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(roleRepository.findByName(RoleName.EMPLOYEE)).thenReturn(Optional.of(employeeRole));
        when(passwordEncoder.encode("NewPass123!")).thenReturn("newHash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        RegisterDTO updateDto = new RegisterDTO("alice_updated", "alice_new@example.com", "NewPass123!", "EMPLOYEE");
        UserDTO result = userService.updateUser(1L, updateDto);

        assertNotNull(result);
        assertEquals("alice_updated", result.getName());
        assertEquals("alice_new@example.com", result.getEmail());
    }

    @Test
    void testDeleteUser_Success() {
        when(userRepository.existsById(1L)).thenReturn(true);
        doNothing().when(userRepository).deleteById(1L);

        userService.deleteUser(1L);

        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    void testDeleteUser_NotFoundThrowsException() {
        when(userRepository.existsById(999L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> userService.deleteUser(999L));
    }
}
