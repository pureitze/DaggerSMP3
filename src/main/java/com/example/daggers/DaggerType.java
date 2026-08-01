package com.example.daggers;

import org.bukkit.Material;

/**
 * One entry per dagger. All daggers use NETHERITE_SWORD as their base item so
 * they all share netherite's attack speed automatically (vanilla attack speed
 * is identical across sword materials - only damage differs). Base damage is
 * then overridden to 8.5 in DaggerItem.create(). Visual distinction currently
 * comes from name/color/lore; a resource pack with custom model data could
 * later give each one a real dagger-shaped model.
 */
public enum DaggerType {
    FIRE("Fire Dagger", Material.NETHERITE_SWORD, "§c", 1001),
    ICE("Ice Dagger", Material.NETHERITE_SWORD, "§b", 1002),
    WATER("Water Dagger", Material.NETHERITE_SWORD, "§9", 1003),
    SOUL("Soul Dagger", Material.NETHERITE_SWORD, "§5", 1004),
    DARKNESS("Darkness Dagger", Material.NETHERITE_SWORD, "§8", 1005),
    BACKSTAB("Backstab Dagger", Material.NETHERITE_SWORD, "§4", 1006);

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
