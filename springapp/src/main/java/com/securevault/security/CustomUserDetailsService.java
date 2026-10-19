package com.securevault.security;

import com.securevault.entity.Role;
import com.securevault.entity.User;
import com.securevault.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        Optional<User> userOpt = userRepository.findByEmail(identifier);
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByName(identifier);
        }

        User user = userOpt.orElseThrow(() ->
                new UsernameNotFoundException("User not found with email or username: " + identifier));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (user.getRoles() != null && !user.getRoles().isEmpty()) {
            for (Role role : user.getRoles()) {
                String roleName = role.getName().name();
                if (!roleName.startsWith("ROLE_")) {
                    roleName = "ROLE_" + roleName;
                }
                authorities.add(new SimpleGrantedAuthority(roleName));
            }
        } else {
            authorities.add(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
        }

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                user.isEnabled(),
                true,
                true,
                true,
                authorities
        );
    }
}
