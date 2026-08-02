package com.example.daggers.listeners;

import com.example.daggers.DaggerType;
import com.example.daggers.RecipeGuiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
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
        if (!(holder instanceof RecipeGuiManager.MainMenuHolder) && !(holder instanceof RecipeGuiManager.EditorHolder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (holder instanceof RecipeGuiManager.MainMenuHolder) {
            event.setCancelled(true);
            int slot = event.getRawSlot();
            DaggerType[] types = DaggerType.values();
            if (slot >= 0 && slot < types.length) {
                guiManager.openEditor(player, types[slot]);
            }
            return;
        }

        RecipeGuiManager.EditorHolder editorHolder = (RecipeGuiManager.EditorHolder) holder;
        int rawSlot = event.getRawSlot();
        Inventory topInventory = event.getView().getTopInventory();

        if (rawSlot == RecipeGuiManager.SAVE_SLOT) {
            event.setCancelled(true);
            guiManager.handleSave(player, topInventory, editorHolder.type);
            guiManager.openEditor(player, editorHolder.type);
            return;
        }
        if (rawSlot == RecipeGuiManager.BACK_SLOT) {
            event.setCancelled(true);
            guiManager.openMainMenu(player);
            return;
        }
        if (rawSlot == RecipeGuiManager.BLADE_SLOT || rawSlot == RecipeGuiManager.HANDLE_SLOT) {
            return;
        }
        if (rawSlot >= 0 && rawSlot < topInventory.getSize()) {
            event.setCancelled(true);
        }
    }
}
