package com.securevault.dto;

import javax.validation.constraints.NotBlank;

public class LoginDTO {

    @NotBlank(message = "Username or Email is required")
    private String usernameOrEmail;

    @NotBlank(message = "Password is required")
    private String password;

    private String role; // optional for frontend convenience

    public LoginDTO() {}

    public LoginDTO(String usernameOrEmail, String password, String role) {
        this.usernameOrEmail = usernameOrEmail;
        this.password = password;
        this.role = role;
    }

    public String getUsernameOrEmail() {
        return usernameOrEmail;
    }

    public void setUsernameOrEmail(String usernameOrEmail) {
        this.usernameOrEmail = usernameOrEmail;
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
}
