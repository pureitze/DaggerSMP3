package com.example.daggers.listeners;

import com.example.daggers.CooldownManager;
import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.DarknessInvisManager;
import com.example.daggers.FreezeManager;
import com.example.daggers.GroundEffectManager;
import com.example.daggers.HealthDaggerManager;
import com.example.daggers.Tier2BuffManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
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
    private static final int TIER2_COOLDOWN_SECONDS = 120;

    private final JavaPlugin plugin;
    private final CooldownManager cooldowns;
    private final FreezeManager freezeManager;
    private final Set<UUID> pendingSoulCurse;
    private final Set<UUID> pendingDarknessCurse;
    private final DarknessInvisManager darknessInvisManager;
    private final Tier2BuffManager tier2BuffManager;
    private final GroundEffectManager groundEffectManager;
    private final WindAbilityListener windAbilityListener;
    private final HealthDaggerManager healthDaggerManager;

    private final Set<UUID> pendingSoulTier2 = new HashSet<>();
    private final Map<UUID, Map<DaggerType, Long>> tier2CooldownEnd = new HashMap<>();

    public DaggerAbilityListener(JavaPlugin plugin, CooldownManager cooldowns, FreezeManager freezeManager,
                                  Set<UUID> pendingSoulCurse, Set<UUID> pendingDarknessCurse,
                                  DarknessInvisManager darknessInvisManager, Tier2BuffManager tier2BuffManager,
                                  GroundEffectManager groundEffectManager, WindAbilityListener windAbilityListener,
                                  HealthDaggerManager healthDaggerManager) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.freezeManager = freezeManager;
        this.pendingSoulCurse = pendingSoulCurse;
        this.pendingDarknessCurse = pendingDarknessCurse;
        this.darknessInvisManager = darknessInvisManager;
        this.tier2BuffManager = tier2BuffManager;
        this.groundEffectManager = groundEffectManager;
        this.windAbilityListener = windAbilityListener;
        this.healthDaggerManager = healthDaggerManager;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        DaggerType type = DaggerItem.getType(item);
        if (type == null) return;
        if (!player.isSneaking()) return;

        event.setCancelled(true);

        if (cooldowns.isOnCooldown(player.getUniqueId(), type)) {
            long remaining = cooldowns.getRemainingSeconds(player.getUniqueId(), type);
            player.sendMessage(type.getDisplayName() + " is on cooldown for " + remaining + "s.");
            return;
        }

        boolean success = switch (type) {
            case FIRE -> castFireball(player);
            case ICE -> castFreeze(player);
            case WATER -> castDolphinsGrace(player);
            case SOUL -> castSoulCurse(player);
            case DARKNESS -> castDarknessCurse(player);
            case BACKSTAB -> castGrapple(player);
            case WIND -> windAbilityListener.castSlam(player);
            case ZEUS -> castZeusLightning(player);
            case HEALTH -> castHealthBoost(player);
        };

        if (success) {
            cooldowns.startCooldown(player.getUniqueId(), type, getCooldownSeconds(type));
        }
    }

    private int getCooldownSeconds(DaggerType type) {
        return type == DaggerType.DARKNESS ? 75 : 60;
    }

    private boolean castFireball(Player player) {
        Fireball fireball = (Fireball) player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(true);
        fireball.setYield(1f);
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
            target.setHealth(Math.max(0, target.getHealth() - 6.0));
        }
    }

    private boolean castFreeze(Player player) {
        Entity targeted = player.getTargetEntity(6);
        if (!(targeted instanceof Player target)) {
            player.sendMessage("§cNo player in range to freeze.");
            return false;
        }

        freezeManager.freeze(target, 4000L);
        player.sendMessage("§bYou freeze " + target.getName() + "!");
        target.sendMessage("§bYou have been frozen by " + player.getName() + "!");
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

        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 160, 0, false, false));
        darknessInvisManager.activate(player, 8000L);
        return true;
    }

    private boolean castGrapple(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), direction, GRAPPLE_RANGE, e -> !e.equals(player));
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), direction, GRAPPLE_RANGE, FluidCollisionMode.NEVER, true);

        double entityDistance = entityHit != null
                ? entityHit.getHitPosition().distance(player.getEyeLocation().toVector()) : Double.MAX_VALUE;
        double blockDistance = blockHit != null
                ? blockHit.getHitPosition().distance(player.getEyeLocation().toVector()) : Double.MAX_VALUE;

        if (entityHit == null && blockHit == null) {
            player.sendMessage("§cNothing in range to grapple.");
            return false;
        }

        if (entityDistance < blockDistance && entityHit.getHitEntity() instanceof Player target) {
            Vector pull = target.getLocation().toVector().subtract(player.getLocation().toVector())
                    .normalize().multiply(1.4);
            pull.setY(Math.max(pull.getY(), 0.3));
            target.setVelocity(pull);
            player.sendMessage("§eYou grapple " + target.getName() + " toward you!");
        } else {
            Vector pull = blockHit.getHitPosition().subtract(player.getEyeLocation().toVector())
                    .normalize().multiply(1.4);
            pull.setY(Math.max(pull.getY(), 0.3));
            player.setVelocity(pull);
            player.sendMessage("§eYou grapple toward the block!");
        }

        return true;
    }

    private boolean castZeusLightning(Player player) {
        Entity targeted = player.getTargetEntity(30);
        if (!(targeted instanceof LivingEntity target)) {
            player.sendMessage("§cNo target in range to strike.");
            return false;
        }

        Location loc = target.getLocation();
        loc.getWorld().strikeLightningEffect(loc); // visual + sound only, no vanilla fire/damage
        target.setHealth(Math.max(0, target.getHealth() - 3.0)); // 1.5 hearts, true damage
        player.sendMessage("§eYou call down a lightning strike!");
        return true;
    }

    private boolean castHealthBoost(Player player) {
        healthDaggerManager.activateBoost(player);
        player.sendMessage("§aYou feel your vitality surge! (+5 hearts for 30s)");
        return true;
    }

    // Prevents anyone from ender-pearling away while within 16 blocks of a
    // player currently hidden by the Darkness Dagger's tier 2 invisibility.
    @EventHandler
    public void onPearlThrow(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null || item.getType() != org.bukkit.Material.ENDER_PEARL) return;

        Player thrower = event.getPlayer();
        for (Player other : org.bukkit.Bukkit.getOnlinePlayers()) {
            if (other.equals(thrower)) continue;
            if (!darknessInvisManager.isActive(other)) continue;
            if (!other.getWorld().equals(thrower.getWorld())) continue;
            if (other.getLocation().distance(thrower.getLocation()) > 16.0) continue;

            event.setCancelled(true);
            thrower.sendMessage("§8Something unseen prevents you from throwing that...");
            return;
        }
    }

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
            long remaining = getTier2RemainingSeconds(player, type);
            player.sendMessage(type.getDisplayName() + " tier 2 ability is on cooldown for " + remaining + "s.");
            return;
        }

        switch (type) {
            case FIRE -> castFireTier2(player);
            case ICE -> castIceTier2(player);
            case WATER -> castWaterTier2(player);
            case SOUL -> castSoulTier2(player);
            case DARKNESS -> castDarknessTier2(player);
            case BACKSTAB -> castBackstabTier2(player);
            case WIND -> castWindTier2(player);
            case ZEUS -> castZeusTier2(player);
            case HEALTH -> castHealthTier2(player);
        }

        startTier2Cooldown(player, type);
    }

    private void castFireTier2(Player player) {
        groundEffectManager.createFireField(player.getLocation(), 8, 8000L);
        tier2BuffManager.activate(player, DaggerType.FIRE, 8000L);
        player.sendMessage("§6§lThe ground erupts into flame!");
    }

    private void castIceTier2(Player player) {
        groundEffectManager.createIceField(player.getLocation(), 8, 8000L);
        tier2BuffManager.activate(player, DaggerType.ICE, 8000L);
        player.sendMessage("§b§lThe ground freezes over!");
    }

    private void castWaterTier2(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 608, 0));
        tier2BuffManager.activate(player, DaggerType.WATER, 8000L);
        player.sendMessage("§9§lThe tide surges through you!");
    }

    private void castSoulTier2(Player player) {
        pendingSoulTier2.add(player.getUniqueId());
        player.sendMessage("§5§lYour next hit will tear at your target's soul!");
    }

    private void castDarknessTier2(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 172, 0, false, false));
        tier2BuffManager.activate(player, DaggerType.DARKNESS, 8000L);
        player.sendMessage("§8§lYou fade into the shadows!");
    }

    private void castBackstabTier2(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 168, 1));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 189, 2));
        player.sendMessage("§4§lYou feel unstoppable!");
    }

    private void castWindTier2(Player player) {
        tier2BuffManager.activate(player, DaggerType.WIND, 20_000L);
        player.sendMessage("§f§lThe wind empowers your strikes!");
    }

    private void castZeusTier2(Player player) {
        Entity targeted = player.getTargetEntity(30);
        if (!(targeted instanceof LivingEntity target)) {
            player.sendMessage("§cNo target in range to strike.");
            return;
        }

        Location center = target.getLocation();
        center.getWorld().strikeLightningEffect(center);
        for (int i = 0; i < 3; i++) {
            Location offset = center.clone().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            center.getWorld().strikeLightningEffect(offset);
        }

        for (Entity nearby : center.getWorld().getNearbyEntities(center, 4, 4, 4)) {
            if (nearby instanceof Player p && !p.equals(player)) {
                p.setHealth(Math.max(0, p.getHealth() - 6.0)); // 3 hearts total, true damage
            }
        }
        player.sendMessage("§e§lYou summon a lightning storm!");
    }

    private void castHealthTier2(Player player) {
        tier2BuffManager.activate(player, DaggerType.HEALTH, 20_000L);
        player.sendMessage("§a§lYou feel unbreakable!");
    }

    public boolean consumeSoulTier2(UUID uuid) {
        return pendingSoulTier2.remove(uuid);
    }

    private boolean isOnTier2Cooldown(Player player, DaggerType type) {
        Map<DaggerType, Long> playerCooldowns = tier2CooldownEnd.get(player.getUniqueId());
        if (playerCooldowns == null) return false;
        Long end = playerCooldowns.get(type);
        return end != null && end > System.currentTimeMillis();
    }

    private long getTier2RemainingSeconds(Player player, DaggerType type) {
        Map<DaggerType, Long> playerCooldowns = tier2CooldownEnd.get(player.getUniqueId());
        if (playerCooldowns == null) return 0;
        Long end = playerCooldowns.get(type);
        if (end == null) return 0;
        return Math.max(0, (end - System.currentTimeMillis()) / 1000L);
    }

    private void startTier2Cooldown(Player player, DaggerType type) {
        Map<DaggerType, Long> playerCooldowns = tier2CooldownEnd.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        playerCooldowns.put(type, System.currentTimeMillis() + TIER2_COOLDOWN_SECONDS * 1000L);
    }
}
