package com.billar.security;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bloquea 5 minutos a un usuario tras 5 contraseñas incorrectas seguidas (protege contra adivinar el PIN o la clave). */
@Component
public class LoginAttempts {
    private static final int MAX = 5;
    private static final long LOCK_SECONDS = 300;

    private record Entry(int fails, Instant lockedUntil) {}
    private final Map<String, Entry> map = new ConcurrentHashMap<>();

    public boolean isLocked(String username) {
        Entry e = map.get(username.toLowerCase());
        return e != null && e.lockedUntil() != null && e.lockedUntil().isAfter(Instant.now());
    }

    public void fail(String username) {
        map.merge(username.toLowerCase(), new Entry(1, null), (old, n) -> {
            int f = old.fails() + 1;
            return f >= MAX ? new Entry(0, Instant.now().plusSeconds(LOCK_SECONDS)) : new Entry(f, null);
        });
    }

    public void success(String username) {
        map.remove(username.toLowerCase());
    }
}
