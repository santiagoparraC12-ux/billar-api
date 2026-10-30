package com.billar.repo;

import com.billar.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClosingRepo extends JpaRepository<ShiftClosing, Long> {
    Optional<ShiftClosing> findFirstByOrderByClosedAtDesc();
    List<ShiftClosing> findByClosedAtBetweenOrderByClosedAtDesc(Instant from, Instant to);
}
