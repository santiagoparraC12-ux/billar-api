package com.billar.repo;

import com.billar.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SessionRepo extends JpaRepository<TableSession, Long> {
    List<TableSession> findByStatus(SessionStatus status);
    boolean existsByTableIdAndStatus(Long tableId, SessionStatus status);
}
