package com.example.daggers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecipeManager {

    private final JavaPlugin plugin;

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Hardcoded fallback recipes, used the first time config.yml has no entry yet for a dagger. */
    private Map<Material, Integer> defaultIngredients(DaggerType type) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        switch (type) {
            case FIRE -> {
                map.put(Material.FIRE_CHARGE, 2);
                map.put(Material.NETHERITE_BLOCK, 1);
                map.put(Material.MAGMA_CREAM, 4);
                map.put(Material.BLAZE_POWDER, 2);
            }
            case WATER -> {
                map.put(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
                map.put(Material.PRISMARINE_SHARD, 2);
                map.put(Material.NAUTILUS_SHELL, 4);
                map.put(Material.TRIDENT, 1);
            }
            case ICE -> {
                map.put(Material.BLUE_ICE, 2);
                map.put(Material.SNOW_BLOCK, 4);
                map.put(Material.POWDER_SNOW_BUCKET, 2);
                map.put(Material.NETHERITE_BLOCK, 1);
            }
            case SOUL -> {
                map.put(Material.WITHER_ROSE, 4);
                map.put(Material.NETHER_STAR, 1);
                map.put(Material.WITHER_SKELETON_SKULL, 2);
                map.put(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2);
            }
            case DARKNESS -> {
                map.put(Material.SCULK_SHRIEKER, 4);
                map.put(Material.HEAVY_CORE, 1);
                map.put(Material.NETHERITE_INGOT, 4);
            }
            case BACKSTAB -> {
                map.put(Material.DRAGON_EGG, 1);
                map.put(Material.DRAGON_HEAD, 2);
                map.put(Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
                map.put(Material.DRAGON_BREATH, 4);
            }
        }
        return map;
    }

    public void registerRecipes() {
        plugin.saveDefaultConfig();
        for (DaggerType type : DaggerType.values()) {
            registerRecipe(type, getIngredients(type));
        }
    }

    public Map<Material, Integer> getIngredients(DaggerType type) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("recipes." + type.name() + ".ingredients");

        if (section == null) {
            return defaultIngredients(type);
        }

        Map<Material, Integer> result = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                int amount = section.getInt(key);
                if (amount > 0) result.put(material, amount);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material '" + key + "' in recipe config for " + type.name());
            }
        }

        return result.isEmpty() ? defaultIngredients(type) : result;
    }

    public void updateRecipe(DaggerType type, Map<Material, Integer> ingredients) {
        FileConfiguration config = plugin.getConfig();
        String base = "recipes." + type.name() + ".ingredients";
        config.set(base, null);
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            config.set(base + "." + entry.getKey().name(), entry.getValue());
        }
        plugin.saveConfig();

        registerRecipe(type, ingredients);
    }

    private void registerRecipe(DaggerType type, Map<Material, Integer> ingredients) {
        if (ingredients.isEmpty()) return;

        int total = ingredients.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 9) {
            plugin.getLogger().warning("Recipe for " + type.name() + " has " + total + " total ingredients - a crafting grid only holds 9. Skipping registration.");
            return;
        }

        ItemStack result = DaggerItem.create(type);
        NamespacedKey key = new NamespacedKey(plugin, "dagger_" + type.name().toLowerCase());

        plugin.getServer().removeRecipe(key);

        ShapelessRecipe recipe = new ShapelessRecipe(key, result);
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            recipe.addIngredient(entry.getValue(), entry.getKey());
        }

        try {
            plugin.getServer().addRecipe(recipe);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Failed to register recipe for " + type.name() + ": " + e.getMessage());
        }
    }
}
