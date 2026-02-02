package com.orangeslices.additionalbosses.bosses.listeners;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import io.papermc.paper.event.entity.EntityRemoveFromWorldEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Cleanup hook for when bosses are removed without dying:
 * - despawn timer removes them
 * - chunk unload removes them
 * - /kill or other plugins remove them
 *
 * Ensures active boss counts + bossbars + despawn tasks don't drift.
 *
 * Paper event: io.papermc.paper.event.entity.EntityRemoveFromWorldEvent
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

        plugin.onBossRemoved(boss);
    }
}
