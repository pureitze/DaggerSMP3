package com.example.daggers;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Handles the Health Dagger's non-combat effects:
 *  - Passive: +5 hearts (10 HP) as long as a Health Dagger is anywhere in the
 *    player's inventory (any storage slot or the offhand) - not just while
 *    actively holding it in the main hand.
 *  - Tier 1 ability (shift + right-click): permanently unlocks +5 MORE hearts
 *    (15 -> 20 hearts total) for that player, as long as they still carry a
 *    Health Dagger somewhere. Unlike before, this no longer expires after a
 *    timer - once unlocked, it stays for as long as the dagger does.
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

    private final JavaPlugin plugin;
    // players who have ever triggered the tier 1 ability - the +5 extra hearts
    // stays unlocked for them permanently, as long as they still carry the dagger
    private final Set<UUID> boostUnlocked = new HashSet<>();

    public HealthDaggerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            boolean hasDagger = hasHealthDaggerAnywhere(player);
            syncPassive(player, hasDagger);
            syncBoost(player, hasDagger && boostUnlocked.contains(player.getUniqueId()));
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

    private void syncBoost(Player player, boolean shouldHaveBoost) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        boolean hasModifier = attr.getModifiers().stream()
                .anyMatch(m -> m.getUniqueId().equals(BOOST_MODIFIER_ID));

        if (shouldHaveBoost && !hasModifier) {
            attr.addModifier(new AttributeModifier(BOOST_MODIFIER_ID, "health-dagger-boost",
                    BOOST_BONUS_HP, AttributeModifier.Operation.ADD_NUMBER));
            player.setHealth(Math.min(attr.getValue(), player.getHealth() + BOOST_BONUS_HP));
        } else if (!shouldHaveBoost && hasModifier) {
            attr.getModifiers().stream()
                    .filter(m -> m.getUniqueId().equals(BOOST_MODIFIER_ID))
                    .findFirst()
                    .ifPresent(attr::removeModifier);
            if (player.getHealth() > attr.getValue()) {
                player.setHealth(attr.getValue());
            }
        }
    }

    /** Called from DaggerAbilityListener for the shift+right-click ability. */
    public void activateBoost(Player player) {
        boostUnlocked.add(player.getUniqueId());
        // sync immediately rather than waiting for the next tick, so it feels instant
        syncBoost(player, hasHealthDaggerAnywhere(player));
    }
}
