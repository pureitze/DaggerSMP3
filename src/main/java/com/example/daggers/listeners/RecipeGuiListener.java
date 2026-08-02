package com.example.daggers.listeners;

import com.example.daggers.DaggerType;
import com.example.daggers.RecipeGuiManager;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

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

        if (holder instanceof RecipeGuiManager.ViewRecipeHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                guiManager.openViewMainMenu(player);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.UpgraderRecipeHolder) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
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

            if (rawSlot == 22) {
                event.setCancelled(true);
                guiManager.handleSave(player, topInventory, editorHolder.type);
                guiManager.openAdminEditor(player, editorHolder.type);
                return;
            }

            if (rawSlot == 18) {
                event.setCancelled(true);
                guiManager.openAdminMainMenu(player);
                return;
            }

            for (int slot : RecipeGuiManager.INGREDIENT_SLOTS) {
                if (rawSlot == slot) {
                    // Ingredient slots are editable — let the click through.
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
                || holder instanceof RecipeGuiManager.ViewRecipeHolder
                || holder instanceof RecipeGuiManager.UpgraderRecipeHolder) {
            event.setCancelled(true);
        }
    }
}
