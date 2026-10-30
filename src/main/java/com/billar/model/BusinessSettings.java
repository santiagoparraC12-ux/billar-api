package com.billar.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class BusinessSettings {
    @Id
    private Long id;
    private String businessName;
    private int billingStepMinutes;
    private long baseCash;

}
