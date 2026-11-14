package com.billar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class SessionSeries {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private TableSession session;
    private Instant startedAt;
    private Instant endedAt;      // vacío mientras la serie está en juego
    private Long loserId;         // id del SessionPlayer que perdió
    private int billedMinutes;
    private long timeValue;
}
