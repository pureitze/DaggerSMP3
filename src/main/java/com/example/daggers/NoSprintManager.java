package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NoSprintManager {
    private final Set<UUID> noSprintPlayers = new HashSet<>();
    private final JavaPlugin plugin;

    public NoSprintManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void disableSprint(Player player, long durationTicks) {
        UUID id = player.getUniqueId();
        noSprintPlayers.add(id);
        player.setSprinting(false);

        Bukkit.getScheduler().runTaskLater(plugin, () -> noSprintPlayers.remove(id), durationTicks);
    }

    public boolean isNoSprint(Player player) {
        return noSprintPlayers.contains(player.getUniqueId());
    }
}
