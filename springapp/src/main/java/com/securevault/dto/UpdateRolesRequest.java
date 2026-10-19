package com.securevault.dto;

import java.util.Set;
import javax.validation.constraints.NotEmpty;

public class UpdateRolesRequest {

    @NotEmpty(message = "Roles set cannot be empty")
    private Set<String> roles;

    public UpdateRolesRequest() {}

    public UpdateRolesRequest(Set<String> roles) {
        this.roles = roles;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
        this.roles = roles;
    }
}
