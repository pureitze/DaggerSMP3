package com.example.daggers.listeners;

import com.example.daggers.DaggerType;
import com.example.daggers.RecipeGuiManager;
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
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (holder instanceof RecipeGuiManager.ViewMainMenuHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            DaggerType[] types = DaggerType.values();
            if (slot >= 0 && slot < types.length) {
                guiManager.openViewRecipe(player, types[slot]);
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

        if (holder instanceof RecipeGuiManager.AdminMainMenuHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            DaggerType[] types = DaggerType.values();
            if (slot >= 0 && slot < types.length) {
                guiManager.openAdminEditor(player, types[slot]);
            }
            return;
        }

        if (holder instanceof RecipeGuiManager.AdminEditorHolder editorHolder) {
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
            for (int ingredientSlot : RecipeGuiManager.INGREDIENT_SLOTS) {
                if (rawSlot == ingredientSlot) return; // allow free placement/removal
            }
            if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof RecipeGuiManager.ViewMainMenuHolder || holder instanceof RecipeGuiManager.ViewRecipeHolder) {
            event.setCancelled(true);
        }
    }
}
