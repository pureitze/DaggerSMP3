package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
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
 * (tracked independently per type) gets special treatment instead of the normal
 * announcement: the crafting table it was made on becomes unbreakable (including
 * against explosions) for 8 minutes, grows a particle beam out of the top, and
 * shows a Wither-style boss bar with the dagger's name and coordinates. When the
 * timer ends, the table becomes breakable again and the dagger drops on top of it.
 */
public class DaggerCraftListener implements Listener {

    private static final long FIRST_DAGGER_DELAY_TICKS = 8 * 60 * 20L; // 8 minutes

    private final JavaPlugin plugin;
    private final Set<DaggerType> firstCraftedTypes;
    private final ConcurrentHashMap<DaggerType, BossBar> activeBars = new ConcurrentHashMap<>();
    // block locations currently protected + beaming, and which dagger type each belongs to
    private final ConcurrentHashMap<Location, DaggerType> protectedAltars = new ConcurrentHashMap<>();

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

            Location tableLoc = (event.getInventory() instanceof CraftingInventory ci && ci.getLocation() != null)
                    ? ci.getLocation().getBlock().getLocation()
                    : null;

            // let the normal craft complete this tick, then redirect the result next tick
            Bukkit.getScheduler().runTask(plugin, () -> handleFirstOfType(player, type, tableLoc));
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

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        DaggerType type = protectedAltars.get(event.getBlock().getLocation());
        if (type == null) return;

        event.setCancelled(true);
        event.getPlayer().sendMessage("§5This crafting table is bound by ancient magic and cannot be broken yet.");
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> protectedAltars.containsKey(b.getLocation()));
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> protectedAltars.containsKey(b.getLocation()));
    }

    private void handleFirstOfType(Player player, DaggerType type, Location tableLoc) {
        // Pull the freshly-crafted dagger back out of the player's hands.
        ItemStack cursor = player.getItemOnCursor();
        if (DaggerItem.getType(cursor) == type) {
            player.setItemOnCursor(null);
        } else {
            player.getInventory().removeItem(DaggerItem.create(type));
        }

        // Fallback if there's no real table block (e.g. crafted via the 2x2 personal grid).
        Location beamOrigin = (tableLoc != null) ? tableLoc : player.getLocation().getBlock().getLocation();

        if (tableLoc != null) {
            protectedAltars.put(tableLoc, type);
        }

        String coords = formatCoords(beamOrigin);
        String title = type.getColor() + type.getDisplayName() + " §7- " + coords;

        BossBar bar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_10);
        bar.setProgress(1.0);
        for (Player online : Bukkit.getOnlinePlayers()) {
            bar.addPlayer(online);
        }
        activeBars.put(type, bar);

        Bukkit.broadcastMessage("§5§lThe first " + type.getColor() + type.getDisplayName() + "§5§l has been forged by "
                + player.getName() + " at " + coords + "! Its altar hums with power...");

        Particle.DustOptions dustOptions = new Particle.DustOptions(
                org.bukkit.Color.fromRGB(colorFromType(type)), 1.2f);

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
                    if (tableLoc != null) {
                        protectedAltars.remove(tableLoc);
                    }

                    Location dropLoc = beamOrigin.clone().add(0.5, 1.05, 0.5);
                    dropLoc.getWorld().dropItem(dropLoc, DaggerItem.create(type));
                    dropLoc.getWorld().playSound(dropLoc, Sound.ENTITY_WITHER_SPAWN, 1f, 1f);

                    Bukkit.broadcastMessage("§5§lThe " + type.getColor() + type.getDisplayName()
                            + "§5§l may now be claimed at " + coords + "!");
                    cancel();
                    return;
                }

                bar.setProgress(progress);
                spawnBeamTick(beamOrigin, dustOptions);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnBeamTick(Location origin, Particle.DustOptions dustOptions) {
        Location base = origin.clone().add(0.5, 1.0, 0.5);
        for (double y = 0; y < 12; y += 0.5) {
            base.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0, 0, 0, 0, dustOptions);
        }
    }

    private int colorFromType(DaggerType type) {
        return switch (type) {
            case FIRE -> 0xFF5A28;
            case ICE -> 0x6EDCFF;
            case WATER -> 0x3C8CFF;
            case SOUL -> 0xBE50FF;
            case DARKNESS -> 0x6E6E78;
            case BACKSTAB -> 0xC81919;
            case WIND -> 0xE1E1E6;
            case ZEUS -> 0xFFDC3C;
            case HEALTH -> 0x50E664;
        };
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
