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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder) {
        this(userRepository, roleRepository, passwordEncoder, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UserService(UserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       @org.springframework.beans.factory.annotation.Autowired(required = false) AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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

    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public User getUserEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Transactional
    public UserDTO updateUserRoles(Long targetUserId, Set<String> newRoleNames, String currentAdminEmail) {
        User targetUser = getUserEntityById(targetUserId);

        if (newRoleNames == null || newRoleNames.isEmpty()) {
            throw new BadRequestException("At least one role must be provided");
        }

        Set<String> normalizedRoles = newRoleNames.stream()
                .map(r -> r.toUpperCase().replace("ROLE_", "").trim())
                .collect(Collectors.toSet());

        // Safeguard 1: Admin cannot demote themselves (cannot remove ADMIN role from self)
        if (currentAdminEmail != null && targetUser.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            if (!normalizedRoles.contains(RoleName.ADMIN.name())) {
                throw new ForbiddenException("Admins cannot demote themselves (cannot remove ADMIN role from your own account)");
            }
        }

        // Safeguard 2: At least one ADMIN must remain in the system
        boolean isCurrentlyAdmin = targetUser.getRoles() != null && targetUser.getRoles().stream()
                .anyMatch(r -> r.getName() == RoleName.ADMIN);

        if (isCurrentlyAdmin && !normalizedRoles.contains(RoleName.ADMIN.name())) {
            long totalAdmins = userRepository.findAll().stream()
                    .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN))
                    .count();
            if (totalAdmins <= 1) {
                throw new BadRequestException("At least one ADMIN must remain in the system");
            }
        }

        // Resolve roles
        Set<Role> updatedRoles = new HashSet<>();
        for (String rName : normalizedRoles) {
            try {
                RoleName roleEnum = RoleName.valueOf(rName);
                Role role = roleRepository.findByName(roleEnum)
                        .orElseGet(() -> roleRepository.save(new Role(roleEnum)));
                updatedRoles.add(role);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role specified: " + rName);
            }
        }

        targetUser.setRoles(updatedRoles);
        User savedUser = userRepository.save(targetUser);

        if (auditService != null) {
            auditService.log(currentAdminEmail, "ROLE_CHANGE", "USER", savedUser.getId(),
                    "Updated roles for " + savedUser.getEmail() + " to: " + normalizedRoles);
        }

        return mapToDTO(savedUser);
    }

    @Transactional
    public UserDTO updateUserEnabled(Long targetUserId, boolean enabled, String currentAdminEmail) {
        User targetUser = getUserEntityById(targetUserId);

        // Safeguards when attempting to disable an account
        if (!enabled) {
            // Safeguard 1: Admin cannot disable themselves
            if (currentAdminEmail != null && targetUser.getEmail().equalsIgnoreCase(currentAdminEmail)) {
                throw new ForbiddenException("Admins cannot disable their own account");
            }

            // Safeguard 2: At least one active ADMIN must remain in the system
            boolean isTargetAdmin = targetUser.getRoles() != null && targetUser.getRoles().stream()
                    .anyMatch(r -> r.getName() == RoleName.ADMIN);

            if (isTargetAdmin) {
                long activeAdmins = userRepository.findAll().stream()
                        .filter(User::isEnabled)
                        .filter(u -> u.getRoles() != null && u.getRoles().stream().anyMatch(r -> r.getName() == RoleName.ADMIN))
                        .count();
                if (activeAdmins <= 1) {
                    throw new BadRequestException("At least one active ADMIN must remain in the system");
                }
            }
        }

        targetUser.setEnabled(enabled);
        User savedUser = userRepository.save(targetUser);

        if (auditService != null) {
            auditService.log(currentAdminEmail, enabled ? "USER_ENABLE" : "USER_DISABLE", "USER", savedUser.getId(),
                    (enabled ? "Enabled" : "Disabled") + " user account: " + savedUser.getEmail());
        }

        return mapToDTO(savedUser);
    }

    @Transactional
    public UserDTO registerUser(RegisterDTO dto) {
        if (userRepository.existsByName(dto.getUsername())) {
            throw new BadRequestException("Username already exists: " + dto.getUsername());
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new BadRequestException("Email already exists: " + dto.getEmail());
        }

        RoleName targetRoleName = RoleName.EMPLOYEE;
        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            String roleClean = dto.getRole().toUpperCase().replace("ROLE_", "");
            try {
                targetRoleName = RoleName.valueOf(roleClean);
            } catch (IllegalArgumentException ex) {
                targetRoleName = RoleName.EMPLOYEE;
            }
        }

        final RoleName finalTargetRole = targetRoleName;
        Role assignedRole = roleRepository.findByName(finalTargetRole)
                .orElseGet(() -> roleRepository.save(new Role(finalTargetRole)));

        User user = new User();
        user.setName(dto.getUsername().trim());
        user.setEmail(dto.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        user.setEnabled(true);
        user.getRoles().add(assignedRole);

        User savedUser = userRepository.save(user);
        return mapToDTO(savedUser);
    }

    @Transactional
    public UserDTO updateUser(Long id, RegisterDTO dto) {
        User user = getUserEntityById(id);

        if (dto.getUsername() != null && !dto.getUsername().isBlank()) {
            user.setName(dto.getUsername().trim());
        }
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            user.setEmail(dto.getEmail().trim().toLowerCase());
        }
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(dto.getPassword()));
        }
        if (dto.getRole() != null && !dto.getRole().isBlank()) {
            String roleClean = dto.getRole().toUpperCase().replace("ROLE_", "");
            try {
                final RoleName rName = RoleName.valueOf(roleClean);
                Role role = roleRepository.findByName(rName)
                        .orElseGet(() -> roleRepository.save(new Role(rName)));
                user.getRoles().clear();
                user.getRoles().add(role);
            } catch (IllegalArgumentException ignored) {}
        }

        User updated = userRepository.save(user);
        return mapToDTO(updated);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }
}
