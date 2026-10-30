package com.billar.repo;

import com.billar.model.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TableRepo extends JpaRepository<PoolTable, Long> {
    List<PoolTable> findByActiveTrueOrderByIdAsc();
}
