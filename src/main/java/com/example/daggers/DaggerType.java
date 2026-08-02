package com.example.daggers;

import org.bukkit.Material;

public enum DaggerType {
    FIRE("Fire Dagger", Material.NETHERITE_SWORD, "§c", 1015),
    ICE("Ice Dagger", Material.NETHERITE_SWORD, "§b", 1039),
    WATER("Water Dagger", Material.NETHERITE_SWORD, "§9", 1063),
    SOUL("Soul Dagger", Material.NETHERITE_SWORD, "§5", 1087),
    DARKNESS("Darkness Dagger", Material.NETHERITE_SWORD, "§8", 1111),
    BACKSTAB("Backstab Dagger", Material.NETHERITE_SWORD, "§4", 1135),
    WIND("Wind Dagger", Material.NETHERITE_SWORD, "§f", 1159),
    ZEUS("Zeus Dagger", Material.NETHERITE_SWORD, "§e", 1183),
    HEALTH("Health Dagger", Material.NETHERITE_SWORD, "§a", 1207);

    private final String displayName;
    private final Material material;
    private final String color;
    private final int customModelData;

    DaggerType(String displayName, Material material, String color, int customModelData) {
        this.displayName = displayName;
        this.material = material;
        this.color = color;
        this.customModelData = customModelData;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getMaterial() {
        return material;
    }

    public String getColor() {
        return color;
    }

    public int getCustomModelData() {
        return customModelData;
    }
}
