package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.HitTracker;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityEquipment;
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

    // Players who used a right-click "curse my next hit" ability and are
    // waiting to land it. Shared with DaggerAbilityListener.
    private final Set<UUID> pendingSoulCurse;
    private final Set<UUID> pendingDarknessCurse;
    private final HitTracker hitTracker;

    public DaggerDamageListener(Set<UUID> pendingSoulCurse, Set<UUID> pendingDarknessCurse, HitTracker hitTracker) {
        this.pendingSoulCurse = pendingSoulCurse;
        this.pendingDarknessCurse = pendingDarknessCurse;
        this.hitTracker = hitTracker;
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
            case DARKNESS -> handleDarknessDagger(attacker, target, isCrit);
            case BACKSTAB -> handleBackstabDagger(attacker, target, isCrit, isSweep, isSprint);
        };

        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }

        // Apply any pending "next hit is cursed" effects
        if (pendingSoulCurse.remove(attacker.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0));    // 3s
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 1)); // 5s, Slowness II
        }
        if (pendingDarknessCurse.remove(attacker.getUniqueId())) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 120, 0)); // 6s
        }
    }

    private double handleFireDagger(Player attacker, LivingEntity target, boolean isCrit, boolean isSweep, boolean isSprint) {
        // Base ability: every hit sets the target on fire (5 seconds)
        target.setFireTicks(Math.max(target.getFireTicks(), 100));

        // Bonus damage only while the attacker themself is burning
        if (attacker.getFireTicks() > 0) {
            if (isCrit) return 1.8;
            if (isSweep || isSprint) return 1.3;
        }
        return 1.0;
    }

    private double handleIceDagger(Player attacker, boolean isCrit, boolean isSweep, boolean isSprint) {
        if (isStandingOnIce(attacker)) {
            if (isCrit) return 1.8;
            if (isSweep || isSprint) return 1.3;
        }
        return 1.0;
    }

    private double handleWaterDagger(Player attacker) {
        // Water dagger boosts sprint, sweep, AND regular hits equally, so we
        // don't even need to distinguish attack type here - just "in water".
        return attacker.isInWater() ? 1.5 : 1.0;
    }

    private double handleSoulDagger(Player attacker, LivingEntity target, boolean isCrit) {
        int hitCount = hitTracker.recordHit(attacker.getUniqueId(), target.getUniqueId());
        double multiplier = 1.0;

        if (hitCount % 5 == 0) {
            multiplier = isCrit ? 2.0 : 1.5;
        }

        if (hitCount % 10 == 0) {
            double stolenHealth = 3.0; // 1.5 hearts = 3 HP (1 heart = 2 HP)

            target.setHealth(Math.max(0, target.getHealth() - stolenHealth));

            double maxHealth = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + stolenHealth));

            attacker.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 2)); // Regen III, 2s
        }

        return multiplier;
    }

    private double handleDarknessDagger(Player attacker, LivingEntity target, boolean isCrit) {
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
        if (isBackstab(attacker, target)) return 2.5;
        if (isCrit) return 1.8;
        if (isSweep || isSprint) return 1.3;
        return 1.0;
    }

    private boolean isStandingOnIce(Player player) {
        Block below = player.getLocation().subtract(0, 1, 0).getBlock();
        Material type = below.getType();
        return type == Material.ICE || type == Material.PACKED_ICE
                || type == Material.BLUE_ICE || type == Material.FROSTED_ICE;
    }

    /**
     * Approximates vanilla's critical-hit condition using public API only:
     * falling, airborne, not sprinting, no blindness. This isn't pixel-perfect
     * (exact vanilla crit logic lives server-side and isn't exposed by Bukkit),
     * but it's the same approximation most plugins use and feels right in practice.
     */
    private boolean isApproximateCrit(Player attacker) {
        return attacker.getFallDistance() > 0f
                && !attacker.isOnGround()
                && !attacker.isInsideVehicle()
                && !attacker.isSprinting()
                && !attacker.hasPotionEffect(PotionEffectType.BLINDNESS);
    }

    /**
     * A "backstab" is any hit landed while the target is facing roughly away
     * from the attacker - specifically, the attacker is within a ~120-degree
     * cone directly behind the target's facing direction. Bukkit has no
     * built-in concept of this, so this is a reasonable geometric approximation.
     */
    private boolean isBackstab(Player attacker, LivingEntity target) {
        Vector targetFacing = target.getLocation().getDirection().setY(0).normalize();
        Vector targetToAttacker = attacker.getLocation().toVector()
                .subtract(target.getLocation().toVector())
                .setY(0);

        if (targetToAttacker.lengthSquared() == 0) return false;
        targetToAttacker.normalize();

        double dot = targetFacing.dot(targetToAttacker);
        // dot == -1 means the attacker is directly behind the target's facing
        // direction. -0.5 gives roughly a 120-degree cone behind the target.
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
                    armor[i] = null; // the piece breaks
                } else {
                    damageable.setDamage(newDamage);
                    piece.setItemMeta((ItemMeta) damageable);
                }
            }
        }
        equipment.setArmorContents(armor);
    }
}
