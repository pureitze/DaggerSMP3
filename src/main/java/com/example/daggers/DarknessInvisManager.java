package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DarknessInvisManager {
    private final Set<UUID> activePlayers = new HashSet<>();
    private final JavaPlugin plugin;

    public DarknessInvisManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player, long durationTicks) {
        UUID id = player.getUniqueId();
        activePlayers.add(id);
        Bukkit.getScheduler().runTaskLater(plugin, () -> activePlayers.remove(id), durationTicks);
    }

    public boolean isActive(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }
}
