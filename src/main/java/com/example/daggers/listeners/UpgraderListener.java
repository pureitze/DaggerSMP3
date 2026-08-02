package com.example.daggers.listeners;

import com.example.daggers.DaggerItem;
import com.example.daggers.UpgraderItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class UpgraderListener implements Listener {

    @EventHandler
    public void onUpgrade(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (!UpgraderItem.isUpgrader(mainHand)) return;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (DaggerItem.getType(offHand) == null) {
            player.sendMessage("§cPut the Tier I dagger you want to upgrade in your off-hand.");
            return;
        }
        if (DaggerItem.getTier(offHand) != 1) {
            player.sendMessage("§cThat dagger isn't Tier I.");
            return;
        }

        event.setCancelled(true);
        DaggerItem.upgradeToTier2(offHand);

        mainHand.setAmount(mainHand.getAmount() - 1);
        player.getInventory().setItemInMainHand(mainHand);

        player.sendMessage("§d§lYour dagger has been upgraded to Tier II!");
    }
}
