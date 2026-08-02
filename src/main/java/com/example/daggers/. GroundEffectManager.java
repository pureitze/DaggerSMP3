package com.example.daggers;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;

public class GroundEffectManager {

    private final JavaPlugin plugin;

    public GroundEffectManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Sets fire above solid ground blocks in a circle, then reverts after durationTicks. */
    public void createFireField(Location center, int radius, long durationTicks) {
        Map<Block, BlockData> originals = new HashMap<>();
        int r2 = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;

                Block ground = center.clone().add(dx, -1, dz).getBlock();
                Block above = center.clone().add(dx, 0, dz).getBlock();

                if (!ground.getType().isSolid()) continue;
                if (above.getType() != Material.AIR) continue;

                originals.put(above, above.getBlockData().clone());
                above.setType(Material.FIRE, false);
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<Block, BlockData> entry : originals.entrySet()) {
                entry.getKey().setBlockData(entry.getValue(), false);
            }
        }, durationTicks);
    }

    /** Replaces the top layer of ground in a circle with packed ice, then reverts after durationTicks. */
    public void createIceField(Location center, int radius, long durationTicks) {
        Map<Block, BlockData> originals = new HashMap<>();
        int r2 = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > r2) continue;

                Block ground = center.clone().add(dx, -1, dz).getBlock();
                if (!ground.getType().isSolid()) continue;
                if (ground.getType() == Material.PACKED_ICE || ground.getType() == Material.ICE) continue;

                originals.put(ground, ground.getBlockData().clone());
                ground.setType(Material.PACKED_ICE, false);
            }
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            for (Map.Entry<Block, BlockData> entry : originals.entrySet()) {
                entry.getKey().setBlockData(entry.getValue(), false);
            }
        }, durationTicks);
    }
}
