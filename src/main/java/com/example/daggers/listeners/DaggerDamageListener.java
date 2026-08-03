package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.HitTracker;
import com.example.daggers.NoSprintManager;
import com.example.daggers.Tier2BuffManager;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;

public class DaggerDamageListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<UUID> pendingSoulCurse;
    private final Set<UUID> pendingDarknessCurse;
    private final HitTracker hitTracker;
    private final NoSprintManager noSprintManager;
    private final Tier2BuffManager tier2BuffManager;
    private final DaggerAbilityListener abilityListener;

    public DaggerDamageListener(JavaPlugin plugin, Set<UUID> pendingSoulCurse, Set<UUID> pendingDarknessCurse,
                                 HitTracker hitTracker, NoSprintManager noSprintManager,
                                 Tier2BuffManager tier2BuffManager, DaggerAbilityListener abilityListener) {
        this.plugin = plugin;
        this.pendingSoulCurse = pendingSoulCurse;
        this.pendingDarknessCurse = pendingDarknessCurse;
        this.hitTracker = hitTracker;
        this.noSprintManager = noSprintManager;
        this.tier2BuffManager = tier2BuffManager;
        this.abilityListener = abilityListener;
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        DaggerType type = DaggerItem.getType(item);
        if (type == null) return;

        boolean isSweep = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        boolean isSprint = player.isSprinting();
        boolean isCrit = isApproximateCrit(player);

        double multiplier = switch (type) {
            case FIRE -> handleFireDagger(player, target, isCrit);
            case ICE -> handleIceDagger(player, isCrit);
            case WATER -> handleWaterDagger(player, isCrit);
            case SOUL -> handleSoulDagger(player, target, isCrit);
            case DARKNESS -> handleDarknessDagger(player, target, isCrit);
            case BACKSTAB -> handleBackstabDagger(player, target, isCrit, isSweep, isSprint);
            case WIND -> handleWindDagger(player, isCrit);
            case ZEUS -> handleZeusDagger(player, target, isCrit);
            case HEALTH -> handleHealthDagger(player, target, isCrit);
        };

        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }

        if (pendingSoulCurse.remove(player.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }

        if (pendingDarknessCurse.remove(player.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
            if (target instanceof Player targetPlayer) {
                noSprintManager.disableSprint(targetPlayer, 3000L);
            }
        }

        if (abilityListener.consumeSoulTier2(player.getUniqueId())) {
            target.setHealth(Math.max(0, target.getHealth() - 6.0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0));
        }
    }

    // --- Base damage: 1.1x regular/sprint/sweep, 1.3x crit for every dagger except Backstab.
    //     Fire and Ice get an elevated 1.3x/1.6x while their special condition is active.
    //     Tier 2 (where applicable): 1.4x regular/sprint/sweep, 1.7x crit.

    private double handleFireDagger(Player player, LivingEntity target, boolean isCrit) {
        target.setFireTicks(Math.max(target.getFireTicks(), 100));

        if (tier2BuffManager.hasBuff(player, DaggerType.FIRE)) {
            return isCrit ? 1.7 : 1.4;
        }
        if (player.getFireTicks() > 0) {
            return isCrit ? 1.6 : 1.3;
        }
        return isCrit ? 1.3 : 1.1;
    }

    private double handleIceDagger(Player player, boolean isCrit) {
        boolean onIce = isStandingOnIce(player);

        if (tier2BuffManager.hasBuff(player, DaggerType.ICE)) {
            return isCrit ? 1.7 : 1.4;
        }
        if (onIce) {
            return isCrit ? 1.6 : 1.3;
        }
        return isCrit ? 1.3 : 1.1;
    }

    private double handleWaterDagger(Player player, boolean isCrit) {
        if (tier2BuffManager.hasBuff(player, DaggerType.WATER)) {
            return isCrit ? 1.7 : 1.4;
        }
        return isCrit ? 1.3 : 1.1;
    }

    private double handleSoulDagger(Player player, LivingEntity target, boolean isCrit) {
        int hits = hitTracker.recordHit(player.getUniqueId(), target.getUniqueId());

        if (hits % 10 == 0) {
            double stolen = 3.0;
            target.setHealth(Math.max(0, target.getHealth() - stolen));

            double maxHealth = player.getAttribute(Attribute.MAX_HEALTH).getValue();
            player.setHealth(Math.min(maxHealth, player.getHealth() + stolen));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 2));
        }

        return isCrit ? 1.3 : 1.1;
    }

    private double handleDarknessDagger(Player player, LivingEntity target, boolean isCrit) {
        if (tier2BuffManager.hasBuff(player, DaggerType.DARKNESS)) {
            return isCrit ? 1.7 : 1.4;
        }

        int hits = hitTracker.recordHit(player.getUniqueId(), target.getUniqueId());
        if (hits % 10 == 0) {
            damageArmorDurability(target, 25);
        }

        return isCrit ? 1.3 : 1.1;
    }

    // Backstab is intentionally untouched by this rework.
    private double handleBackstabDagger(Player player, LivingEntity target, boolean isCrit, boolean isSweep, boolean isSprint) {
        if (isBackstab(player, target)) {
            target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            return isCrit ? 2.0 : 1.8;
        }
        if (isCrit) return 1.5;
        if (isSweep || isSprint) return 1.3;
        return 1.3;
    }

    private double handleWindDagger(Player player, boolean isCrit) {
        if (tier2BuffManager.hasBuff(player, DaggerType.WIND)) {
            return isCrit ? 1.7 : 1.4;
        }
        return isCrit ? 1.3 : 1.1;
    }

    private double handleZeusDagger(Player player, LivingEntity target, boolean isCrit) {
        int hits = hitTracker.recordHit(player.getUniqueId(), target.getUniqueId());
        if (hits % 10 == 0) {
            trapInCobweb(target);
        }
        return isCrit ? 1.3 : 1.1;
    }

    private double handleHealthDagger(Player player, LivingEntity target, boolean isCrit) {
        if (tier2BuffManager.hasBuff(player, DaggerType.HEALTH)) {
            return isCrit ? 1.7 : 1.4;
        }

        int hits = hitTracker.recordHit(player.getUniqueId(), target.getUniqueId());
        if (hits % 10 == 0) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
        }

        return isCrit ? 1.3 : 1.1;
    }

    private void trapInCobweb(LivingEntity target) {
        Block head = target.getLocation().add(0, 1, 0).getBlock();
        if (head.getType() != Material.AIR) return; // don't overwrite something already there

        head.setType(Material.COBWEB);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (head.getType() == Material.COBWEB) {
                head.setType(Material.AIR);
            }
        }, 100L); // reverts after 5s
    }

    private boolean isStandingOnIce(Player player) {
        Material type = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        return type == Material.ICE || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
    }

    private boolean isApproximateCrit(Player player) {
        return player.getFallDistance() > 0f
                && !player.isOnGround()
                && !player.isInsideVehicle()
                && !player.isSprinting()
                && !player.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    private boolean isBackstab(Player player, LivingEntity target) {
        Vector targetFacing = target.getLocation().getDirection().setY(0).normalize();
        Vector toAttacker = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (toAttacker.lengthSquared() == 0) return false;
        toAttacker.normalize();
        return targetFacing.dot(toAttacker) < -0.5;
    }

    private void damageArmorDurability(LivingEntity target, int amount) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) return;

        ItemStack[] armor = equipment.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType() == Material.AIR) continue;

            ItemMeta meta = piece.getItemMeta();
            if (!(meta instanceof Damageable damageable)) continue;

            int newDamage = damageable.getDamage() + amount;
            if (newDamage >= piece.getType().getMaxDurability()) {
                armor[i] = null;
            } else {
                damageable.setDamage(newDamage);
                piece.setItemMeta(meta);
            }
        }
        equipment.setArmorContents(armor);
    }
}
