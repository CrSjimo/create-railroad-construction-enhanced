package dev.sjimo.rrce;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SessionManager {
    private static final Map<UUID, PlayerSession> SESSIONS = new ConcurrentHashMap<>();

    private SessionManager() {}

    public static PlayerSession get(UUID id) {
        return SESSIONS.computeIfAbsent(id, ignored -> new PlayerSession());
    }

    public static void disconnected(UUID id) {
        PlayerSession session = SESSIONS.get(id);
        if (session != null) session.disconnectedAt = Instant.now();
    }

    public static void connected(UUID id) {
        PlayerSession session = SESSIONS.get(id);
        if (session != null) session.disconnectedAt = null;
    }

    public static void expire() {
        Instant now = Instant.now();
        SESSIONS.entrySet().removeIf(entry -> entry.getValue().disconnectedAt != null
            && Duration.between(entry.getValue().disconnectedAt, now).toMinutes() >= 10);
    }
}
