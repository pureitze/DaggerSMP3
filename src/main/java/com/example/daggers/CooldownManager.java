package com.example.daggers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks "when does this player's ability with this dagger become available again".
 * Purely in-memory: cooldowns reset if the server restarts, which is fine for
 * this kind of gameplay cooldown.
 */
public class CooldownManager {
    private final Map<UUID, Map<DaggerType, Long>> readyAt = new HashMap<>();

    public boolean isOnCooldown(UUID player, DaggerType type) {
        return getRemainingSeconds(player, type) > 0;
    }

    public long getRemainingSeconds(UUID player, DaggerType type) {
        Long expiry = readyAt.getOrDefault(player, Map.of()).get(type);
        if (expiry == null) return 0;
        long remainingMillis = expiry - System.currentTimeMillis();
        return Math.max(0, remainingMillis / 1000);
    }

    public void startCooldown(UUID player, DaggerType type, int seconds) {
        readyAt.computeIfAbsent(player, k -> new HashMap<>())
                .put(type, System.currentTimeMillis() + seconds * 1000L);
    }
}
