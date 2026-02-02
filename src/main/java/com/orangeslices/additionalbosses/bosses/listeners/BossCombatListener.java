package com.orangeslices.additionalbosses.bosses.listeners;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public final class BossCombatListener implements Listener {

    private final AdditionalBossesPlugin plugin;

    public BossCombatListener(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (!plugin.bossApplier().isBoss(boss)) return;

        // Cancel any pending despawn
        plugin.cancelBossDespawn(boss.getUniqueId());

        FileConfiguration cfg = plugin.getConfig();

        // XP calculation
        int baseExp = event.getDroppedExp();
        double globalMult = cfg.getDouble("rewards.xp_multiplier", 1.0);

        String rank = plugin.bossApplier().getRank(boss);
        double rankMult = (rank != null)
                ? cfg.getDouble("ranks." + rank + ".xp_multiplier", 1.0)
                : 1.0;

        double raw = baseExp * globalMult * rankMult;
        int finalExp;
        if (raw <= 0) finalExp = 0;
        else if (raw > Integer.MAX_VALUE) finalExp = Integer.MAX_VALUE;
        else finalExp = (int) Math.round(raw);

        event.setDroppedExp(finalExp);

        // Death message (local broadcast)
        if (cfg.getBoolean("messages.enabled", true)
                && cfg.getBoolean("messages.death.enabled", true)) {

            double radius = cfg.getDouble("messages.radius", 40.0);
            String format = cfg.getString(
                    "messages.death.format",
                    "&aDefeated: {rank_color}{title}{mob} &e(+{rank_xp}x XP)"
            );

            plugin.broadcastLocal(
                    boss.getLocation(),
                    radius,
                    formatDeathMessage(boss, format)
            );
        }

        // IMPORTANT: keep boss tracking + bossbar cleanup accurate
        plugin.onBossRemoved(boss);
    }

    private String formatDeathMessage(LivingEntity boss, String format) {
        FileConfiguration cfg = plugin.getConfig();

        String rank = plugin.bossApplier().getRank(boss);
        String rankLabel = (rank != null)
                ? cfg.getString("ranks." + rank + ".label", rank)
                : "Boss";
        String rankColor = (rank != null)
                ? cfg.getString("ranks." + rank + ".color", "&c")
                : "&c";

        String title = plugin.bossApplier().getTitle(boss);
        String titlePart = (title != null && !title.isBlank()) ? title + " " : "";

        String mobName = boss.getType().name().toLowerCase().replace('_', ' ');
        mobName = Character.toUpperCase(mobName.charAt(0)) + mobName.substring(1);

        double rankXp = (rank != null)
                ? cfg.getDouble("ranks." + rank + ".xp_multiplier", 1.0)
                : 1.0;

        return ChatColor.translateAlternateColorCodes('&',
                format
                        .replace("{rank}", rank == null ? "" : rank)
                        .replace("{rank_label}", rankLabel == null ? "" : rankLabel)
                        .replace("{rank_color}", rankColor == null ? "" : rankColor)
                        .replace("{title}", titlePart)
                        .replace("{mob}", mobName)
                        .replace("{rank_xp}", String.valueOf((int) Math.round(rankXp)))
        );
    }
}
