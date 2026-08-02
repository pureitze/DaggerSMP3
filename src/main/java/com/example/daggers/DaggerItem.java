package com.example.daggers;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class DaggerItem {

    public static ItemStack create(DaggerType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(type.getColor() + type.getDisplayName());
        meta.setLore(getLore(type, 1));

        // Cosmetic glow without a fake enchantment, so real enchant books
        // (Sharpness, Looting, Fire Aspect, etc.) added via anvil show up normally
        meta.setEnchantmentGlintOverride(true);

        // Custom model data lets the resource pack swap this item's texture
        // without changing its underlying material (still a netherite sword
        // under the hood, just re-skinned client-side). Must match the
        // predicate values in the resource pack's netherite_sword.json.
        meta.setCustomModelData(type.getCustomModelData());

        meta.getPersistentDataContainer().set(DaggerKeys.TYPE_KEY, PersistentDataType.STRING, type.name());
        meta.getPersistentDataContainer().set(DaggerKeys.TIER_KEY, PersistentDataType.INTEGER, 1);

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

    /**
     * Returns this dagger's tier (1 or 2). Defaults to 1 if the tag is
     * missing (e.g. an old dagger crafted before tiers existed).
     */
    public static int getTier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1;

        Integer tier = item.getItemMeta()
                .getPersistentDataContainer()
                .get(DaggerKeys.TIER_KEY, PersistentDataType.INTEGER);

        return tier != null ? tier : 1;
    }

    /**
     * Mutates the given dagger ItemStack in place, upgrading it to Tier II.
     * This preserves everything else about the item (enchants, durability,
     * repair cost, etc.) since we're editing its existing meta rather than
     * replacing it with a freshly created item.
     */
    public static void upgradeToTier2(ItemStack item) {
        DaggerType type = getType(item);
        if (type == null) return;

        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(type.getColor() + type.getDisplayName() + " §7[Tier II]");
        meta.setLore(getLore(type, 2));
        meta.getPersistentDataContainer().set(DaggerKeys.TIER_KEY, PersistentDataType.INTEGER, 2);
        item.setItemMeta(meta);
    }

    private static List<String> getLore(DaggerType type, int tier) {
        String accent = type.getColor();
        List<String> lore = new java.util.ArrayList<>();

        lore.add(accent + "§m                    ");
        lore.add("§f§lDamage: §c8.0 §7(vanilla netherite sword)");
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
                    "§7§oBonus damage on sprint, sweep, and regular hits.",
                    "§7§oMassive bonus damage when striking from behind."
            );
        });
        lore.add("");
        lore.add(accent + "§lAbility " + "§8[Shift + Right-click]");
        lore.addAll(switch (type) {
            case FIRE -> List.of("§7Hurl a fireball §8(60s)");
            case ICE -> List.of("§7Freeze a target for 4s §8(60s)");
            case WATER -> List.of("§7Dolphin's Grace, 15s §8(60s)");
            case SOUL -> List.of("§7Curse your next target §8(60s)");
            case DARKNESS -> List.of("§7Invisible 8s + curse your next target §8(75s)");
            case BACKSTAB -> List.of("§7Grappling hook, up to 32 blocks §8(60s)");
        });

        if (tier >= 2) {
            lore.add("");
            lore.add("§d§lTier II Ability " + "§8[Shift + Swap-hands]");
            lore.addAll(switch (type) {
                case FIRE -> List.of(
                        "§78-block fire field, 16s.",
                        "§71.5x dmg (sprint/sweep/hit), 2.0x crit, 8s §8(90s)"
                );
                case ICE -> List.of(
                        "§78-block ice field, 16s.",
                        "§71.5x dmg (sprint/sweep/hit), 2.0x crit, 8s §8(90s)"
                );
                case WATER -> List.of(
                        "§7Dolphin's Grace, 30s.",
                        "§71.8x dmg (sprint/sweep/hit), 8s §8(90s)"
                );
                case SOUL -> List.of(
                        "§7Next hit: 3 hearts + Wither 5s §8(90s)"
                );
                case DARKNESS -> List.of(
                        "§7Invisible 8s.",
                        "§71.5x dmg (sprint/sweep/hit), 2.0x crit, 8s §8(90s)"
                );
                case BACKSTAB -> List.of(
                        "§7Resistance II + Speed III, 8s §8(90s)"
                );
            });
        }

        lore.add(accent + "§m                    ");
        return lore;
    }
}
