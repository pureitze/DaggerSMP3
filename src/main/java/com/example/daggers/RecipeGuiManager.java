package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeGuiManager {

    public static final int RESULT_SLOT = 4;
    public static final int[] INGREDIENT_SLOTS;
    public static final int SAVE_SLOT = 22;
    public static final int BACK_SLOT_ADMIN = 18;
    public static final int BACK_SLOT_VIEW = 22;

    static {
        INGREDIENT_SLOTS = new int[]{10, 12, 14, 16};
    }

    private final JavaPlugin plugin;
    private final RecipeManager recipeManager;

    public RecipeGuiManager(JavaPlugin plugin, RecipeManager recipeManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
    }

    public void openViewMainMenu(Player player) {
        ViewMainMenuHolder holder = new ViewMainMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 18, "§8Dagger Recipes");
        holder.setInventory(inv);
        fillGlass(inv, 18);

        int slot = 0;
        for (DaggerType type : DaggerType.values()) {
            inv.setItem(slot, DaggerItem.create(type));
            slot++;
        }

        // Upgrader sits at the start of row two, clear of all 9 dagger slots (0-8).
        inv.setItem(9, UpgraderItem.create());

        player.openInventory(inv);
    }

    public void openViewRecipe(Player player, DaggerType type) {
        ViewRecipeHolder holder = new ViewRecipeHolder(type);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Recipe: " + type.getDisplayName());
        holder.setInventory(inv);
        fillGlass(inv, 27);

        inv.setItem(RESULT_SLOT, DaggerItem.create(type));
        placeIngredientDisplay(inv, type);

        inv.setItem(BACK_SLOT_VIEW, namedItem(Material.ARROW, "§7« Back"));

        player.openInventory(inv);
    }

    public void openUpgraderRecipe(Player player) {
        UpgraderRecipeHolder holder = new UpgraderRecipeHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, "§8Recipe: Dagger Upgrader");
        holder.setInventory(inv);
        fillGlass(inv, 27);

        inv.setItem(RESULT_SLOT, UpgraderItem.create());

        Map<Material, Integer> ingredients = new LinkedHashMap<>();
        ingredients.put(Material.DIAMOND_BLOCK, 4);
        ingredients.put(Material.NETHERITE_INGOT, 4);
        ingredients.put(Material.NETHER_STAR, 1);

        int i = 0;
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            if (i >= INGREDIENT_SLOTS.length) break;

            ItemStack item = new ItemStack(entry.getKey(), Math.min(entry.getValue(), entry.getKey().getMaxStackSize()));
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of("§7Required: §f" + entry.getValue()));
            item.setItemMeta(meta);

            inv.setItem(INGREDIENT_SLOTS[i], item);
            i++;
        }

        inv.setItem(BACK_SLOT_VIEW, namedItem(Material.ARROW, "§7« Back"));

        player.openInventory(inv);
    }

    private void placeIngredientDisplay(Inventory inv, DaggerType type) {
        Map<Material, Integer> ingredients = recipeManager.getIngredients(type);

        int i = 0;
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            if (i >= INGREDIENT_SLOTS.length) break;

            ItemStack item = new ItemStack(entry.getKey(), Math.min(entry.getValue(), entry.getKey().getMaxStackSize()));
            ItemMeta meta = item.getItemMeta();
            meta.setLore(List.of("§7Required: §f" + entry.getValue()));
            item.setItemMeta(meta);

            inv.setItem(INGREDIENT_SLOTS[i], item);
            i++;
        }
    }

    public void openAdminMainMenu(Player player) {
        AdminMainMenuHolder holder = new AdminMainMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, "§8[Admin] Dagger Recipes");
        holder.setInventory(inv);
        fillGlass(inv, 9);

        int slot = 0;
        for (DaggerType type : DaggerType.values()) {
            inv.setItem(slot, DaggerItem.create(type));
            slot++;
        }

        player.openInventory(inv);
    }

    public void openAdminEditor(Player player, DaggerType type) {
        AdminEditorHolder holder = new AdminEditorHolder(type);
        Inventory inv = Bukkit.createInventory(holder, 27, "§8[Admin] Edit: " + type.getDisplayName());
        holder.setInventory(inv);
        fillGlass(inv, 27);

        inv.setItem(RESULT_SLOT, DaggerItem.create(type));

        Map<Material, Integer> ingredients = recipeManager.getIngredients(type);
        int i = 0;
        for (Map.Entry<Material, Integer> entry : ingredients.entrySet()) {
            if (i >= INGREDIENT_SLOTS.length) break;
            inv.setItem(INGREDIENT_SLOTS[i], new ItemStack(entry.getKey(), Math.min(entry.getValue(), entry.getKey().getMaxStackSize())));
            i++;
        }

        inv.setItem(SAVE_SLOT, namedItem(Material.EMERALD, "§aSave Recipe"));
        inv.setItem(BACK_SLOT_ADMIN, namedItem(Material.ARROW, "§7« Back"));

        player.openInventory(inv);
    }

    public void handleSave(Player player, Inventory inv, DaggerType type) {
        Map<Material, Integer> ingredients = new LinkedHashMap<>();

        for (int slot : INGREDIENT_SLOTS) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR) continue;
            ingredients.put(item.getType(), item.getAmount());
        }

        if (ingredients.isEmpty()) {
            player.sendMessage("§cAt least one ingredient slot needs an item in it.");
            return;
        }

        int total = ingredients.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 9) {
            player.sendMessage("§cTotal ingredient count can't exceed 9 (a full crafting grid). Currently: " + total);
            return;
        }

        recipeManager.updateRecipe(type, ingredients);
        player.sendMessage("§a" + type.getDisplayName() + " recipe updated!");
    }

    private void fillGlass(Inventory inv, int size) {
        ItemStack filler = namedItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < size; i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack namedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        item.setItemMeta(meta);
        return item;
    }

    public static class ViewMainMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class ViewRecipeHolder implements InventoryHolder {
        public final DaggerType type;
        private Inventory inventory;

        public ViewRecipeHolder(DaggerType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class UpgraderRecipeHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class AdminMainMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class AdminEditorHolder implements InventoryHolder {
        public final DaggerType type;
        private Inventory inventory;

        public AdminEditorHolder(DaggerType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
