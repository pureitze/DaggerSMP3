package com.example.daggers.listeners;

import com.example.daggers.CooldownManager;
import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.DarknessInvisManager;
import com.example.daggers.FreezeManager;
import com.example.daggers.GroundEffectManager;
import com.example.daggers.Tier2BuffManager;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DaggerAbilityListener implements Listener {

    private static final String FIRE_DAGGER_PROJECTILE_TAG = "flame_dagger_fireball";
    private static final double GRAPPLE_RANGE = 32.0;
    private static final int TIER2_COOLDOWN_SECONDS = 90;

    private final JavaPlugin plugin;
    private final CooldownManager cooldowns;
    private final FreezeManager freezeManager;
    private final Set<UUID> pendingSoulCurse;
    private final Set<UUID> pendingDarknessCurse;
    private final DarknessInvisManager darknessInvisManager;
    private final Tier2BuffManager tier2BuffManager;
    private final GroundEffectManager groundEffectManager;
    private final Set<UUID> pendingSoulTier2 = new HashSet<>();

    // Separate, simple per-player-per-type cooldown tracker just for Tier 2
    // abilities, so it doesn't interfere with the existing Tier 1 cooldowns.
    private final Map<UUID, Map<DaggerType, Long>> tier2CooldownEnd = new HashMap<>();

    public DaggerAbilityListener(JavaPlugin plugin, CooldownManager cooldowns, FreezeManager freezeManager,
                                  Set<UUID> pendingSoulCurse, Set<UUID> pendingDarknessCurse,
                                  DarknessInvisManager darknessInvisManager, Tier2BuffManager tier2BuffManager,
                                  GroundEffectManager groundEffectManager) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.freezeManager = freezeManager;
        this.pendingSoulCurse = pendingSoulCurse;
        this.pendingDarknessCurse = pendingDarknessCurse;
        this.darknessInvisManager = darknessInvisManager;
        this.tier2BuffManager = tier2BuffManager;
        this.groundEffectManager = groundEffectManager;
    }

    // ================= TIER 1 ABILITY (Shift + Right-click) =================

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return; // ignore the paired off-hand copy of this event

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        DaggerType type = DaggerItem.getType(item);
        if (type == null) return;

        if (!player.isSneaking()) return; // plain right-click: do nothing, let off-hand item use proceed normally

        event.setCancelled(true); // only cancel (and use the ability) on Shift + right-click

        if (cooldowns.isOnCooldown(player.getUniqueId(), type)) {
            long remaining = cooldowns.getRemainingSeconds(player.getUniqueId(), type);
            player.sendMessage("§c" + type.getDisplayName() + " ability on cooldown: " + remaining + "s");
            return;
        }

        boolean success = switch (type) {
            case FIRE -> castFireball(player);
            case ICE -> castFreeze(player);
            case WATER -> castDolphinsGrace(player);
            case SOUL -> castSoulCurse(player);
            case DARKNESS -> castDarknessCurse(player);
            case BACKSTAB -> castGrapple(player);
        };

        if (success) {
            cooldowns.startCooldown(player.getUniqueId(), type, getCooldownSeconds(type));
        }
    }

    private int getCooldownSeconds(DaggerType type) {
        return type == DaggerType.DARKNESS ? 75 : 60;
    }

    private boolean castFireball(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(true);
        fireball.setYield(1.0f);
        fireball.setVelocity(player.getLocation().getDirection().multiply(1.5));
        fireball.setMetadata(FIRE_DAGGER_PROJECTILE_TAG, new FixedMetadataValue(plugin, true));
        player.sendMessage("§6You hurl a fireball!");
        return true;
    }

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!fireball.hasMetadata(FIRE_DAGGER_PROJECTILE_TAG)) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            double trueDamage = 6.0;
            target.setHealth(Math.max(0, target.getHealth() - trueDamage));
        }
    }

    private boolean castFreeze(Player player) {
        Entity targetEntity = player.getTargetEntity(6);
        if (!(targetEntity instanceof Player target)) {
            player.sendMessage("§cNo player in range to freeze.");
            return false;
        }

        freezeManager.freeze(target, 80);
        target.sendMessage("§b" + player.getName() + " froze you in place!");
        player.sendMessage("§bYou froze " + target.getName() + "!");
        return true;
    }

    private boolean castDolphinsGrace(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 300, 0));
        player.sendMessage("§bDolphin's Grace activated!");
        return true;
    }

    private boolean castSoulCurse(Player player) {
        pendingSoulCurse.add(player.getUniqueId());
        player.sendMessage("§5Your next hit will curse your target!");
        return true;
    }

    private boolean castDarknessCurse(Player player) {
        pendingDarknessCurse.add(player.getUniqueId());
        player.sendMessage("§8Your next hit will engulf your target in darkness!");

        final long durationTicks = 160L; // 8 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int) durationTicks, 0, false, false));
        darknessInvisManager.activate(player, durationTicks);

        new BukkitRunnable() {
            long elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }
                for (Player enemy : Bukkit.getOnlinePlayers()) {
                    if (enemy.equals(player)) continue;
                    if (!enemy.getWorld().equals(player.getWorld())) continue;
                    if (enemy.getLocation().distance(player.getLocation()) > 16.0) continue;

                    enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false));
                }
                elapsed += 20;
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    @EventHandler
    public void onPearlThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_PEARL) return;

        Player thrower = event.getPlayer();
        for (Player nearby : Bukkit.getOnlinePlayers()) {
            if (nearby.equals(thrower)) continue;
            if (!darknessInvisManager.isActive(nearby)) continue;
            if (!nearby.getWorld().equals(thrower.getWorld())) continue;
            if (nearby.getLocation().distance(thrower.getLocation()) > 16.0) continue;

            event.setCancelled(true);
            thrower.sendMessage("§8Something unseen prevents you from throwing that...");
            return;
        }
    }

    private boolean castGrapple(Player player) {
        Vector direction = player.getEyeLocation().getDirection();

        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), direction, GRAPPLE_RANGE,
                entity -> entity instanceof Player p && !p.equals(player)
        );
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), direction, GRAPPLE_RANGE, FluidCollisionMode.NEVER, true
        );

        double entityDistance = entityHit != null
                ? entityHit.getHitPosition().distance(player.getEyeLocation().toVector())
                : Double.MAX_VALUE;
        double blockDistance = blockHit != null
                ? blockHit.getHitPosition().distance(player.getEyeLocation().toVector())
                : Double.MAX_VALUE;

        if (entityHit == null && blockHit == null) {
            player.sendMessage("§cNothing in range to grapple.");
            return false;
        }

        if (entityDistance < blockDistance && entityHit.getHitEntity() instanceof Player target) {
            Vector pull = player.getLocation().toVector()
                    .subtract(target.getLocation().toVector())
                    .normalize()
                    .multiply(1.8);
            pull.setY(Math.max(pull.getY(), 0.3));
            target.setVelocity(pull);
            player.sendMessage("§eYou grappled " + target.getName() + " towards you!");
        } else {
            Vector pull = blockHit.getHitPosition()
                    .subtract(player.getEyeLocation().toVector())
                    .normalize()
                    .multiply(1.8);
            pull.setY(Math.max(pull.getY(), 0.3));
            player.setVelocity(pull);
            player.sendMessage("§eYou grapple toward the block!");
        }
        return true;
    }

    // ================= TIER 2 ABILITY (Shift + Swap-hands) =================

    @EventHandler
    public void onTier2Ability(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        DaggerType type = DaggerItem.getType(item);
        if (type == null) return;
        if (DaggerItem.getTier(item) < 2) return;

        event.setCancelled(true);

        if (isOnTier2Cooldown(player, type)) {
            player.sendMessage("§cTier II ability on cooldown: " + getTier2RemainingSeconds(player, type) + "s");
            return;
        }

        switch (type) {
            case FIRE -> castFireTier2(player);
            case ICE -> castIceTier2(player);
            case WATER -> castWaterTier2(player);
            case SOUL -> castSoulTier2(player);
            case DARKNESS -> castDarknessTier2(player);
            case BACKSTAB -> castBackstabTier2(player);
        }

        startTier2Cooldown(player, type);
    }

    private void castFireTier2(Player player) {
        groundEffectManager.createFireField(player.getLocation(), 8, 320L); // 16s
        tier2BuffManager.activate(player, DaggerType.FIRE, 160L); // 8s
        player.sendMessage("§6§lThe ground erupts into flame!");
    }

    private void castIceTier2(Player player) {
        groundEffectManager.createIceField(player.getLocation(), 8, 320L); // 16s
        tier2BuffManager.activate(player, DaggerType.ICE, 160L); // 8s
        player.sendMessage("§b§lThe ground freezes over!");
    }

    private void castWaterTier2(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 600, 0)); // 30s
        tier2BuffManager.activate(player, DaggerType.WATER, 160L); // 8s
        player.sendMessage("§9§lThe tide surges through you!");
    }

    private void castSoulTier2(Player player) {
        pendingSoulTier2.add(player.getUniqueId());
        player.sendMessage("§5§lYour next hit will tear at your target's soul!");
    }

    private void castDarknessTier2(Player player) {
        final long durationTicks = 160L; // 8 seconds
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, (int) durationTicks, 0, false, false));
        tier2BuffManager.activate(player, DaggerType.DARKNESS, durationTicks);
        player.sendMessage("§8§lYou fade into the shadows!");
    }

    private void castBackstabTier2(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DAMAGE_RESISTANCE, 160, 1)); // Resistance II, 8s
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 2)); // Speed III, 8s
        player.sendMessage("§4§lYou feel unstoppable!");
    }

    /** Exposes the Soul Tier 2 "next hit" flag to DaggerDamageListener. */
    public boolean consumeSoulTier2(UUID playerId) {
        return pendingSoulTier2.remove(playerId);
    }

    private boolean isOnTier2Cooldown(Player player, DaggerType type) {
        Map<DaggerType, Long> playerCooldowns = tier2CooldownEnd.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        Long endTime = playerCooldowns.get(type);
        return endTime != null && endTime > System.currentTimeMillis();
    }

    private long getTier2RemainingSeconds(Player player, DaggerType type) {
        Map<DaggerType, Long> playerCooldowns = tier2CooldownEnd.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        Long endTime = playerCooldowns.get(type);
        if (endTime == null) return 0;
        return Math.max(0, (endTime - System.currentTimeMillis()) / 1000);
    }

    private void startTier2Cooldown(Player player, DaggerType type) {
        tier2CooldownEnd
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(type, System.currentTimeMillis() + TIER2_COOLDOWN_SECONDS * 1000L);
    }
}
