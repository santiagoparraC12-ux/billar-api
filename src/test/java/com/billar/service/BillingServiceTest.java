package com.billar.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingServiceTest {
    private final BillingService billing = new BillingService();

    @Test
    void cobraMinutoIniciado() {
        Duration d = Duration.ofMinutes(37).plusSeconds(10);
        assertEquals(38, billing.billedMinutes(d, 1));
        assertEquals(3800, billing.timeValue(d, 6000, 1));   // 38 min x $100 por minuto
    }

    @Test
    void unaHoraExactaCuestaLaTarifa() {
        assertEquals(6000, billing.timeValue(Duration.ofMinutes(60), 6000, 1));
    }

    @Test
    void minimoUnMinuto() {
        assertEquals(1, billing.billedMinutes(Duration.ofSeconds(5), 1));
        assertEquals(100, billing.timeValue(Duration.ofSeconds(5), 6000, 1));
    }

    @Test
    void fraccionesDeQuinceMinutos() {
        assertEquals(15, billing.billedMinutes(Duration.ofMinutes(3), 15));
        assertEquals(30, billing.billedMinutes(Duration.ofMinutes(16), 15));
        assertEquals(15, billing.billedMinutes(Duration.ofMinutes(15), 15));
    }
}
