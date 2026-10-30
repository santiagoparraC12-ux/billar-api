package com.billar.service;

import com.billar.dto.Dtos.AuditView;
import com.billar.model.AuditLog;
import com.billar.repo.AuditRepo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AuditService {
    private final AuditRepo repo;

    public AuditService(AuditRepo repo) { this.repo = repo; }

    public void log(String user, String action) {
        AuditLog a = new AuditLog();
        a.setLoggedAt(Instant.now());
        a.setUsername(user);
        a.setAction(action.length() > 500 ? action.substring(0, 500) : action);
        repo.save(a);
    }

    @Transactional(readOnly = true)
    public List<AuditView> recent() {
        return repo.findTop100ByOrderByLoggedAtDesc().stream().map(a -> new AuditView(a.getLoggedAt(), a.getUsername(), a.getAction())).toList();
    }
}
