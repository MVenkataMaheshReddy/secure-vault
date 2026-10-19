package com.securevault.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

public class RegisterRequest {

    @NotBlank(message = "Name is required")
    @JsonAlias({"username", "userName"})
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password is required")
    @Pattern(
        regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
        message = "Password must be at least 8 characters long and contain at least one letter and one number"
    )
    private String password;

    private String role; // "EMPLOYEE" or "ADMIN"

    @JsonAlias({"adminAccessCode", "adminRegistrationCode"})
    private String adminCode;

    public RegisterRequest() {}

    public RegisterRequest(String name, String email, String password) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = "EMPLOYEE";
    }

    public RegisterRequest(String name, String email, String password, String role) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
    }

    public RegisterRequest(String name, String email, String password, String role, String adminCode) {
        this.name = name;
        this.email = email;
        this.password = password;
        this.role = role;
        this.adminCode = adminCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getAdminCode() {
        return adminCode;
    }

    public void setAdminCode(String adminCode) {
        this.adminCode = adminCode;
    }
}
