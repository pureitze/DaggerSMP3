package com.example.daggers;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class UpgraderItem {

    public static final int CUSTOM_MODEL_DATA = 2001;

    public static ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(CUSTOM_MODEL_DATA);
        meta.setDisplayName("§d§lDagger Upgrader");
        meta.setLore(List.of(
                "§7Combine with a Tier I dagger to",
                "§7permanently upgrade it to Tier II.",
                "",
                "§7Usage: hold this in your main hand,",
                "§7the dagger in your off-hand,",
                "§7then Shift + Right-click."
        ));
        meta.getPersistentDataContainer().set(DaggerKeys.UPGRADER_KEY, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isUpgrader(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte value = item.getItemMeta().getPersistentDataContainer().get(DaggerKeys.UPGRADER_KEY, PersistentDataType.BYTE);
        return value != null && value == 1;
    }
}
