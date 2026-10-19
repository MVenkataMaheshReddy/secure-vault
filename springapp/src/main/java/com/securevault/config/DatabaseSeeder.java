package com.securevault.config;

import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import com.securevault.repository.RoleRepository;
import com.securevault.repository.UserRepository;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSeeder.class);

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.name:Admin User}")
    private String adminName;

    @Value("${app.admin.email:admin@securevault.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@12345}")
    private String adminPassword;

    public DatabaseSeeder(RoleRepository roleRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedRoles();
        seedDefaultAdminUser();
    }

    private void seedRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(new Role(roleName));
                log.info("Seeded role: {}", roleName);
            }
        }
    }

    private void seedDefaultAdminUser() {
        Role adminRole = roleRepository.findByName(RoleName.ADMIN).orElse(null);
        if (adminRole == null) {
            adminRole = roleRepository.save(new Role(RoleName.ADMIN));
        }

        // Check if any admin user already exists
        boolean hasAdminUser = userRepository.findAll().stream()
                .anyMatch(u -> u.getRoles() != null && u.getRoles().stream()
                        .anyMatch(r -> r.getName() == RoleName.ADMIN));

        if (!hasAdminUser && !userRepository.existsByEmail(adminEmail.toLowerCase().trim())) {
            User admin = new User();
            admin.setName(adminName);
            admin.setEmail(adminEmail.toLowerCase().trim());
            admin.setPasswordHash(passwordEncoder.encode(adminPassword));
            admin.setEnabled(true);

            Set<Role> roles = new HashSet<>();
            roles.add(adminRole);
            admin.setRoles(roles);

            userRepository.save(admin);
            log.info("Seeded default ADMIN user: {} ({})", adminName, adminEmail);
        }
    }
}
