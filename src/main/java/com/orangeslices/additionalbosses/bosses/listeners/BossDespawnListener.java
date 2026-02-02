package com.orangeslices.additionalbosses.bosses.listeners;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveFromWorldEvent;

/**
 * Cleanup hook for when bosses are removed without dying:
 * - despawn timer removes them
 * - chunk unload removes them
 * - /kill, /minecraft:kill, or other plugins remove them
 * - plugin reload edge cases
 *
 * Ensures active boss counts + bossbars + despawn tasks don't drift.
 *
 * Paper event: EntityRemoveFromWorldEvent
 */
public final class BossDespawnListener implements Listener {

    private final AdditionalBossesPlugin plugin;

    public BossDespawnListener(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRemove(EntityRemoveFromWorldEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        // If it's being removed from world and it isn't a normal death,
        // treat as despawn cleanup.
        plugin.onBossRemoved(boss);
    }
}
