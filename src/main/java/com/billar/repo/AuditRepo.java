package com.billar.repo;

import com.billar.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditRepo extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop100ByOrderByLoggedAtDesc();
}
