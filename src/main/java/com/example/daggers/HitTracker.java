package com.example.daggers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Counts how many times each player has hit each other specific target
 * with the Soul Dagger. Needed for the "every 5th hit" / "every 10th hit" effects.
 */
public class HitTracker {
    private final Map<UUID, Map<UUID, Integer>> hitCounts = new HashMap<>();

    /**
     * Records a hit and returns the new total hit count for this attacker->target pair.
     */
    public int recordHit(UUID attacker, UUID target) {
        Map<UUID, Integer> targets = hitCounts.computeIfAbsent(attacker, k -> new HashMap<>());
        int newCount = targets.getOrDefault(target, 0) + 1;
        targets.put(target, newCount);
        return newCount;
    }
}
