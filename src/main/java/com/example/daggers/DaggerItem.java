package com.example.daggers;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

public class DaggerItem {

    // Netherite sword's vanilla base attack damage is 8.0; we add +0.5 on top
    // to land exactly on the requested 8.5. Attack speed is left untouched,
    // so it stays identical to a normal netherite sword.
    private static final double DAMAGE_BONUS = 0.5;

    public static ItemStack create(DaggerType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(type.getColor() + type.getDisplayName());
        meta.setLore(getLore(type));

        // Cosmetic glow without a fake enchantment, so real enchant books
  // (Sharpness, Looting, Fire Aspect, etc.) added via anvil show up normally
  meta.setEnchantmentGlintOverride(true);

        // Custom model data lets the resource pack swap this item's texture
        // without changing its underlying material (still a netherite sword
        // under the hood, just re-skinned client-side). Must match the
        // predicate values in the resource pack's netherite_sword.json.
        meta.setCustomModelData(type.getCustomModelData());

        // Hidden tag so we can identify this exact dagger type later
        meta.getPersistentDataContainer().set(
                DaggerKeys.TYPE_KEY,
                PersistentDataType.STRING,
                type.name()
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Returns the DaggerType of this item, or null if it isn't one of our daggers.
     */
    public static DaggerType getType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;

        String value = item.getItemMeta()
                .getPersistentDataContainer()
                .get(DaggerKeys.TYPE_KEY, PersistentDataType.STRING);

        if (value == null) return null;

        try {
            return DaggerType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static List<String> getLore(DaggerType type) {
        String accent = type.getColor();
        List<String> lore = new java.util.ArrayList<>();

        lore.add(accent + "§m                    ");
        lore.add("§f§lDamage: §c8.5");
        lore.add("");
        lore.add(accent + "§lPassive");
        lore.addAll(switch (type) {
            case FIRE -> List.of(
                    "§7§oSets enemies ablaze on hit.",
                    "§7§oBonus damage while YOU are on fire."
            );
            case ICE -> List.of(
                    "§7§oBonus damage while standing on ice."
            );
            case WATER -> List.of(
                    "§7§oBonus damage while in water."
            );
            case SOUL -> List.of(
                    "§7§oEvery 5th hit deals bonus damage.",
                    "§7§oEvery 10th hit steals health + heals you."
            );
            case DARKNESS -> List.of(
                    "§7§oEvery 5th hit deals bonus damage.",
                    "§7§oEvery 10th hit damages the target's armor."
            );
            case BACKSTAB -> List.of(
                    "§7§oBonus damage on sprint, sweep, and crit hits.",
                    "§7§oMassive bonus damage when striking from behind."
            );
        });
        lore.add("");
        lore.add(accent + "§lAbility " + "§8[Right-click]");
        lore.addAll(switch (type) {
            case FIRE -> List.of("§7Hurl a fireball §8(60s)");
            case ICE -> List.of("§7Freeze a target for 4s §8(60s)");
            case WATER -> List.of("§7Dolphin's Grace, 15s §8(60s)");
            case SOUL -> List.of("§7Curse your next target §8(60s)");
            case DARKNESS -> List.of("§7Curse your next target with Darkness §8(75s)");
            case BACKSTAB -> List.of("§7Grappling hook, up to 32 blocks §8(60s)");
        });
        lore.add(accent + "§m                    ");

        return lore;
    }
}

