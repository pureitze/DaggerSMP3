package com.example.daggers.listeners;

import com.example.daggers.CraftLimitManager;
import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Announces every dagger craft in chat. The FIRST craft of EACH dagger type
 * (tracked independently per type) gets special treatment instead of the normal
 * announcement: the crafting table it was made on becomes unbreakable (including
 * against explosions) for 8 minutes, grows a tall glowing beam (a stretched
 * ItemDisplay entity - NOT particles, NOT a real beacon) extending 250 blocks up
 * and 250 blocks down, and shows a Wither-style boss bar with the dagger's name
 * and coordinates. When the timer ends, the beam disappears, the table becomes
 * breakable again, and the dagger drops on top of it.
 *
 * Also enforces an optional per-type crafting cap (set via /dagger admin) -
 * once a type hits its configured limit, further crafts of that type are
 * blocked entirely.
 */
public class DaggerCraftListener implements Listener {

    private static final long FIRST_DAGGER_DELAY_TICKS = 8 * 60 * 20L; // 8 minutes
    private static final float BEAM_REACH = 250f; // blocks up AND down
    private static final double BEAM_SEGMENT_HEIGHT = 24.0; // blocks per segment - modest stretch, avoids culling issues

    private final JavaPlugin plugin;
    private final CraftLimitManager craftLimitManager;
    private final Set<DaggerType> firstCraftedTypes;
    private final ConcurrentHashMap<DaggerType, BossBar> activeBars = new ConcurrentHashMap<>();
    // block locations currently protected + beaming, and which dagger type each belongs to
    private final ConcurrentHashMap<Location, DaggerType> protectedAltars = new ConcurrentHashMap<>();

    public DaggerCraftListener(JavaPlugin plugin, CraftLimitManager craftLimitManager) {
        this.plugin = plugin;
        this.craftLimitManager = craftLimitManager;
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

        if (!craftLimitManager.canCraft(type)) {
            event.setCancelled(true);
            Integer limit = craftLimitManager.getLimit(type);
            player.sendMessage("§c" + type.getDisplayName() + " has reached its crafting limit ("
                    + craftLimitManager.getCraftedCount(type) + "/" + limit + ").");
            return;
        }

        if (!firstCraftedTypes.contains(type)) {
            firstCraftedTypes.add(type);
            saveClaimedTypes();
            craftLimitManager.incrementCraftedCount(type);

            Location tableLoc = (event.getInventory() instanceof CraftingInventory ci && ci.getLocation() != null)
                    ? ci.getLocation().getBlock().getLocation()
                    : null;

            // let the normal craft complete this tick, then redirect the result next tick
            Bukkit.getScheduler().runTask(plugin, () -> handleFirstOfType(player, type, tableLoc));
            return;
        }

        craftLimitManager.incrementCraftedCount(type);

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

        List<ItemDisplay> beam = spawnBeam(beamOrigin, type);

        Bukkit.broadcastMessage("§5§lThe first " + type.getColor() + type.getDisplayName() + "§5§l has been forged by "
                + player.getName() + " at " + coords + "! Its altar hums with power...");

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
                    for (ItemDisplay segment : beam) {
                        if (!segment.isDead()) {
                            segment.remove();
                        }
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
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    /**
     * Spawns a chain of stretched ItemDisplay segments that read as a tall glowing
     * beam reaching BEAM_REACH blocks up and BEAM_REACH blocks down from the
     * table. This is a real entity, not a particle effect and not a vanilla
     * beacon - so it isn't hidden by particle settings and isn't limited to
     * pointing only upward or needing sky access / a mineral pyramid.
     */
    private List<ItemDisplay> spawnBeam(Location tableLoc, DaggerType type) {
        var world = tableLoc.getWorld();
        double minY = world.getMinHeight();
        double maxY = world.getMaxHeight();

        // Reach as far as BEAM_REACH in each direction, but never past what the world actually has.
        double bottom = Math.max(minY, tableLoc.getY() - BEAM_REACH);
        double top = Math.min(maxY, tableLoc.getY() + 1.0 + BEAM_REACH);

        List<ItemDisplay> segments = new java.util.ArrayList<>();
        ItemStack beamItem = createBeamItem(type);

        double y = bottom;
        while (y < top) {
            double segmentHeight = Math.min(BEAM_SEGMENT_HEIGHT, top - y);
            Location spawnAt = new Location(world, tableLoc.getX() + 0.5, y, tableLoc.getZ() + 0.5);
            double finalSegmentHeight = segmentHeight;

            ItemDisplay segment = world.spawn(spawnAt, ItemDisplay.class, display -> {
                display.setItemStack(beamItem);
                display.setBillboard(Display.Billboard.FIXED);
                display.setBrightness(new Display.Brightness(15, 15));
                display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);

                Transformation transformation = new Transformation(
                        new Vector3f(0f, 0f, 0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f),
                        new Vector3f(1.0f, (float) (finalSegmentHeight / 16.0), 1.0f),
                        new AxisAngle4f(0f, 0f, 0f, 1f)
                );
                display.setTransformation(transformation);
            });

            segments.add(segment);
            y += BEAM_SEGMENT_HEIGHT;
        }

        return segments;
    }

    private ItemStack createBeamItem(DaggerType type) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(beamModelData(type));
        item.setItemMeta(meta);
        return item;
    }

    private int beamModelData(DaggerType type) {
        return switch (type) {
            case FIRE -> 5001;
            case ICE -> 5002;
            case WATER -> 5003;
            case SOUL -> 5004;
            case DARKNESS -> 5005;
            case BACKSTAB -> 5006;
            case WIND -> 5007;
            case ZEUS -> 5008;
            case HEALTH -> 5009;
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
