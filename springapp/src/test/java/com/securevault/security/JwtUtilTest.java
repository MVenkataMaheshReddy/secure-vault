package com.securevault.security;

import com.securevault.entity.Role;
import com.securevault.entity.RoleName;
import com.securevault.entity.User;
import io.jsonwebtoken.Claims;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilTest {

    private static final String TEST_SECRET = "0123456789012345678901234567890123456789012345678901234567890123"; // 64 chars
    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(TEST_SECRET, 3600000L); // 1 hour
    }

    @Test
    void testValidToken() {
        User user = new User("Alice", "alice@example.com", "hash");
        user.setId(10L);
        user.setRoles(new HashSet<>(Collections.singletonList(new Role(RoleName.EMPLOYEE))));

        String token = jwtUtil.generateToken(user);

        assertNotNull(token);
        assertTrue(jwtUtil.validateToken(token));
        assertFalse(jwtUtil.isTokenExpired(token));

        assertEquals("alice@example.com", jwtUtil.getEmailFromToken(token));
        assertEquals(10L, jwtUtil.getUserIdFromToken(token));

        List<String> roles = jwtUtil.getRolesFromToken(token);
        assertNotNull(roles);
        assertTrue(roles.contains("EMPLOYEE"));
    }

    @Test
    void testExpiredToken() {
        // Expiration in the past (-10 seconds)
        JwtUtil expiredJwtUtil = new JwtUtil(TEST_SECRET, -10000L);
        String expiredToken = expiredJwtUtil.generateToken("bob@example.com", 20L, Collections.singletonList("EMPLOYEE"));

        assertNotNull(expiredToken);
        assertTrue(expiredJwtUtil.isTokenExpired(expiredToken));
        assertFalse(expiredJwtUtil.validateToken(expiredToken));
    }

    @Test
    void testTamperedToken() {
        String token = jwtUtil.generateToken("charlie@example.com", 30L, Collections.singletonList("EMPLOYEE"));
        assertNotNull(token);

        // Tamper with the token payload
        String[] parts = token.split("\\.");
        assertEquals(3, parts.length);

        // Modify the signature portion
        String tamperedToken = parts[0] + "." + parts[1] + ".tamperedSignature123";

        assertFalse(jwtUtil.validateToken(tamperedToken));
    }
}
