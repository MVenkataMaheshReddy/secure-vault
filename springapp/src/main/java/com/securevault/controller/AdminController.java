package com.securevault.controller;

import com.securevault.dto.UpdateEnabledRequest;
import com.securevault.dto.UpdateRolesRequest;
import com.securevault.dto.UserDTO;
import com.securevault.exception.BadRequestException;
import com.securevault.service.UserService;
import java.util.List;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Management", description = "Endpoints for administrator actions: role assignment, user lock/enable, and platform management")
@RestController
@RequestMapping({"/api/admin", "/admin"})
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Admin dashboard status", description = "Simple health/status ping for admin dashboard")
    @GetMapping("/dashboard")
    public ResponseEntity<String> adminDashboard() {
        return ResponseEntity.ok("Welcome to the SecureVault Admin Dashboard!");
    }

    @Operation(summary = "Get all users (admin)", description = "Retrieve list of all users in the system")
    @GetMapping("/users")
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }

    @Operation(summary = "Get user by ID (admin)", description = "Admin endpoint to retrieve a user profile by ID")
    @GetMapping("/users/{id}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }

    @Operation(summary = "Update user roles", description = "Admin endpoint to assign roles (e.g. ROLE_REVIEWER, ROLE_VERIFIER, ROLE_EMPLOYEE, ROLE_ADMIN)")
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserDTO> updateUserRoles(@PathVariable Long id,
                                                   @Valid @RequestBody UpdateRolesRequest request,
                                                   Authentication authentication) {
        String adminEmail = authentication != null ? authentication.getName() : null;
        UserDTO updatedUser = userService.updateUserRoles(id, request.getRoles(), adminEmail);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Enable or disable user account", description = "Admin endpoint to lock or unlock a user account")
    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<UserDTO> updateUserEnabled(@PathVariable Long id,
                                                     @Valid @RequestBody UpdateEnabledRequest request,
                                                     Authentication authentication) {
        if (request.getEnabled() == null) {
            throw new BadRequestException("Enabled field must be provided");
        }
        String adminEmail = authentication != null ? authentication.getName() : null;
        UserDTO updatedUser = userService.updateUserEnabled(id, request.getEnabled(), adminEmail);
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Delete user (admin)", description = "Admin endpoint to permanently delete a user account")
    @DeleteMapping("/user/{id}")
    public ResponseEntity<String> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok("Deleted user with id " + id);
    }
}
