package com.example.daggers;

import com.example.daggers.listeners.DaggerAbilityListener;
import com.example.daggers.listeners.DaggerDamageListener;
import com.example.daggers.listeners.FreezeMovementListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DaggersPlugin extends JavaPlugin {

    private final CooldownManager cooldownManager = new CooldownManager();
    private final HitTracker hitTracker = new HitTracker();
    private final Set<UUID> pendingSoulCurse = new HashSet<>();
    private final Set<UUID> pendingDarknessCurse = new HashSet<>();
    private FreezeManager freezeManager;

    @Override
    public void onEnable() {
        DaggerKeys.init(this);
        freezeManager = new FreezeManager(this);

        getServer().getPluginManager().registerEvents(
                new DaggerDamageListener(pendingSoulCurse, pendingDarknessCurse, hitTracker), this);
        getServer().getPluginManager().registerEvents(
                new DaggerAbilityListener(this, cooldownManager, freezeManager, pendingSoulCurse, pendingDarknessCurse), this);
        getServer().getPluginManager().registerEvents(
                new FreezeMovementListener(freezeManager), this);

        getLogger().info("CustomDaggers enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomDaggers disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("dagger")) return false;

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /dagger <fire|ice|water|soul|darkness|backstab>");
            return true;
        }

        DaggerType type;
        try {
            type = DaggerType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUnknown dagger. Choose: fire, ice, water, soul, darkness, backstab");
            return true;
        }

        player.getInventory().addItem(DaggerItem.create(type));
        player.sendMessage(type.getColor() + "You received the " + type.getDisplayName() + "!");
        return true;
    }
}
