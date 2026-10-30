package com.billar.security;

import com.billar.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    private final SecretKey key;
    private final long hours;

    public JwtService(@Value("${billar.jwt-secret}") String secret, @Value("${billar.jwt-hours}") long hours) {
        if (secret.length() < 32) throw new IllegalStateException("JWT_SECRET debe tener al menos 32 caracteres");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.hours = hours;
    }

    public String create(AppUser u) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(u.getUsername())
                .claim("role", u.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(now + hours * 3_600_000L))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
