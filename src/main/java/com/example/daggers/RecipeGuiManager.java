package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

public class RecipeGuiManager {

    public static final String MAIN_TITLE = "§8Dagger Recipes";
    public static final String EDITOR_TITLE_PREFIX = "§8Edit Recipe: ";

    public static final int BLADE_SLOT = 11;
    public static final int HANDLE_SLOT = 15;
    public static final int SAVE_SLOT = 22;
    public static final int BACK_SLOT = 18;

    private final JavaPlugin plugin;
    private final RecipeManager recipeManager;

    public RecipeGuiManager(JavaPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    public static class MainMenuHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public static class EditorHolder implements InventoryHolder {
        public final DaggerType type;
        private Inventory inventory;
        public EditorHolder(DaggerType type) { this.type = type; }
        @Override public Inventory getInventory() { return inventory; }
        public void setInventory(Inventory inventory) { this.inventory = inventory; }
    }

    public void openMainMenu(Player player) {
        MainMenuHolder holder = new MainMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, MAIN_TITLE);
        holder.setInventory(inv);

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) inv.setItem(i, filler);

        int slot = 0;
        for (DaggerType type : DaggerType.values()) {
            inv.setItem(slot, DaggerItem.create(type));
            slot++;
        }
        player.openInventory(inv);
    }

    public void openEditor(Player player, DaggerType type) {
        EditorHolder holder = new EditorHolder(type);
        Inventory inv = Bukkit.createInventory(holder, 27, EDITOR_TITLE_PREFIX + type.getDisplayName());
        holder.setInventory(inv);

        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        inv.setItem(BLADE_SLOT, new ItemStack(recipeManager.getBladeMaterial(type)));
        inv.setItem(12, namedItem(Material.PAPER, "§eBlade Material §7(place an item here)"));
        inv.setItem(HANDLE_SLOT, new ItemStack(recipeManager.getHandleMaterial(type)));
        inv.setItem(14, namedItem(Material.PAPER, "§eHandle Material §7(place an item here)"));

        inv.setItem(SAVE_SLOT, namedItem(Material.EMERALD, "§aSave Recipe"));
        inv.setItem(BACK_SLOT, namedItem(Material.ARROW, "§7« Back"));

        player.openInventory(inv);
    }

    public void handleSave(Player player, Inventory inv, DaggerType type) {
        ItemStack bladeItem = inv.getItem(BLADE_SLOT);
        ItemStack handleItem = inv.getItem(HANDLE_SLOT);

        if (bladeItem == null || bladeItem.getType() == Material.AIR
                || handleItem == null || handleItem.getType() == Material.AIR) {
            player.sendMessage("§cBoth the blade and handle slots need an item in them.");
            return;
        }

        recipeManager.updateRecipe(type, bladeItem.getType(), handleItem.getType());
        player.sendMessage("§a" + type.getDisplayName() + " recipe updated: "
                + bladeItem.getType() + " + " + handleItem.getType());
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }
}
