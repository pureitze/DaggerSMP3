package com.example.daggers;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * We initialize this in onEnable() (rather than as a static field built from
 * JavaPlugin.getPlugin()) so there's no risk of it running before the plugin
 * instance actually exists.
 */
public class DaggerKeys {
    public static NamespacedKey TYPE_KEY;

    public static void init(JavaPlugin plugin) {
        TYPE_KEY = new NamespacedKey(plugin, "dagger_type");
    }
}
