package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Announces every dagger craft in chat. The FIRST craft of EACH dagger type
 * (tracked independently per type, so Fire/Ice/Water/etc. each get their own
 * one-time event) gets special treatment instead of the normal announcement:
 * it's pulled out of the crafter's hands, placed in the world as a glowing,
 * unpickupable "altar offering" for 8 minutes with a Wither-style boss bar
 * showing its name and coordinates, then becomes claimable by anyone.
 *
 * Multiple types can have their countdown running at the same time (e.g. the
 * first Fire Dagger and first Ice Dagger crafted minutes apart), each with its
 * own independent boss bar.
 */
public class DaggerCraftListener implements Listener {

    private static final long FIRST_DAGGER_DELAY_TICKS = 8 * 60 * 20L; // 8 minutes

    private final JavaPlugin plugin;
    private final Set<DaggerType> firstCraftedTypes;
    // one active boss bar per type currently in its 8-minute countdown
    private final ConcurrentHashMap<DaggerType, BossBar> activeBars = new ConcurrentHashMap<>();

    public DaggerCraftListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.firstCraftedTypes = EnumSet.noneOf(DaggerType.class);

        List<String> saved = plugin.getConfig().getStringList("firstCraftedDaggerTypes");
        for (String name : saved) {
            try {
                firstCraftedTypes.add(DaggerType.valueOf(name));
            } catch (IllegalArgumentException ignored) {
                // stale/unknown entry in config, skip it
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack result = event.getInventory().getResult();
        DaggerType type = DaggerItem.getType(result);
        if (type == null) return;

        if (!firstCraftedTypes.contains(type)) {
            firstCraftedTypes.add(type);
            saveClaimedTypes();

            // let the normal craft complete this tick, then redirect the result next tick
            Bukkit.getScheduler().runTask(plugin, () -> handleFirstOfType(player, type));
            return;
        }

        Location loc = player.getLocation();
        String coords = formatCoords(loc);
        Bukkit.broadcastMessage(type.getColor() + type.getDisplayName() + "§r has been crafted by "
                + player.getName() + " at " + coords);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        for (BossBar bar : activeBars.values()) {
            bar.addPlayer(event.getPlayer());
        }
    }

    private void handleFirstOfType(Player player, DaggerType type) {
        // Pull the freshly-crafted dagger back out of the player's hands.
        ItemStack cursor = player.getItemOnCursor();
        if (DaggerItem.getType(cursor) == type) {
            player.setItemOnCursor(null);
        } else {
            player.getInventory().removeItem(DaggerItem.create(type));
        }

        Location loc = player.getLocation();
        Item dropped = loc.getWorld().dropItem(loc, DaggerItem.create(type));
        dropped.setGravity(false);
        dropped.setGlowing(true);
        dropped.setPickupDelay(Integer.MAX_VALUE); // locked until the timer finishes
        dropped.setCustomName(type.getColor() + type.getDisplayName());
        dropped.setCustomNameVisible(true);

        String coords = formatCoords(loc);
        String title = type.getColor() + type.getDisplayName() + " §7- " + coords;

        BossBar bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.NOTCHED_10);
        bar.setProgress(1.0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(online);
        }
        activeBars.put(type, bar);

        Bukkit.broadcastMessage("§5§lThe first " + type.getColor() + type.getDisplayName() + "§5§l has been forged by "
                + player.getName() + "! It rests upon the altar at " + coords + "...");

        new BukkitRunnable() {
            long elapsedTicks = 0L;

            @Override
            public void run() {
                elapsedTicks += 20L;
                double progress = 1.0 - (double) elapsedTicks / FIRST_DAGGER_DELAY_TICKS;

                if (progress <= 0) {
                    bar.setProgress(0);
                    bar.removeAll();
                    activeBars.remove(type);

                    if (!dropped.isDead()) {
                        dropped.setPickupDelay(0);
                        dropped.getWorld().playSound(dropped.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
                    }

                    Bukkit.broadcastMessage("§5§lThe " + type.getColor() + type.getDisplayName()
                            + "§5§l may now be claimed!");
                    cancel();
                    return;
                }

                bar.setProgress(progress);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void saveClaimedTypes() {
        List<String> names = firstCraftedTypes.stream().map(Enum::name).toList();
        plugin.getConfig().set("firstCraftedDaggerTypes", names);
        plugin.saveConfig();
    }

    private String formatCoords(Location loc) {
        return String.format("(%d, %d, %d)", loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
