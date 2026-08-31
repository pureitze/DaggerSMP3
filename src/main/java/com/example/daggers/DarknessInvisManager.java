package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Darkness Dagger's invisibility. The vanilla Invisibility potion
 * effect (still applied separately, in DaggerAbilityListener) only hides a
 * player's body - it never hides armor or held items, which is why enemies
 * could previously still see gear floating in place.
 *
 * This class provides TRUE full invisibility on top of that, using Bukkit's
 * player-hiding API: while active, the player is completely removed from
 * every other player's client (no body, no armor, no held item, no name
 * tag) rather than just made semi-transparent. This does not affect hostile
 * mob AI, which can still sense/target the player regardless of client-side
 * hiding - only other players are fooled.
 */
public class DarknessInvisManager implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> invisiblePlayers = new HashSet<>();

    public DarknessInvisManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player, long durationMillis) {
        invisiblePlayers.add(player.getUniqueId());
        hideFromEveryone(player);

        long ticks = durationMillis / 50L; // 1 tick = 50ms
        Bukkit.getScheduler().runTaskLater(plugin, () -> deactivate(player), ticks);
    }

    private void deactivate(Player player) {
        invisiblePlayers.remove(player.getUniqueId());
        if (player.isOnline()) {
            showToEveryone(player);
        }
    }

    private void hideFromEveryone(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.hidePlayer(plugin, player);
            }
        }
    }

    private void showToEveryone(Player player) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.showPlayer(plugin, player);
            }
        }
    }

    public boolean isActive(Player player) {
        return invisiblePlayers.contains(player.getUniqueId());
    }

    // Anyone who joins mid-invisibility needs to have already-invisible
    // players hidden from them too, since hidePlayer only applies to whoever
    // was online at the moment activate() was called.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID uuid : invisiblePlayers) {
            Player invisible = Bukkit.getPlayer(uuid);
            if (invisible != null && !invisible.equals(joining)) {
                joining.hidePlayer(plugin, invisible);
            }
        }
    }
}
