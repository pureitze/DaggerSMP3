package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.HitTracker;
import com.example.daggers.NoSprintManager;
import com.example.daggers.Tier2BuffManager;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;

public class DaggerDamageListener implements Listener {

    private final Set<UUID> pendingSoulCurse;
    private final Set<UUID> pendingDarknessCurse;
    private final HitTracker hitTracker;
    private final NoSprintManager noSprintManager;
    private final Tier2BuffManager tier2BuffManager;
    private final DaggerAbilityListener abilityListener;

    public DaggerDamageListener(Set<UUID> pendingSoulCurse, Set<UUID> pendingDarknessCurse, HitTracker hitTracker,
                                 NoSprintManager noSprintManager, Tier2BuffManager tier2BuffManager,
                                 DaggerAbilityListener abilityListener) {
        this.pendingSoulCurse = pendingSoulCurse;
        this.pendingDarknessCurse = pendingDarknessCurse;
        this.hitTracker = hitTracker;
        this.noSprintManager = noSprintManager;
        this.tier2BuffManager = tier2BuffManager;
        this.abilityListener = abilityListener;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        DaggerType type = DaggerItem.getType(weapon);
        if (type == null) return;

        boolean isSweep = event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
        boolean isSprint = attacker.isSprinting();
        boolean isCrit = isApproximateCrit(attacker);

        double multiplier = switch (type) {
            case FIRE -> handleFireDagger(attacker, target, isCrit, isSweep, isSprint);
            case ICE -> handleIceDagger(attacker, isCrit, isSweep, isSprint);
            case WATER -> handleWaterDagger(attacker);
            case SOUL -> handleSoulDagger(attacker, target, isCrit);
            case DARKNESS -> handleDarknessDagger(attacker, target, isCrit, isSweep, isSprint);
            case BACKSTAB -> handleBackstabDagger(attacker, target, isCrit, isSweep, isSprint);
        };

        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }

        if (pendingSoulCurse.remove(attacker.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1));
        }
        if (pendingDarknessCurse.remove(attacker.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0));
            if (target instanceof Player targetPlayer) {
                noSprintManager.disableSprint(targetPlayer, 120);
            }
        }

        // Soul Dagger Tier 2: one-time bonus hit, consumed here
        if (abilityListener.consumeSoulTier2(attacker.getUniqueId())) {
            target.setHealth(Math.max(0, target.getHealth() - 6.0)); // 3 hearts, true damage
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 0)); // 5s
        }
    }

    private double handleFireDagger(Player attacker, LivingEntity target, boolean isCrit, boolean isSweep, boolean isSprint) {
        target.setFireTicks(Math.max(target.getFireTicks(), 100));

        if (tier2BuffManager.hasBuff(attacker, DaggerType.FIRE)) {
            return isCrit ? 2.0 : 1.5;
        }

        if (attacker.getFireTicks() > 0) {
            if (isCrit) return 1.8;
            if (isSweep || isSprint) return 1.3;
        }
        return 1.0;
    }

    private double handleIceDagger(Player attacker, boolean isCrit, boolean isSweep, boolean isSprint) {
        boolean onIce = isStandingOnIce(attacker);

        if (tier2BuffManager.hasBuff(attacker, DaggerType.ICE) && onIce) {
            return isCrit ? 2.0 : 1.5;
        }

        if (onIce) {
            if (isCrit) return 1.8;
            if (isSweep || isSprint) return 1.3;
        }
        return 1.0;
    }

    private double handleWaterDagger(Player attacker) {
        if (tier2BuffManager.hasBuff(attacker, DaggerType.WATER)) {
            return 1.8;
        }
        return attacker.isInWater() ? 1.5 : 1.0;
    }

    private double handleSoulDagger(Player attacker, LivingEntity target, boolean isCrit) {
        int hitCount = hitTracker.recordHit(attacker.getUniqueId(), target.getUniqueId());
        double multiplier = 1.0;

        if (hitCount % 5 == 0) {
            multiplier = isCrit ? 2.0 : 1.5;
        }

        if (hitCount % 10 == 0) {
            double stolenHealth = 3.0;
            target.setHealth(Math.max(0, target.getHealth() - stolenHealth));
            double maxHealth = attacker.getAttribute(Attribute.MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + stolenHealth));
            attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 2));
        }

        return multiplier;
    }

    private double handleDarknessDagger(Player attacker, LivingEntity target, boolean isCrit, boolean isSweep, boolean isSprint) {
        if (tier2BuffManager.hasBuff(attacker, DaggerType.DARKNESS)) {
            return isCrit ? 2.0 : 1.5;
        }

        int hitCount = hitTracker.recordHit(attacker.getUniqueId(), target.getUniqueId());
        double multiplier = 1.0;

        if (hitCount % 5 == 0) {
            multiplier = isCrit ? 2.0 : 1.5;
        }

        if (hitCount % 10 == 0) {
            damageArmorDurability(target, 25);
        }

        return multiplier;
    }

    private double handleBackstabDagger(Player attacker, LivingEntity target, boolean isCrit, boolean isSweep, boolean isSprint) {
        if (isBackstab(attacker, target)) {
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.7f);
            return isCrit ? 2.0 : 1.8;
        }
        if (isCrit) return 1.5;
        if (isSweep || isSprint) return 1.2;
        return 1.2;
    }

    private boolean isStandingOnIce(Player player) {
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        Material type = below.getType();
        return type == Material.ICE || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
    }

    private boolean isApproximateCrit(Player attacker) {
        return attacker.getFallDistance() > 0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isSprinting()
                && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    private boolean isBackstab(Player attacker, LivingEntity target) {
        Vector targetFacing = target.getLocation().getDirection().setY(0).normalize();
        Vector targetToAttacker = attacker.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .setY(0);

        if (targetToAttacker.lengthSquared() == 0) return false;
        targetToAttacker.normalize();

        double dot = targetFacing.dot(targetToAttacker);
        return dot < -0.5;
    }

    private void damageArmorDurability(LivingEntity target, int amount) {
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) return;

        ItemStack[] armor = equipment.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack piece = armor[i];
            if (piece == null || piece.getType() == Material.AIR) continue;

            ItemMeta meta = piece.getItemMeta();
            if (meta instanceof Damageable damageable) {
                int newDamage = damageable.getDamage() + amount;
                if (newDamage >= piece.getType().getMaxDurability()) {
                    armor[i] = null;
                } else {
                    damageable.setDamage(newDamage);
                    piece.setItemMeta((ItemMeta) damageable);
                }
            }
        }
        equipment.setArmorContents(armor);
    }
}
