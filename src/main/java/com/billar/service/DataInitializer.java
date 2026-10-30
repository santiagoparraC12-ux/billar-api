package com.billar.service;

import com.billar.model.AppUser;
import com.billar.model.PoolTable;
import com.billar.model.Role;
import com.billar.repo.TableRepo;
import com.billar.repo.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Primer arranque: crea el usuario dueño, los ajustes y 4 mesas de ejemplo (a $6.000 por hora). */
@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepo users;
    private final TableRepo tables;
    private final SettingsService settings;
    private final PasswordEncoder encoder;
    private final String ownerUser;
    private final String ownerPass;

    public DataInitializer(UserRepo users, TableRepo tables, SettingsService settings, PasswordEncoder encoder,
                           @Value("${billar.owner.username}") String ownerUser,
                           @Value("${billar.owner.password}") String ownerPass) {
        this.users = users;
        this.tables = tables;
        this.settings = settings;
        this.encoder = encoder;
        this.ownerUser = ownerUser;
        this.ownerPass = ownerPass;
    }

    @Override
    public void run(String... args) {
        settings.get();
        if (users.count() == 0) {
            AppUser u = new AppUser();
            u.setUsername(ownerUser);
            u.setFullName("Dueño");
            u.setRole(Role.OWNER);
            u.setPasswordHash(encoder.encode(ownerPass));
            users.save(u);
            log.warn("Se creó el usuario dueño '{}'. Cambia su contraseña de inmediato.", ownerUser);
        }
        if (tables.count() == 0) {
            for (int i = 1; i <= 4; i++) {
                PoolTable t = new PoolTable();
                t.setName("Mesa " + i);
                t.setRatePerHour(6000);
                tables.save(t);
            }
        }
    }
}
