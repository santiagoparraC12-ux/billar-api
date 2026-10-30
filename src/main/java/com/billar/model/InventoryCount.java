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
public class InventoryCount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant countedAt;
    private String countedBy;
    private Long productId;
    private String productName;
    private int systemStock;
    private int countedStock;
    private int difference;
    private long unitCost;

}
