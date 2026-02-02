package com.orangeslices.additionalbosses.bosses;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BossHealthBarManager {

    private final AdditionalBossesPlugin plugin;
    private final BossApplier bossApplier;

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    // combat tracking (set by listener)
    private final Map<UUID, Long> lastCombatMs = new ConcurrentHashMap<>();

    public BossHealthBarManager(AdditionalBossesPlugin plugin, BossApplier bossApplier) {
        this.plugin = plugin;
        this.bossApplier = bossApplier;
    }

    /** Call when boss takes/deals damage to keep bar alive */
    public void markInCombat(LivingEntity boss) {
        if (boss == null) return;
        if (!bossApplier.isBoss(boss)) return;
        lastCombatMs.put(boss.getUniqueId(), System.currentTimeMillis());
    }

    /** Call when boss is created (we won't show bar until combat happens) */
    public void trackBoss(LivingEntity boss) {
        if (boss == null) return;
        if (!bossApplier.isBoss(boss)) return;

        boolean enabled = plugin.getConfig().getBoolean("bossbar.enabled", true);
        if (!enabled) return;

        UUID id = boss.getUniqueId();
        if (tasks.containsKey(id)) return;

        int period = Math.max(5, plugin.getConfig().getInt("bossbar.update_ticks", 10));
        long combatWindowMs = plugin.getConfig().getLong("bossbar.combat_window_ms", 12000L);
        double radius = plugin.getConfig().getDouble("bossbar.radius", 40.0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LivingEntity live = (LivingEntity) Bukkit.getEntity(id);
            if (live == null || !live.isValid() || live.isDead()) {
                stopFor(id);
                return;
            }

            boolean combatOnly = plugin.getConfig().getBoolean("bossbar.combat_only", true);
            if (combatOnly) {
                long last = lastCombatMs.getOrDefault(id, 0L);
                if (System.currentTimeMillis() - last > combatWindowMs) {
                    // out of combat: hide bar if present
                    BossBar existing = bars.remove(id);
                    if (existing != null) existing.removeAll();
                    return;
                }
            }

            BossBar bar = bars.computeIfAbsent(id, _id -> createBarFor(live));
            updateBar(live, bar, radius);

        }, 1L, period);

        tasks.put(id, task);
    }

    public void stopFor(LivingEntity boss) {
        if (boss == null) return;
        stopFor(boss.getUniqueId());
    }

    public void stopAll() {
        for (UUID id : new ArrayList<>(tasks.keySet())) stopFor(id);
        tasks.clear();
        bars.clear();
        lastCombatMs.clear();
    }

    private void stopFor(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();

        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();

        lastCombatMs.remove(id);
    }

    private BossBar createBarFor(LivingEntity boss) {
        // Resolve rank-based color/style
        String rankId = bossApplier.getRank(boss);
        ConfigurationSection rankSec = (rankId != null && !rankId.isBlank())
                ? plugin.getConfig().getConfigurationSection("ranks." + rankId)
                : null;

        String colorKey = (rankSec != null)
                ? rankSec.getString("bossbar.color", plugin.getConfig().getString("bossbar.color", "PURPLE"))
                : plugin.getConfig().getString("bossbar.color", "PURPLE");

        String styleKey = (rankSec != null)
                ? rankSec.getString("bossbar.style", plugin.getConfig().getString("bossbar.style", "SOLID"))
                : plugin.getConfig().getString("bossbar.style", "SOLID");

        BarColor color = safeBarColor(colorKey, BarColor.PURPLE);
        BarStyle style = safeBarStyle(styleKey, BarStyle.SOLID);

        return Bukkit.createBossBar("Boss", color, style);
    }

    private void updateBar(LivingEntity boss, BossBar bar, double radius) {
        // Title with placeholders
        String title = buildTitle(boss);
        bar.setTitle(title);

        // Progress
        double hp = Math.max(0.0, boss.getHealth());
        double max = 1.0;
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            max = Math.max(1.0, boss.getAttribute(Attribute.MAX_HEALTH).getValue());
        }

        double pct = Math.min(1.0, hp / max);
        bar.setProgress(Math.max(0.0, pct));

        // Viewers (same world within radius)
        World w = boss.getWorld();
        double r2 = radius * radius;

        for (Player p : w.getPlayers()) {
            boolean near = p.getLocation().distanceSquared(boss.getLocation()) <= r2;
            boolean already = bar.getPlayers().contains(p);

            if (near && !already) bar.addPlayer(p);
            if (!near && already) bar.removePlayer(p);
        }
    }

    private String buildTitle(LivingEntity boss) {
        String format = plugin.getConfig().getString("bossbar.title_format",
                "&6{rank_color}{title}{mob} &7[{affixes}]");

        String rankId = bossApplier.getRank(boss);
        String rankColor = plugin.getConfig().getString("boss.name.color", "&c");

        ConfigurationSection rankSec = (rankId != null && !rankId.isBlank())
                ? plugin.getConfig().getConfigurationSection("ranks." + rankId)
                : null;

        if (rankSec != null) {
            rankColor = rankSec.getString("color", rankColor);
        }

        String title = bossApplier.getTitle(boss);
        if (title == null) title = "";
        if (!title.isBlank()) title = title.trim() + " ";

        String mob = prettyMobName(boss.getType());

        String affixesCsv = bossApplier.getAffixesString(boss);
        String affixesPretty = prettyAffixes(affixesCsv);

        String out = format
                .replace("{rank}", rankId == null ? "" : rankId)
                .replace("{rank_color}", rankColor == null ? "" : rankColor)
                .replace("{title}", title)
                .replace("{mob}", mob)
                .replace("{affixes}", affixesPretty);

        return ChatColor.translateAlternateColorCodes('&', out).trim();
    }

    private String prettyAffixes(String csv) {
        if (csv == null || csv.isBlank()) return "none";

        String[] parts = csv.split(",");
        List<String> shown = new ArrayList<>();

        int max = Math.max(1, plugin.getConfig().getInt("bossbar.max_affixes_shown", 3));

        for (String p : parts) {
            if (p == null) continue;
            String id = p.trim();
            if (id.isEmpty()) continue;

            String pretty = plugin.getConfig().getString("affixes.pool." + id + ".name", id);
            shown.add(pretty);

            if (shown.size() >= max) break;
        }

        if (parts.length > max) {
            shown.add("+" + (parts.length - max));
        }

        return String.join(", ", shown);
    }

    private String prettyMobName(org.bukkit.entity.EntityType type) {
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    private BarColor safeBarColor(String key, BarColor fallback) {
        if (key == null) return fallback;
        try {
            return BarColor.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private BarStyle safeBarStyle(String key, BarStyle fallback) {
        if (key == null) return fallback;
        try {
            return BarStyle.valueOf(key.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
