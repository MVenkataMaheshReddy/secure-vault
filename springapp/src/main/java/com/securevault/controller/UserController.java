package com.securevault.controller;

import com.securevault.dto.RegisterDTO;
import com.securevault.dto.UserDTO;
import com.securevault.exception.ForbiddenException;
import com.securevault.service.UserService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User Management", description = "Endpoints for managing user profiles and querying users")
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    private boolean isUserAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @Operation(summary = "Get all users", description = "Admin-only endpoint to retrieve all registered users")
    @GetMapping("/fetch")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @Operation(summary = "Register user", description = "Public/Admin endpoint to create a user account")
    @PostMapping({"/register", "/create"})
    public ResponseEntity<UserDTO> registerUser(@Valid @RequestBody RegisterDTO dto) {
        UserDTO created = userService.registerUser(dto);
        return ResponseEntity.ok(created);
    }

    @Operation(summary = "Get user by ID", description = "Retrieve a user profile by ID. Restricted to owner or ADMIN.")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id, Authentication authentication) {
        UserDTO user = userService.getUserById(id);
        boolean isAdmin = isUserAdmin(authentication);
        String currentEmail = authentication != null ? authentication.getName() : null;

        // Object-level ownership check: ADMIN or self
        if (!isAdmin && (currentEmail == null || !currentEmail.equalsIgnoreCase(user.getEmail()))) {
            throw new ForbiddenException("You do not have permission to view this user profile");
        }

        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Update user", description = "Update user details. Restricted to account owner or ADMIN. Non-admins cannot alter roles.")
    @PutMapping("/update/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id,
                                              @Valid @RequestBody RegisterDTO dto,
                                              Authentication authentication) {
        UserDTO targetUser = userService.getUserById(id);
        boolean isAdmin = isUserAdmin(authentication);
        String currentEmail = authentication != null ? authentication.getName() : null;

        // Object-level ownership check: ADMIN or self
        if (!isAdmin && (currentEmail == null || !currentEmail.equalsIgnoreCase(targetUser.getEmail()))) {
            throw new ForbiddenException("You do not have permission to update this user profile");
        }

        // Prevent privilege escalation: non-admin cannot alter roles
        if (!isAdmin) {
            dto.setRole(null);
        }

        return ResponseEntity.ok(userService.updateUser(id, dto));
    }

    @Operation(summary = "Delete user", description = "Admin-only endpoint to delete a user. Admin cannot delete their own account.")
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteUser(@PathVariable Long id, Authentication authentication) {
        UserDTO targetUser = userService.getUserById(id);
        String currentEmail = authentication != null ? authentication.getName() : null;

        if (currentEmail != null && currentEmail.equalsIgnoreCase(targetUser.getEmail())) {
            throw new ForbiddenException("Admins cannot delete their own account");
        }

        userService.deleteUser(id);
        return ResponseEntity.ok("User deleted successfully!");
    }
}
