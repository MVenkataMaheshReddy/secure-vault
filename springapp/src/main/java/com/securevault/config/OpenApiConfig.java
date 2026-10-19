package com.securevault.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "SecureVault REST API",
        version = "1.0.0",
        description = "Intelligent Digital Locker & Document Management Platform API with JWT Authentication, Versioning, Approval Workflow, and Zero-Knowledge Verification",
        contact = @Contact(name = "SecureVault Security & Engineering Team", email = "support@securevault.com"),
        license = @License(name = "Apache 2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")
    ),
    security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    description = "Enter your JWT Bearer token obtained from the /api/auth/login endpoint. Format: Bearer <token>"
)
public class OpenApiConfig {
}
