package com.example.daggers;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Handles the Health Dagger's non-combat effects:
 *  - Passive: +5 hearts (10 HP) as long as a Health Dagger is anywhere in the
 *    player's inventory (any storage slot or the offhand) - not just while
 *    actively holding it in the main hand.
 *  - Tier 1 ability (shift + right-click): +5 MORE hearts (15 -> 20 hearts
 *    total) for 30 seconds. The 60-second cooldown between uses is handled
 *    by the normal per-ability cooldown system in DaggerAbilityListener, not
 *    here - this class only cares about the effect's own duration.
 *
 * The "every 10th hit -> Regeneration III" and tier 2 damage buff both live
 * in DaggerDamageListener / DaggerAbilityListener, following the same
 * pattern as every other dagger's combat effects.
 */
public class HealthDaggerManager {

    private static final UUID PASSIVE_MODIFIER_ID = UUID.fromString("a1e9b6d2-0000-4c3d-9a3e-000000000001");
    private static final UUID BOOST_MODIFIER_ID = UUID.fromString("a1e9b6d2-0000-4c3d-9a3e-000000000002");

    private static final double PASSIVE_BONUS_HP = 10.0; // +5 hearts
    private static final double BOOST_BONUS_HP = 10.0;   // +5 more hearts on top of the passive
    private static final long BOOST_DURATION_MILLIS = 30_000L; // 30 seconds

    private final JavaPlugin plugin;

    public HealthDaggerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            syncPassive(player, hasHealthDaggerAnywhere(player));
        }
    }

    private boolean hasHealthDaggerAnywhere(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (DaggerItem.getType(item) == DaggerType.HEALTH) {
                return true;
            }
        }
        return DaggerItem.getType(player.getInventory().getItemInOffHand()) == DaggerType.HEALTH;
    }

    private void syncPassive(Player player, boolean holding) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        boolean hasModifier = attr.getModifiers().stream()
                .anyMatch(m -> m.getUniqueId().equals(PASSIVE_MODIFIER_ID));

        if (holding && !hasModifier) {
            attr.addModifier(new AttributeModifier(PASSIVE_MODIFIER_ID, "health-dagger-passive",
                    PASSIVE_BONUS_HP, AttributeModifier.Operation.ADD_NUMBER));
        } else if (!holding && hasModifier) {
            attr.getModifiers().stream()
                    .filter(m -> m.getUniqueId().equals(PASSIVE_MODIFIER_ID))
                    .findFirst()
                    .ifPresent(attr::removeModifier);
            if (player.getHealth() > attr.getValue()) {
                player.setHealth(attr.getValue());
            }
        }
    }

    /** Called from DaggerAbilityListener for the shift+right-click ability. Lasts 30 seconds. */
    public void activateBoost(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        boolean already = attr.getModifiers().stream()
                .anyMatch(m -> m.getUniqueId().equals(BOOST_MODIFIER_ID));

        if (!already) {
            attr.addModifier(new AttributeModifier(BOOST_MODIFIER_ID, "health-dagger-boost",
                    BOOST_BONUS_HP, AttributeModifier.Operation.ADD_NUMBER));
            player.setHealth(Math.min(attr.getValue(), player.getHealth() + BOOST_BONUS_HP));
        }

        long ticks = BOOST_DURATION_MILLIS / 50L; // 1 tick = 50ms
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            AttributeInstance a = player.getAttribute(Attribute.MAX_HEALTH);
            if (a == null) return;
            a.getModifiers().stream()
                    .filter(m -> m.getUniqueId().equals(BOOST_MODIFIER_ID))
                    .findFirst()
                    .ifPresent(a::removeModifier);
            if (player.getHealth() > a.getValue()) {
                player.setHealth(a.getValue());
            }
        }, ticks);
    }
}
