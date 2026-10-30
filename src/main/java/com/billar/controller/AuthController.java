package com.billar.controller;

import com.billar.dto.Dtos.*;
import com.billar.service.*;
import com.billar.repo.*;
import com.billar.model.*;
import com.billar.error.ApiException;
import com.billar.security.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final UserRepo users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttempts attempts;
    private final AuditService audit;

    public AuthController(UserRepo users, PasswordEncoder encoder, JwtService jwt, LoginAttempts attempts, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.audit = audit;
    }

    @PostMapping("/login")
    public LoginRes login(@Valid @RequestBody LoginReq r) {
        if (attempts.isLocked(r.username())) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos. Espera 5 minutos.");
        }
        AppUser u = users.findByUsername(r.username()).orElse(null);
        if (u == null || !u.isActive() || !encoder.matches(r.password(), u.getPasswordHash())) {
            attempts.fail(r.username());
            throw ApiException.unauthorized("Usuario o contraseña incorrectos");
        }
        attempts.success(r.username());
        audit.log(u.getUsername(), "Inició sesión");
        return new LoginRes(jwt.create(u), u.getUsername(), u.getFullName(), u.getRole());
    }

    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        AppUser u = users.findByUsername(auth.getName()).orElseThrow();
        return Map.of("username", u.getUsername(), "fullName", u.getFullName(), "role", u.getRole());
    }
}
