package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.RecipeGuiManager;
import com.example.daggers.UpgraderItem;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public class RecipeGuiListener implements Listener {

    private final RecipeGuiManager guiManager;

    public RecipeGuiListener(RecipeGuiManager guiManager) {
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        HumanEntity human = event.getWhoClicked();
        if (!(human instanceof Player)) return;
        Player player = (Player) human;

        if (holder instanceof RecipeGuiManager.ViewMainMenuHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();

            if (rawSlot == 9) {
                guiManager.openUpgraderRecipe(player);
                return;
            }

            DaggerType[] types = DaggerType.values();
            if (rawSlot >= 0 && rawSlot < types.length) {
                guiManager.openViewRecipe(player, types[rawSlot]);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.DaggerGiveMenuHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();

            ItemStack toGive;
            if (rawSlot == 9) {
                toGive = UpgraderItem.create();
            } else {
                DaggerType[] types = DaggerType.values();
                if (rawSlot < 0 || rawSlot >= types.length) return;
                toGive = DaggerItem.create(types[rawSlot]);
            }

            var leftover = player.getInventory().addItem(toGive);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), toGive);
                player.sendMessage("§eYour inventory was full, so it was dropped at your feet.");
            } else {
                player.sendMessage("§aGiven!");
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.CraftLimitMenuHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            DaggerType[] types = DaggerType.values();
            if (rawSlot >= 0 && rawSlot < types.length) {
                guiManager.openCraftLimitInput(player, types[rawSlot]);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.CraftLimitInputHolder) {
            RecipeGuiManager.CraftLimitInputHolder inputHolder = (RecipeGuiManager.CraftLimitInputHolder) holder;
            int rawSlot = event.getRawSlot();

            // Anvil layout: 0 = input slot, 1 = (unused second slot), 2 = result slot
            if (rawSlot == 2) {
                event.setCancelled(true);
                AnvilInventory anvil = (AnvilInventory) event.getInventory();
                String typed = anvil.getRenameText();
                if (typed == null || typed.isBlank()) {
                    ItemStack current = anvil.getItem(0);
                    typed = (current != null && current.hasItemMeta() && current.getItemMeta().hasDisplayName())
                            ? current.getItemMeta().getDisplayName()
                            : "";
                }

                if (typed.equalsIgnoreCase("unlimited")) {
                    guiManager.setCraftLimit(inputHolder.type, null);
                    player.sendMessage("§a" + inputHolder.type.getDisplayName() + " is now unlimited.");
                } else {
                    try {
                        int value = Integer.parseInt(typed.trim());
                        if (value < 0) throw new NumberFormatException();
                        guiManager.setCraftLimit(inputHolder.type, value);
                        player.sendMessage("§a" + inputHolder.type.getDisplayName() + " limit set to " + value + ".");
                    } catch (NumberFormatException e) {
                        player.sendMessage("§cType a whole number (or \"unlimited\") before clicking the result.");
                        return;
                    }
                }

                player.closeInventory();
                guiManager.openCraftLimitMenu(player);
                return;
            }

            // Let normal anvil renaming happen in the input slot itself.
            if (rawSlot != 0) {
                event.setCancelled(true);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.ViewRecipeHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == RecipeGuiManager.BACK_SLOT_VIEW) {
                guiManager.openViewMainMenu(player);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.UpgraderRecipeHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == RecipeGuiManager.BACK_SLOT_VIEW) {
                guiManager.openViewMainMenu(player);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.AdminMainMenuHolder) {
            event.setCancelled(true);
            int rawSlot = event.getRawSlot();
            DaggerType[] types = DaggerType.values();
            if (rawSlot >= 0 && rawSlot < types.length) {
                guiManager.openAdminEditor(player, types[rawSlot]);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.AdminEditorHolder) {
            RecipeGuiManager.AdminEditorHolder editorHolder = (RecipeGuiManager.AdminEditorHolder) holder;
            int rawSlot = event.getRawSlot();
            Inventory topInventory = event.getView().getTopInventory();

            if (rawSlot == RecipeGuiManager.SAVE_SLOT) {
                event.setCancelled(true);
                guiManager.handleSave(player, topInventory, editorHolder.type);
                guiManager.openAdminEditor(player, editorHolder.type);
                return;
            }

            if (rawSlot == RecipeGuiManager.BACK_SLOT_ADMIN) {
                event.setCancelled(true);
                guiManager.openAdminMainMenu(player);
                return;
            }

            for (int slot : RecipeGuiManager.INGREDIENT_SLOTS) {
                if (rawSlot == slot) {
                    // Ingredient slots are editable - let the click through.
                    return;
                }
            }

            if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof RecipeGuiManager.ViewMainMenuHolder
                || holder instanceof RecipeGuiManager.DaggerGiveMenuHolder
                || holder instanceof RecipeGuiManager.CraftLimitMenuHolder
                || holder instanceof RecipeGuiManager.ViewRecipeHolder
                || holder instanceof RecipeGuiManager.UpgraderRecipeHolder) {
            event.setCancelled(true);
        }
    }
}
