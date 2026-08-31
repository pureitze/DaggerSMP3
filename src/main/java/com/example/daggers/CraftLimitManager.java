package com.example.daggers;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tracks an optional per-dagger-type crafting cap, set by admins via
 * /dagger admin. A type with no configured limit is unlimited. Both the
 * configured limits and the running crafted counts persist across restarts
 * in config.yml.
 */
public class CraftLimitManager {

    private static final String LIMITS_PATH = "craftLimits";
    private static final String COUNTS_PATH = "craftedCounts";

    private final JavaPlugin plugin;
    private final Map<DaggerType, Integer> limits = new EnumMap<>(DaggerType.class);
    private final Map<DaggerType, Integer> craftedCounts = new EnumMap<>(DaggerType.class);

    public CraftLimitManager(JavaPlugin plugin) {
        this.plugin = plugin;
        for (DaggerType type : DaggerType.values()) {
            int limit = plugin.getConfig().getInt(LIMITS_PATH + "." + type.name(), -1);
            if (limit >= 0) {
                limits.put(type, limit);
            }
            int count = plugin.getConfig().getInt(COUNTS_PATH + "." + type.name(), 0);
            craftedCounts.put(type, count);
        }
    }

    /** Null means unlimited. */
    public Integer getLimit(DaggerType type) {
        return limits.get(type);
    }

    public int getCraftedCount(DaggerType type) {
        return craftedCounts.getOrDefault(type, 0);
    }

    public boolean canCraft(DaggerType type) {
        Integer limit = limits.get(type);
        if (limit == null) return true;
        return getCraftedCount(type) < limit;
    }

    public void setLimit(DaggerType type, Integer limit) {
        if (limit == null || limit < 0) {
            limits.remove(type);
            plugin.getConfig().set(LIMITS_PATH + "." + type.name(), null);
        } else {
            limits.put(type, limit);
            plugin.getConfig().set(LIMITS_PATH + "." + type.name(), limit);
        }
        plugin.saveConfig();
    }

    public void incrementCraftedCount(DaggerType type) {
        int newCount = getCraftedCount(type) + 1;
        craftedCounts.put(type, newCount);
        plugin.getConfig().set(COUNTS_PATH + "." + type.name(), newCount);
        plugin.saveConfig();
    }
}
