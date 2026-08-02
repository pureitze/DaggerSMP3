package com.example.daggers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class RecipeManager {

    private final JavaPlugin plugin;

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        plugin.saveDefaultConfig();
        for (DaggerType type : DaggerType.values()) {
            registerRecipe(type, getIngredients(type));
        }
    }

    public Map<Material, Integer> getIngredients(DaggerType type) {
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection section = config.getConfigurationSection("defaultIngredients." + type.name());
        if (section == null) {
            return defaultIngredients(type);
        }

        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            try {
                Material material = Material.valueOf(key.toUpperCase());
                int amount = section.getInt(key);
                if (amount > 0) {
                    ingredients.put(material, amount);
                }
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Unknown material '" + key + "' in recipe for " + type.name());
            }
        }

        return ingredients.isEmpty() ? defaultIngredients(type) : ingredients;
    }

    public void updateRecipe(DaggerType type, Map<Material, Integer> ingredients) {
        FileConfiguration config = plugin.getConfig();
        String path = "defaultIngredients." + type.name();
        config.set(path, null);

        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            config.set(path + "." + entry.getKey().name(), entry.getValue());
        }

        plugin.saveConfig();
        registerRecipe(type, ingredients);
    }

    public void registerRecipe(DaggerType type, Map<Material, Integer> ingredients) {
        if (ingredients.isEmpty()) return;

        int total = ingredients.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 9) {
            plugin.getLogger().warning(type.name() + " recipe has too many total ingredients (" + total + "), skipping registration.");
            return;
        }

        try {
            var result = DaggerItem.create(type);
            NamespacedKey key = new NamespacedKey(plugin, "dagger_" + type.name().toLowerCase());
            plugin.getServer().removeRecipe(key);

            ShapelessRecipe recipe = new ShapelessRecipe(key, result);
            for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
                recipe.addIngredient(entry.getValue(), entry.getKey());
            }
            plugin.getServer().addRecipe(recipe);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning(type.name() + " recipe registration failed: " + e.getMessage());
        }
    }

    private Map<Material, Integer> defaultIngredients(DaggerType type) {
        Map<Material, Integer> map = new LinkedHashMap<>();

        switch (type) {
            case FIRE -> {
                map.put(Material.FIRE_CHARGE, 2);
                map.put(Material.NETHERITE_BLOCK, 1);
                map.put(Material.MAGMA_CREAM, 4);
                map.put(Material.BLAZE_POWDER, 2);
            }
            case ICE -> {
                map.put(Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, 2);
                map.put(Material.PRISMARINE_SHARD, 2);
                map.put(Material.NAUTILUS_SHELL, 4);
                map.put(Material.TRIDENT, 1);
            }
            case WATER -> {
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
            case WIND -> {
                map.put(Material.FEATHER, 4);
                map.put(Material.PHANTOM_MEMBRANE, 2);
                map.put(Material.BREEZE_ROD, 1);
            }
            case ZEUS -> {
                map.put(Material.LIGHTNING_ROD, 1);
                map.put(Material.GOLD_BLOCK, 2);
                map.put(Material.DIAMOND_BLOCK, 1);
            }
            case HEALTH -> {
                map.put(Material.TOTEM_OF_UNDYING, 1);
                map.put(Material.GOLDEN_APPLE, 4);
                map.put(Material.GHAST_TEAR, 2);
            }
        }

        return map;
    }
}
