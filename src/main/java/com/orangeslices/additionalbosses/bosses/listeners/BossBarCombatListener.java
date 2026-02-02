package com.orangeslices.additionalbosses.bosses.listeners;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class BossBarCombatListener implements Listener {

    private final AdditionalBossesPlugin plugin;

    public BossBarCombatListener(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDamaged(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        if (plugin.bossHealthBars() != null) {
            plugin.bossHealthBars().markInCombat(boss);
            plugin.bossHealthBars().trackBoss(boss);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossDealsDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity boss)) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        if (plugin.bossHealthBars() != null) {
            plugin.bossHealthBars().markInCombat(boss);
            plugin.bossHealthBars().trackBoss(boss);
        }
    }
}
