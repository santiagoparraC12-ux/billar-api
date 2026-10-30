package com.billar.service;

import org.springframework.stereotype.Service;

import java.time.Duration;

/** Regla de cobro del tiempo. Toda la cuenta se hace en el servidor con SU reloj, nunca con el del celular. */
@Service
public class BillingService {

    /** Minutos que se cobran: minuto iniciado (step = 1) o fracciones de step minutos. */
    public int billedMinutes(Duration d, int step) {
        double m = d.toMillis() / 60000.0;
        if (step > 1) {
            return (int) Math.max(step, Math.ceil((m - 0.0001) / step) * step);
        }
        return (int) Math.max(1, Math.ceil(m));
    }

    /** Valor del tiempo en pesos, redondeado a los 50 pesos más cercanos. Ejemplo: 38 min a 6000/h = 3800. */
    public long timeValue(Duration d, long ratePerHour, int step) {
        int minutes = billedMinutes(d, step);
        return Math.round(minutes / 60.0 * ratePerHour / 50.0) * 50L;
    }

    public long timeValue(Duration d, String rate, int step) {
        return 0;
    }
}
