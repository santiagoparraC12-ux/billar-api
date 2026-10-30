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
@RequestMapping("/api")
public class AdminController {
    private final ReportService reports;
    private final AuditService audit;
    private final SettingsService settings;
    private final UserService users;

    public AdminController(ReportService reports, AuditService audit, SettingsService settings, UserService users) {
        this.reports = reports;
        this.audit = audit;
        this.settings = settings;
        this.users = users;
    }

    @GetMapping("/reports/summary")
    @PreAuthorize("hasRole('OWNER')")
    public Summary summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return reports.summary(from, to);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasRole('OWNER')")
    public List<AuditView> audit() { return audit.recent(); }

    @GetMapping("/settings")
    public SettingsView settings() { return settings.view(); }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('OWNER')")
    public SettingsView updateSettings(@Valid @RequestBody SettingsReq r, Authentication auth) { return settings.update(r, auth.getName()); }

    @GetMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    public List<UserView> users() { return users.list(); }

    @PostMapping("/users")
    @PreAuthorize("hasRole('OWNER')")
    public UserView createUser(@Valid @RequestBody UserReq r, Authentication auth) { return users.create(r, auth.getName()); }

    @PatchMapping("/users/{id}/active")
    @PreAuthorize("hasRole('OWNER')")
    public UserView setActive(@PathVariable Long id, @RequestParam boolean value, Authentication auth) {
        return users.setActive(id, value, auth.getName());
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasRole('OWNER')")
    public void resetPassword(@PathVariable Long id, @Valid @RequestBody PasswordReq r, Authentication auth) {
        users.resetPassword(id, r.password(), auth.getName());
    }
}
