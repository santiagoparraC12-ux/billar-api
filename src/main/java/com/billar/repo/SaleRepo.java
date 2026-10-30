package com.billar.repo;

import com.billar.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepo extends JpaRepository<Sale, Long> {
    List<Sale> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant from, Instant to);
    List<Sale> findByPaidAtBetween(Instant from, Instant to);
    List<Sale> findByMethodAndPaidFalseAndStatusOrderByCreatedAtAsc(PayMethod method, SaleStatus status);
}
