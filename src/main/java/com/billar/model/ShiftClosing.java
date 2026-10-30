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
public class ShiftClosing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant fromAt;
    private Instant closedAt;
    private String closedBy;
    private long baseCash;
    private long expectedCash;
    private long countedCash;
    private long difference;
    private long cashSales;
    private long transferSales;
    private long creditSales;
    private long courtesySales;
    private long expensesTotal;

}
