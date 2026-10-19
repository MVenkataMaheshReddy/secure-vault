package com.securevault.dto;

import javax.validation.constraints.NotNull;

public class UpdateEnabledRequest {

    @NotNull(message = "Enabled field is required")
    private Boolean enabled;

    public UpdateEnabledRequest() {}

    public UpdateEnabledRequest(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
