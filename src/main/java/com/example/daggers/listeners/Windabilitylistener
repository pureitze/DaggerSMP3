package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles all of the Wind Dagger's movement-based kit:
 *  - Passive: double jump (5 blocks up, 15s cooldown)
 *  - Tier 1 ability (shift + right-click): launch 7 blocks up, slam on landing
 *  - Tier 2 passive: double-tap sneak to dash forward 10 blocks
 *
 * The tier 2 *damage buff* ability (shift + offhand) is NOT handled here —
 * that goes through the existing Tier2BuffManager / onTier2Ability path in
 * DaggerAbilityListener, same as every other dagger's T2 buff.
 */
public class WindAbilityListener implements Listener {

    private static final long DOUBLE_JUMP_COOLDOWN_MS = 15_000L;
    private static final double DOUBLE_JUMP_HEIGHT = 5.0;

    private static final double SLAM_LAUNCH_HEIGHT = 7.0;
    private static final double SLAM_RADIUS = 8.0;
    private static final double SLAM_DAMAGE = 6.0; // 3 hearts, true damage (bypasses armor)

    private static final long DASH_DOUBLE_TAP_WINDOW_MS = 400L;
    private static final long DASH_COOLDOWN_MS = 8_000L;
    private static final double DASH_DISTANCE = 10.0;

    private final JavaPlugin plugin;

    private final Map<UUID, Long> doubleJumpCooldownEnd = new HashMap<>();
    private final Set<UUID> awaitingSlamLanding = new HashSet<>();
    private final Map<UUID, Long> lastSneakPress = new HashMap<>();
    private final Map<UUID, Long> dashCooldownEnd = new HashMap<>();

    public WindAbilityListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isHoldingWind(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return DaggerItem.getType(item) == DaggerType.WIND;
    }

    private boolean isHoldingWindTier2(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return DaggerItem.getType(item) == DaggerType.WIND && DaggerItem.getTier(item) >= 2;
    }

    // ---------------- Passive: double jump ----------------
    // Standard trick: give allowFlight to Wind Dagger holders. A double-tap
    // of the jump key while airborne with allowFlight=true makes the client
    // attempt to enter flight, which fires this event — we cancel that and
    // apply our own upward velocity instead.
    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!isHoldingWind(player)) return;
        if (player.isFlying()) return;

        event.setCancelled(true);
        player.setFlying(false);

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long cdEnd = doubleJumpCooldownEnd.get(uuid);
        if (cdEnd != null && now < cdEnd) {
            player.sendActionBar("§bDouble Jump: " + ((cdEnd - now) / 1000 + 1) + "s");
            return;
        }

        launchUpward(player, DOUBLE_JUMP_HEIGHT);
        doubleJumpCooldownEnd.put(uuid, now + DOUBLE_JUMP_COOLDOWN_MS);
    }

    // Keeps allowFlight synced to whether they're holding a Wind Dagger,
    // and watches for landing after a slam launch.
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        boolean holdingWind = isHoldingWind(player);

        if (holdingWind && !player.isFlying() && !player.getAllowFlight()) {
            player.setAllowFlight(true);
        } else if (!holdingWind && player.getAllowFlight()
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            player.setAllowFlight(false);
        }

        UUID uuid = player.getUniqueId();
        if (awaitingSlamLanding.contains(uuid) && player.isOnGround()) {
            awaitingSlamLanding.remove(uuid);
            slam(player);
        }
    }

    private void launchUpward(Player player, double height) {
        Vector v = player.getVelocity();
        double vy = Math.sqrt(2 * 0.08 * height); // rough Minecraft gravity approximation — tune as needed
        player.setVelocity(new Vector(v.getX(), vy, v.getZ()));
    }

    /** Called from DaggerAbilityListener when a player shift+right-clicks with a Wind Dagger. */
    public boolean castSlam(Player player) {
        launchUpward(player, SLAM_LAUNCH_HEIGHT);
        awaitingSlamLanding.add(player.getUniqueId());
        return true;
    }

    private void slam(Player player) {
        Location loc = player.getLocation();
        for (Entity nearby : loc.getWorld().getNearbyEntities(loc, SLAM_RADIUS, SLAM_RADIUS, SLAM_RADIUS)) {
            if (nearby.getUniqueId().equals(player.getUniqueId())) continue;
            if (!(nearby instanceof Player target)) continue;

            target.setHealth(Math.max(0, target.getHealth() - SLAM_DAMAGE));
            target.damage(0.0); // hurt sound/animation without extra damage
        }
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
    }

    // ---------------- Tier 2 passive: double-sneak dash ----------------
    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) return; // only care about the press, not release
        Player player = event.getPlayer();
        if (!isHoldingWindTier2(player)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        Long lastPress = lastSneakPress.put(uuid, now);
        if (lastPress == null || now - lastPress > DASH_DOUBLE_TAP_WINDOW_MS) {
            return; // first press — wait for the second
        }

        Long cdEnd = dashCooldownEnd.get(uuid);
        if (cdEnd != null && now < cdEnd) {
            player.sendActionBar("§bDash: " + ((cdEnd - now) / 1000 + 1) + "s");
            return;
        }

        dash(player);
        dashCooldownEnd.put(uuid, now + DASH_COOLDOWN_MS);
        lastSneakPress.remove(uuid);
    }

    private void dash(Player player) {
        Vector direction = player.getLocation().getDirection().setY(0).normalize();
        // Velocity doesn't map 1:1 to blocks due to drag — this multiplier
        // is a starting point, test in-game and adjust to taste.
        Vector impulse = direction.multiply(DASH_DISTANCE / 5.0);
        impulse.setY(0.15);
        player.setVelocity(impulse);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1.4f);
    }
}
