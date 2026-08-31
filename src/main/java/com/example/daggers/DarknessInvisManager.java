package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Darkness Dagger's invisibility using two techniques together:
 *
 *  1. Player#hidePlayer / showPlayer - removes the player entity and its
 *     nametag entirely from other clients' rendering.
 *
 *  2. Player#sendEquipmentChange(s) - separately fakes what each observer
 *     sees as the invisible player's equipped gear (main hand, off hand,
 *     helmet, chestplate, leggings, boots) as empty. This is necessary
 *     because hidePlayer alone is a long-documented limitation: it reliably
 *     hides the body/nametag but not equipped armor or held items - that's
 *     never been something hidePlayer was designed to solve on its own.
 *
 * sendEquipmentChange only fakes what one specific observer sees - it never
 * touches the real inventory, so the invisible player's actual gear, armor
 * values, and combat stats are completely unaffected the whole time.
 *
 * Honest limitation: the Totem of Undying's save animation is a hardcoded
 * client animation triggered by actually using a totem, not just "what's in
 * your hand" - faking the hand as empty won't necessarily suppress that one
 * specific animation if it fires during the effect.
 */
public class DarknessInvisManager implements Listener {

    private static final EquipmentSlot[] ALL_SLOTS = {
            EquipmentSlot.HAND, EquipmentSlot.OFF_HAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final JavaPlugin plugin;
    private final Set<UUID> invisiblePlayers = new HashSet<>();

    public DarknessInvisManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void activate(Player player, long durationMillis) {
        invisiblePlayers.add(player.getUniqueId());
        hideFromEveryone(player);
        fakeEmptyEquipmentForEveryone(player);

        long ticks = durationMillis / 50L; // 1 tick = 50ms
        Bukkit.getScheduler().runTaskLater(plugin, () -> deactivate(player), ticks);
    }

    private void deactivate(Player player) {
        invisiblePlayers.remove(player.getUniqueId());
        if (player.isOnline()) {
            showToEveryone(player);
            restoreRealEquipmentForEveryone(player);
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

    private void fakeEmptyEquipmentForEveryone(Player player) {
        Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : ALL_SLOTS) {
            empty.put(slot, null); // null = shows as air to the observer
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.sendEquipmentChanges(player, empty);
            }
        }
    }

    private void restoreRealEquipmentForEveryone(Player player) {
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) return;

        Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
        real.put(EquipmentSlot.HAND, equipment.getItemInMainHand());
        real.put(EquipmentSlot.OFF_HAND, equipment.getItemInOffHand());
        real.put(EquipmentSlot.HEAD, equipment.getHelmet());
        real.put(EquipmentSlot.CHEST, equipment.getChestplate());
        real.put(EquipmentSlot.LEGS, equipment.getLeggings());
        real.put(EquipmentSlot.FEET, equipment.getBoots());

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.equals(player)) {
                online.sendEquipmentChanges(player, real);
            }
        }
    }

    public boolean isActive(Player player) {
        return invisiblePlayers.contains(player.getUniqueId());
    }

    // Anyone who joins mid-invisibility needs both the entity hidden AND
    // their equipment faked empty, since both were only ever applied to
    // whoever was online at the moment activate() was called.
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        for (UUID uuid : invisiblePlayers) {
            Player invisible = Bukkit.getPlayer(uuid);
            if (invisible == null || invisible.equals(joining)) continue;

            joining.hidePlayer(plugin, invisible);

            Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : ALL_SLOTS) {
                empty.put(slot, null);
            }
            joining.sendEquipmentChanges(invisible, empty);
        }
    }
}
