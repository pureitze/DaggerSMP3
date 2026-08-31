package com.example.daggers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Stores each player's personal choice of what physical action triggers
 * their Tier 1 and Tier 2 abilities, set via /dagger set_ability. Applies
 * globally across all 9 dagger types for that player - not per type.
 * Persists across restarts in config.yml.
 */
public class AbilityTriggerManager {

    public static final AbilityTrigger DEFAULT_TIER1 = AbilityTrigger.SHIFT_RIGHT_CLICK;
    public static final AbilityTrigger DEFAULT_TIER2 = AbilityTrigger.SHIFT_SWAP_HANDS;

    private static final String CONFIG_PATH = "abilityTriggers";

    private final JavaPlugin plugin;
    private final Map<UUID, AbilityTrigger> tier1Triggers = new HashMap<>();
    private final Map<UUID, AbilityTrigger> tier2Triggers = new HashMap<>();

    public AbilityTriggerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(CONFIG_PATH);
        if (section == null) return;

        for (String uuidStr : section.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                continue;
            }

            String t1 = section.getString(uuidStr + ".tier1");
            if (t1 != null) {
                try {
                    tier1Triggers.put(uuid, AbilityTrigger.valueOf(t1));
                } catch (IllegalArgumentException ignored) {
                }
            }

            String t2 = section.getString(uuidStr + ".tier2");
            if (t2 != null) {
                try {
                    tier2Triggers.put(uuid, AbilityTrigger.valueOf(t2));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public AbilityTrigger getTier1Trigger(Player player) {
        return tier1Triggers.getOrDefault(player.getUniqueId(), DEFAULT_TIER1);
    }

    public AbilityTrigger getTier2Trigger(Player player) {
        return tier2Triggers.getOrDefault(player.getUniqueId(), DEFAULT_TIER2);
    }

    /** Returns false (and does not change anything) if this trigger is already used by the player's other tier. */
    public boolean setTier1Trigger(Player player, AbilityTrigger trigger) {
        if (trigger == getTier2Trigger(player)) return false;
        tier1Triggers.put(player.getUniqueId(), trigger);
        save(player.getUniqueId());
        return true;
    }

    public boolean setTier2Trigger(Player player, AbilityTrigger trigger) {
        if (trigger == getTier1Trigger(player)) return false;
        tier2Triggers.put(player.getUniqueId(), trigger);
        save(player.getUniqueId());
        return true;
    }

    private void save(UUID uuid) {
        String path = CONFIG_PATH + "." + uuid;
        AbilityTrigger t1 = tier1Triggers.get(uuid);
        AbilityTrigger t2 = tier2Triggers.get(uuid);
        if (t1 != null) plugin.getConfig().set(path + ".tier1", t1.name());
        if (t2 != null) plugin.getConfig().set(path + ".tier2", t2.name());
        plugin.saveConfig();
    }
}
