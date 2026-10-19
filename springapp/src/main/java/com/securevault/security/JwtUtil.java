package com.securevault.security;

import com.securevault.entity.Role;
import com.securevault.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    private final Key key;
    private final long expirationTimeMs;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtUtil(
            @Value("${jwt.secret:nY1mDkHg2JX3aXK0xkj93i9m1p7CqJUt8V2lDmj3Lk9B+oVyR3cJ5z4E3t3UOa0x}") String secret,
            @Value("${jwt.expiration:3600000}") long expirationTimeMs) {
        this.expirationTimeMs = expirationTimeMs;
        this.key = initKey(secret);
    }

    public JwtUtil(String secret) {
        this(secret, 3600000L); // 1 hour default
    }

    private static Key initKey(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("JWT secret key cannot be null or empty");
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(secret);
            if (decoded.length >= 32) {
                return Keys.hmacShaKeyFor(decoded);
            }
        } catch (IllegalArgumentException ignored) {
            // Not valid Base64, fallback to UTF-8 bytes
        }

        byte[] rawBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (rawBytes.length < 32) {
            throw new IllegalArgumentException(
                    "JWT secret must be at least 256 bits (32 bytes) for secure HMAC-SHA256 signing. Configured secret is "
                            + (rawBytes.length * 8) + " bits (" + rawBytes.length + " bytes)."
            );
        }
        return Keys.hmacShaKeyFor(rawBytes);
    }

    public String generateToken(String email, Long userId, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTimeMs);

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("roles", roles != null ? roles : new ArrayList<>())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String generateToken(User user) {
        List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream()
                        .map(Role::getName)
                        .map(Enum::name)
                        .collect(Collectors.toList())
                : new ArrayList<>();

        return generateToken(user.getEmail(), user.getId(), roleNames);
    }

    public String generateCustomToken(String email, Long userId, List<String> roles, long customExpirationMs) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + customExpirationMs);

        return Jwts.builder()
                .setSubject(email)
                .claim("userId", userId)
                .claim("roles", roles != null ? roles : new ArrayList<>())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String getEmailFromToken(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    public Long getUserIdFromToken(String token) {
        Object userIdObj = getClaimsFromToken(token).get("userId");
        if (userIdObj instanceof Number) {
            return ((Number) userIdObj).longValue();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object rolesObj = getClaimsFromToken(token).get("roles");
        if (rolesObj instanceof List<?>) {
            return (List<String>) rolesObj;
        }
        return new ArrayList<>();
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = getClaimsFromToken(token);
            return claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
