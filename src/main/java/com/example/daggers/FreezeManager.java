package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class FreezeManager {
    private final Set<UUID> frozenPlayers = new HashSet<>();
    private final JavaPlugin plugin;

    public FreezeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void freeze(Player player, long durationMillis) {
    frozenPlayers.add(player.getUniqueId());
    long ticks = durationMillis / 50L; // 1 tick = 50ms
    Bukkit.getScheduler().runTaskLater(plugin, () -> {
        frozenPlayers.remove(player.getUniqueId());
    }, ticks);
}
    }
    public boolean isFrozen(Player player) {
        return frozenPlayers.contains(player.getUniqueId());
    }
}
