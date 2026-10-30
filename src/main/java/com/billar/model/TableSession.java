package com.billar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class TableSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "table_id")
    private PoolTable table;
    private Instant startedAt;
    private Instant endedAt;
    private String openedBy;
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.OPEN;
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SessionItem> items = new ArrayList<>();

    private String label;

    public String getName() { return table != null ? table.getName() : label; }
    public long getRate() { return table != null ? table.getRatePerHour() : 0; }
    public Long tableIdOrNull() { return table != null ? table.getId() : null; }
}
