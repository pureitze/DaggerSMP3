package com.example.daggers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class RecipeManager {

    private final JavaPlugin plugin;

    public RecipeManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerRecipes() {
        plugin.saveDefaultConfig();
        for (DaggerType type : DaggerType.values()) {
            registerRecipe(type, getBladeMaterial(type), getHandleMaterial(type));
        }
    }

    public Material getBladeMaterial(DaggerType type) {
        return parseMaterial(configPath(type) + ".blade", Material.NETHERITE_INGOT);
    }

    public Material getHandleMaterial(DaggerType type) {
        return parseMaterial(configPath(type) + ".handle", Material.STICK);
    }

    public void updateRecipe(DaggerType type, Material blade, Material handle) {
        FileConfiguration config = plugin.getConfig();
        config.set(configPath(type) + ".blade", blade.name());
        config.set(configPath(type) + ".handle", handle.name());
        plugin.saveConfig();

        registerRecipe(type, blade, handle);
    }

    private void registerRecipe(DaggerType type, Material blade, Material handle) {
        ItemStack result = DaggerItem.create(type);
        NamespacedKey key = new NamespacedKey(plugin, "dagger_" + type.name().toLowerCase());

        plugin.getServer().removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, result);
        recipe.shape("B", "B", "H");
        recipe.setIngredient('B', blade);
        recipe.setIngredient('H', handle);

        plugin.getServer().addRecipe(recipe);
    }

    private String configPath(DaggerType type) {
        return "recipes." + type.name();
    }

    private Material parseMaterial(String path, Material fallback) {
        String name = plugin.getConfig().getString(path);
        if (name == null) return fallback;
        try {
            return Material.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material '" + name + "' in recipe config, defaulting to " + fallback);
            return fallback;
        }
    }
}
