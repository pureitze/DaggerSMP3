package com.example.daggers.listeners;

import com.example.daggers.NoSprintManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSprintEvent;

public class NoSprintListener implements Listener {

    private final NoSprintManager noSprintManager;

    public NoSprintListener(NoSprintManager noSprintManager) {
        this.noSprintManager = noSprintManager;
    }

    @EventHandler
    public void onToggleSprint(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) return;
        if (noSprintManager.isNoSprint(event.getPlayer())) {
            event.setCancelled(true);
        }
    }
}
