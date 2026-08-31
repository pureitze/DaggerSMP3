package com.example.daggers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RecipeGuiManager {

    public static final int RESULT_SLOT = 13;
    public static final int[] INGREDIENT_SLOTS;
    public static final int SAVE_SLOT = 16;
    public static final int BACK_SLOT_ADMIN = 25;
    public static final int BACK_SLOT_VIEW = 22;

    static {
        // A real contiguous 3x3 block (left side of the 27-slot inventory),
        // matching an actual crafting table's grid instead of scattered slots.
        INGREDIENT_SLOTS = new int[]{0, 1, 2, 9, 10, 11, 18, 19, 20};
    }

    private final JavaPlugin plugin;
    private final RecipeManager recipeManager;
    private final CraftLimitManager craftLimitManager;

    public RecipeGuiManager(JavaPlugin plugin, RecipeManager recipeManager, CraftLimitManager craftLimitManager) {
        this.plugin = plugin;
        this.recipeManager = recipeManager;
        this.craftLimitManager = craftLimitManager;
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

    /** Admin convenience menu: click any dagger or the Upgrader to receive one in your inventory. */
    public void openDaggerGiveMenu(Player player) {
        DaggerGiveMenuHolder holder = new DaggerGiveMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 18, "§8Get a Dagger");
        holder.setInventory(inv);
        fillGlass(inv, 18);

        int slot = 0;
        for (DaggerType type : DaggerType.values()) {
            inv.setItem(slot, DaggerItem.create(type));
            slot++;
        }

        inv.setItem(9, UpgraderItem.create());

        player.openInventory(inv);
    }

    /** Admin menu: shows each dagger type's crafting cap (or "Unlimited") and how many have been made. */
    public void openCraftLimitMenu(Player player) {
        CraftLimitMenuHolder holder = new CraftLimitMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, "§8[Admin] Craft Limits");
        holder.setInventory(inv);

        int slot = 0;
        for (DaggerType type : DaggerType.values()) {
            ItemStack icon = DaggerItem.create(type);
            ItemMeta meta = icon.getItemMeta();
            Integer limit = craftLimitManager.getLimit(type);
            int crafted = craftLimitManager.getCraftedCount(type);

            List<String> lore = new ArrayList<>(meta.getLore() != null ? meta.getLore() : List.of());
            lore.add("");
            lore.add(limit == null
                    ? "§7Limit: §aUnlimited §7(" + crafted + " crafted)"
                    : "§7Limit: §e" + limit + " §7(" + crafted + " crafted)");
            lore.add("§7Click to change this limit");
            meta.setLore(lore);
            icon.setItemMeta(meta);

            inv.setItem(slot, icon);
            slot++;
        }

        player.openInventory(inv);
    }

    /** Opens a virtual anvil so the admin can type a number (or "unlimited") for this dagger's cap. */
    public void openCraftLimitInput(Player player, DaggerType type) {
        CraftLimitInputHolder holder = new CraftLimitInputHolder(type);
        Inventory inv = Bukkit.createInventory(holder, InventoryType.ANVIL, "§8Set limit: " + type.getDisplayName());
        holder.setInventory(inv);

        Integer currentLimit = craftLimitManager.getLimit(type);
        ItemStack input = new ItemStack(Material.PAPER);
        ItemMeta meta = input.getItemMeta();
        meta.setDisplayName(currentLimit == null ? "unlimited" : String.valueOf(currentLimit));
        input.setItemMeta(meta);

        inv.setItem(0, input);
        player.openInventory(inv);
    }

    public void setCraftLimit(DaggerType type, Integer limit) {
        craftLimitManager.setLimit(type, limit);
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

        // Mark every grid slot as an empty, editable spot (red) before placing
        // real ingredients over the top - whatever's left red is still empty.
        ItemStack emptySlotMarker = namedItem(Material.RED_STAINED_GLASS_PANE, "§cEmpty - place an ingredient here");
        for (int slot : INGREDIENT_SLOTS) {
            inv.setItem(slot, emptySlotMarker);
        }

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
            if (isEmptySlotMarker(item)) continue;
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

    /** True if this is our own red-glass "empty" placeholder, not a real ingredient a player placed there. */
    private boolean isEmptySlotMarker(ItemStack item) {
        if (item.getType() != Material.RED_STAINED_GLASS_PANE) return false;
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.hasDisplayName()
                && meta.getDisplayName().equals("§cEmpty - place an ingredient here");
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

    public static class DaggerGiveMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class CraftLimitMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class CraftLimitInputHolder implements InventoryHolder {
        public final DaggerType type;
        private Inventory inventory;

        public CraftLimitInputHolder(DaggerType type) {
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
