package com.example.daggers.listeners;

import com.example.daggers.CooldownManager;
import com.example.daggers.DaggerItem;
import com.example.daggers.DaggerType;
import com.example.daggers.FreezeManager;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Set;
import java.util.UUID;

public class DaggerAbilityListener implements Listener {

    private static final String FIRE_DAGGER_PROJECTILE_TAG = "flame_dagger_fireball";
    private static final double GRAPPLE_RANGE = 32.0;

    private final JavaPlugin plugin;
    private final CooldownManager cooldowns;
    private final FreezeManager freezeManager;
    private final Set<UUID> pendingSoulCurse;
    private final Set<UUID> pendingDarknessCurse;

    public DaggerAbilityListener(JavaPlugin plugin, CooldownManager cooldowns, FreezeManager freezeManager,
                                  Set<UUID> pendingSoulCurse, Set<UUID> pendingDarknessCurse) {
        this.plugin = plugin;
        this.cooldowns = cooldowns;
        this.freezeManager = freezeManager;
        this.pendingSoulCurse = pendingSoulCurse;
        this.pendingDarknessCurse = pendingDarknessCurse;
    }

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        DaggerType type = DaggerItem.getType(item);
        if (type == null) return;

        event.setCancelled(true); // don't place blocks / open containers while using the ability

        if (cooldowns.isOnCooldown(player.getUniqueId(), type)) {
            long remaining = cooldowns.getRemainingSeconds(player.getUniqueId(), type);
            player.sendMessage("§c" + type.getDisplayName() + " ability on cooldown: " + remaining + "s");
            return;
        }

        boolean success = switch (type) {
            case FIRE -> castFireball(player);
            case ICE -> castFreeze(player);
            case WATER -> castDolphinsGrace(player);
            case SOUL -> castSoulCurse(player);
            case DARKNESS -> castDarknessCurse(player);
            case BACKSTAB -> castGrapple(player);
        };

        if (success) {
            cooldowns.startCooldown(player.getUniqueId(), type, getCooldownSeconds(type));
        }
    }

    private int getCooldownSeconds(DaggerType type) {
        return type == DaggerType.DARKNESS ? 75 : 60;
    }

    private boolean castFireball(Player player) {
        Fireball fireball = player.launchProjectile(Fireball.class);
        fireball.setIsIncendiary(true);
        fireball.setYield(1.0f); // explosion power similar to a normal ghast fireball
        fireball.setVelocity(player.getLocation().getDirection().multiply(1.5));

        // Tag it so our ProjectileHitEvent handler knows to add the bonus true damage
        fireball.setMetadata(FIRE_DAGGER_PROJECTILE_TAG, new FixedMetadataValue(plugin, true));

        player.sendMessage("§6You hurl a fireball!");
        return true;
    }

    @EventHandler
    public void onFireballHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Fireball fireball)) return;
        if (!fireball.hasMetadata(FIRE_DAGGER_PROJECTILE_TAG)) return;

        if (event.getHitEntity() instanceof LivingEntity target) {
            // "True damage" - bypasses armor by editing health directly instead
            // of going through the normal damage/armor-reduction pipeline
            double trueDamage = 6.0; // 3 hearts
            target.setHealth(Math.max(0, target.getHealth() - trueDamage));
        }
        // Block destruction / fire from the explosion happens automatically
        // because isIncendiary + yield were set when the fireball was launched.
    }

    private boolean castFreeze(Player player) {
        LivingEntity targetEntity = player.getTargetEntity(6);
        if (!(targetEntity instanceof Player target)) {
            player.sendMessage("§cNo player in range to freeze.");
            return false; // don't burn the cooldown if there's nothing to hit
        }

        freezeManager.freeze(target, 80); // 4 seconds = 80 ticks
        target.sendMessage("§b" + player.getName() + " froze you in place!");
        player.sendMessage("§bYou froze " + target.getName() + "!");
        return true;
    }

    private boolean castDolphinsGrace(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DOLPHINS_GRACE, 300, 0)); // 15s
        player.sendMessage("§bDolphin's Grace activated!");
        return true;
    }

    private boolean castSoulCurse(Player player) {
        pendingSoulCurse.add(player.getUniqueId());
        player.sendMessage("§5Your next hit will curse your target!");
        return true;
    }

    private boolean castDarknessCurse(Player player) {
        pendingDarknessCurse.add(player.getUniqueId());
        player.sendMessage("§8Your next hit will engulf your target in darkness!");
        return true;
    }

    /**
     * Fires a ray out to GRAPPLE_RANGE blocks. If it hits a player first, that
     * player gets yanked toward the caster. Otherwise, if it hits a block, the
     * caster gets yanked toward that block. This is an instant velocity pull -
     * there's no rendered rope/chain, which would require a custom client-side
     * visual well beyond a simple plugin.
     */
    private boolean castGrapple(Player player) {
        Vector direction = player.getEyeLocation().getDirection();

        RayTraceResult entityHit = player.getWorld().rayTraceEntities(
                player.getEyeLocation(), direction, GRAPPLE_RANGE,
                entity -> entity instanceof Player p && !p.equals(player)
        );
        RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
                player.getEyeLocation(), direction, GRAPPLE_RANGE, FluidCollisionMode.NEVER, true
        );

        double entityDistance = entityHit != null
                ? entityHit.getHitPosition().distance(player.getEyeLocation().toVector())
                : Double.MAX_VALUE;
        double blockDistance = blockHit != null
                ? blockHit.getHitPosition().distance(player.getEyeLocation().toVector())
                : Double.MAX_VALUE;

        if (entityHit == null && blockHit == null) {
            player.sendMessage("§cNothing in range to grapple.");
            return false;
        }

        if (entityDistance < blockDistance && entityHit.getHitEntity() instanceof Player target) {
            Vector pull = player.getLocation().toVector()
                    .subtract(target.getLocation().toVector())
                    .normalize()
                    .multiply(1.8);
            pull.setY(Math.max(pull.getY(), 0.3));
            target.setVelocity(pull);
            player.sendMessage("§eYou grappled " + target.getName() + " towards you!");
        } else {
            Vector pull = blockHit.getHitPosition()
                    .subtract(player.getEyeLocation().toVector())
                    .normalize()
                    .multiply(1.8);
            pull.setY(Math.max(pull.getY(), 0.3));
            player.setVelocity(pull);
            player.sendMessage("§eYou grapple toward the block!");
        }
        return true;
    }
}
