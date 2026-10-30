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
public class Sale {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private SaleType type;
    private String tableName;
    private Instant startedAt;
    private Instant endedAt;
    private long actualSeconds;
    private int billedMinutes;
    private long timeValue;
    private long itemsTotal;
    private long itemsCost;
    private long total;
    @Enumerated(EnumType.STRING)
    private PayMethod method;
    private String customerName;
    private String note;
    private String createdBy;
    private Instant createdAt;
    @Enumerated(EnumType.STRING)
    private SaleStatus status = SaleStatus.ACTIVE;
    private boolean paid;
    private Instant paidAt;
    @Enumerated(EnumType.STRING)
    private PayMethod paidMethod;
    private String voidedBy;
    private Instant voidedAt;
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

}
