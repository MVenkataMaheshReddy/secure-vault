package com.securevault.dto;

import java.util.Set;

public class UserDTO {

    private Long id;
    private String name;
    private String username; // alias for name
    private String email;
    private String role;
    private Set<String> roles;
    private boolean enabled;

    public UserDTO() {}

    public UserDTO(Long id, String name, String email, String role) {
        this.id = id;
        this.name = name;
        this.username = name;
        this.email = email;
        this.role = role;
        this.enabled = true;
    }

    public UserDTO(Long id, String name, String email, String role, Set<String> roles, boolean enabled) {
        this.id = id;
        this.name = name;
        this.username = name;
        this.email = email;
        this.role = role;
        this.roles = roles;
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        this.username = name;
    }

    public String getUsername() {
        return username != null ? username : name;
    }

    public void setUsername(String username) {
        this.username = username;
        this.name = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
