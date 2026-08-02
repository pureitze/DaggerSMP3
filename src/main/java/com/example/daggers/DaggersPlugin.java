package com.example.daggers;

import com.example.daggers.listeners.DaggerAbilityListener;
import com.example.daggers.listeners.DaggerDamageListener;
import com.example.daggers.listeners.FreezeMovementListener;
import com.example.daggers.listeners.RecipeGuiListener;
import com.example.daggers.listeners.UpgraderListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ShapedRecipe;
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
    private NoSprintManager noSprintManager;
    private DarknessInvisManager darknessInvisManager;
    private RecipeManager recipeManager;
    private RecipeGuiManager recipeGuiManager;
    private Tier2BuffManager tier2BuffManager;
    private GroundEffectManager groundEffectManager;

    @Override
    public void onEnable() {
        DaggerKeys.init(this);
        freezeManager = new FreezeManager(this);
        noSprintManager = new NoSprintManager(this);
        darknessInvisManager = new DarknessInvisManager(this);
        tier2BuffManager = new Tier2BuffManager(this);
        groundEffectManager = new GroundEffectManager(this);

        recipeManager = new RecipeManager(this);
        recipeManager.registerRecipes();
        recipeGuiManager = new RecipeGuiManager(this, recipeManager);
        getServer().getPluginManager().registerEvents(new RecipeGuiListener(recipeGuiManager), this);

        registerUpgraderRecipe();

        DaggerAbilityListener abilityListener = new DaggerAbilityListener(
                this, cooldownManager, freezeManager, pendingSoulCurse, pendingDarknessCurse,
                darknessInvisManager, tier2BuffManager, groundEffectManager
        );
        getServer().getPluginManager().registerEvents(abilityListener, this);

        getServer().getPluginManager().registerEvents(
                new DaggerDamageListener(pendingSoulCurse, pendingDarknessCurse, hitTracker, noSprintManager,
                        tier2BuffManager, abilityListener), this);
        getServer().getPluginManager().registerEvents(new FreezeMovementListener(freezeManager), this);
        getServer().getPluginManager().registerEvents(new com.example.daggers.listeners.NoSprintListener(noSprintManager), this);
        getServer().getPluginManager().registerEvents(new UpgraderListener(), this);

        getLogger().info("CustomDaggers enabled!");
    }

    private void registerUpgraderRecipe() {
        NamespacedKey key = new NamespacedKey(this, "dagger_upgrader");
        getServer().removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, UpgraderItem.create());
        recipe.shape("DND", "NBN", "DND");
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('B', Material.BEACON);

        getServer().addRecipe(recipe);
    }

    @Override
    public void onDisable() {
        getLogger().info("CustomDaggers disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("drecipe")) {
            return handleDrecipeCommand(sender, args);
        }

        if (!command.getName().equalsIgnoreCase("dagger")) return false;

        if (!sender.hasPermission("customdaggers.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

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

    private boolean handleDrecipeCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("customdaggers.admin")) {
                player.sendMessage("§cYou don't have permission to use this command.");
                return true;
            }
            recipeGuiManager.openAdminMainMenu(player);
            return true;
        }

        recipeGuiManager.openViewMainMenu(player);
        return true;
    }
}
