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

        // Cosmetic glow, hidden tooltip enchant text
        meta.addEnchant(Enchantment.DAMAGE_ALL, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        // Bump attack damage from netherite's base 8.0 up to 8.5
        AttributeModifier damageModifier = new AttributeModifier(
                UUID.randomUUID(),
                "dagger_damage_bonus",
                DAMAGE_BONUS,
                AttributeModifier.Operation.ADD_NUMBER,
                EquipmentSlot.HAND
        );
        meta.addAttributeModifier(Attribute.GENERIC_ATTACK_DAMAGE, damageModifier);
        // Hide the vanilla "+0.5 Attack Damage" attribute line since the lore
        // already states "8.5 damage" - keeps the tooltip clean
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

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
        List<String> lore = new java.util.ArrayList<>();
        lore.add("§78.5 damage");
        lore.addAll(switch (type) {
            case FIRE -> List.of(
                    "§7Sets enemies ablaze on hit.",
                    "§7Bonus damage while YOU are on fire.",
                    "§7Right-click: hurl a fireball (60s)"
            );
            case ICE -> List.of(
                    "§7Bonus damage while standing on ice.",
                    "§7Right-click: freeze a target for 4s (60s)"
            );
            case WATER -> List.of(
                    "§7Bonus damage while in water.",
                    "§7Right-click: Dolphin's Grace 15s (60s)"
            );
            case SOUL -> List.of(
                    "§7Every 5th hit on a target deals bonus damage.",
                    "§7Every 10th hit steals health + heals you.",
                    "§7Right-click: curse your next target (60s)"
            );
            case DARKNESS -> List.of(
                    "§7Every 5th hit on a target deals bonus damage.",
                    "§7Every 10th hit damages the target's armor.",
                    "§7Right-click: curse your next target with Darkness (75s)"
            );
            case BACKSTAB -> List.of(
                    "§7Bonus damage on sprint, sweep, and crit hits.",
                    "§7Massive bonus damage when striking from behind.",
                    "§7Right-click: grappling hook, up to 32 blocks (60s)"
            );
        });
        return lore;
    }
}
