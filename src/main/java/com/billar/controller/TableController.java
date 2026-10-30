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
@RequestMapping("/api/tables")
public class TableController {
    private final TableRepo tables;
    private final SessionRepo sessions;
    private final AuditService audit;

    public TableController(TableRepo tables, SessionRepo sessions, AuditService audit) {
        this.tables = tables;
        this.sessions = sessions;
        this.audit = audit;
    }

    @GetMapping
    public List<TableView> list() {
        return tables.findByActiveTrueOrderByIdAsc().stream().map(t -> new TableView(t.getId(), t.getName(), t.getRatePerHour())).toList();
    }

    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    public TableView create(@Valid @RequestBody TableReq r, Authentication auth) {
        PoolTable t = new PoolTable();
        t.setName(r.name());
        t.setRatePerHour(r.ratePerHour());
        tables.save(t);
        audit.log(auth.getName(), "Creó " + r.name() + " a " + r.ratePerHour() + " por hora");
        return new TableView(t.getId(), t.getName(), t.getRatePerHour());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public TableView update(@PathVariable Long id, @Valid @RequestBody TableReq r, Authentication auth) {
        PoolTable t = tables.findById(id).orElseThrow(() -> ApiException.notFound("La mesa no existe"));
        t.setName(r.name());
        t.setRatePerHour(r.ratePerHour());
        tables.save(t);
        audit.log(auth.getName(), "Cambió " + r.name() + ": tarifa " + r.ratePerHour() + " por hora");
        return new TableView(t.getId(), t.getName(), t.getRatePerHour());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public void delete(@PathVariable Long id, Authentication auth) {
        PoolTable t = tables.findById(id).orElseThrow(() -> ApiException.notFound("La mesa no existe"));
        if (sessions.existsByTableIdAndStatus(id, SessionStatus.OPEN)) throw ApiException.conflict("La mesa está en uso");
        t.setActive(false);
        tables.save(t);
        audit.log(auth.getName(), "Quitó " + t.getName());
    }
}
