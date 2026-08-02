package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Tier2BuffManager {

    private final Map<UUID, DaggerType> activeBuffs = new HashMap<>();
    private final JavaPlugin plugin;

    public Tier2BuffManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player, DaggerType type, long durationMillis) {
    activeBuffs.put(player.getUniqueId(), type);
    long ticks = durationMillis / 50L; // 1 tick = 50ms
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        if (activeBuffs.get(player.getUniqueId()) == type) {
            activeBuffs.remove(player.getUniqueId());
        }
    }, ticks);
}

    public boolean hasBuff(Player player, DaggerType type) {
        return activeBuffs.get(player.getUniqueId()) == type;
    }
}
