package com.example.daggers;

import com.example.daggers.listeners.DaggerAbilityListener;
import com.example.daggers.listeners.DaggerCraftListener;
import com.example.daggers.listeners.DaggerDamageListener;
import com.example.daggers.listeners.FreezeMovementListener;
import com.example.daggers.listeners.NoSprintListener;
import com.example.daggers.listeners.RecipeGuiListener;
import com.example.daggers.listeners.UpgraderListener;
import com.example.daggers.listeners.WindAbilityListener;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class DaggersPlugin extends JavaPlugin {

    private CooldownManager cooldownManager;
    private HitTracker hitTracker;
    private final Set<UUID> pendingSoulCurse = new HashSet<>();
    private final Set<UUID> pendingDarknessCurse = new HashSet<>();
    private FreezeManager freezeManager;
    private NoSprintManager noSprintManager;
    private DarknessInvisManager darknessInvisManager;
    private Tier2BuffManager tier2BuffManager;
    private GroundEffectManager groundEffectManager;
    private RecipeManager recipeManager;
    private RecipeGuiManager recipeGuiManager;
    private WindAbilityListener windAbilityListener;
    private HealthDaggerManager healthDaggerManager;

    @Override
    public void onEnable() {
        cooldownManager = new CooldownManager();
        hitTracker = new HitTracker();
        DaggerKeys.init(this);

        freezeManager = new FreezeManager(this);
        noSprintManager = new NoSprintManager(this);
        darknessInvisManager = new DarknessInvisManager(this);
        tier2BuffManager = new Tier2BuffManager(this);
        groundEffectManager = new GroundEffectManager(this);
        windAbilityListener = new WindAbilityListener(this);
        healthDaggerManager = new HealthDaggerManager(this);

        recipeManager = new RecipeManager(this);
        recipeManager.registerRecipes();

        recipeGuiManager = new RecipeGuiManager(this, recipeManager);

        getServer().getPluginManager().registerEvents(new RecipeGuiListener(recipeGuiManager), this);

        DaggerAbilityListener abilityListener = new DaggerAbilityListener(
                this, cooldownManager, freezeManager, pendingSoulCurse, pendingDarknessCurse,
                darknessInvisManager, tier2BuffManager, groundEffectManager,
                windAbilityListener, healthDaggerManager);

        DaggerDamageListener damageListener = new DaggerDamageListener(
                this, pendingSoulCurse, pendingDarknessCurse, hitTracker, noSprintManager,
                tier2BuffManager, abilityListener);

        getServer().getPluginManager().registerEvents(abilityListener, this);
        getServer().getPluginManager().registerEvents(damageListener, this);
        getServer().getPluginManager().registerEvents(new FreezeMovementListener(freezeManager), this);
        getServer().getPluginManager().registerEvents(new NoSprintListener(noSprintManager), this);
        getServer().getPluginManager().registerEvents(new UpgraderListener(), this);
        getServer().getPluginManager().registerEvents(windAbilityListener, this);
        getServer().getPluginManager().registerEvents(new DaggerCraftListener(this), this);

        registerUpgraderRecipe();

        getLogger().info("CustomDaggers enabled!");
    }

    private void registerUpgraderRecipe() {
        NamespacedKey key = new NamespacedKey(this, "dagger_upgrader");
        getServer().removeRecipe(key);

        ShapedRecipe recipe = new ShapedRecipe(key, UpgraderItem.create());
        recipe.shape("DND", "NSN", "DND");
        recipe.setIngredient('D', Material.DIAMOND_BLOCK);
        recipe.setIngredient('N', Material.NETHERITE_INGOT);
        recipe.setIngredient('S', Material.NETHER_STAR);

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

        if (!command.getName().equalsIgnoreCase("dagger")) {
            return false;
        }

        if (!sender.hasPermission("customdaggers.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command.");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can run this command.");
            return true;
        }

        if (args.length == 0) {
            recipeGuiManager.openDaggerGiveMenu(player);
            return true;
        }

        if (args.length != 1) {
            player.sendMessage("§cUsage: /dagger [fire|ice|water|soul|darkness|backstab|wind|zeus|health]");
            return true;
        }

        DaggerType type;
        try {
            type = DaggerType.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cUnknown dagger. Choose: fire, ice, water, soul, darkness, backstab, wind, zeus, health");
            return true;
        }

        ItemStack item = DaggerItem.create(type);
        player.getInventory().addItem(item);
        player.sendMessage("You received the " + type.getColor() + type.getDisplayName() + "!");
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
