package com.orangeslices.additionalbosses.bosses;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossHealthBarManager {

    private final AdditionalBossesPlugin plugin;
    private final BossApplier bossApplier;

    // bossId -> BossBar
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    // bossId -> updater task
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    // bossId -> last combat timestamp (ms)
    private final Map<UUID, Long> lastCombatMs = new ConcurrentHashMap<>();

    public BossHealthBarManager(AdditionalBossesPlugin plugin, BossApplier bossApplier) {
        this.plugin = plugin;
        this.bossApplier = bossApplier;
    }

    /** Call when boss takes/deals damage to keep bar alive (combat-only mode). */
    public void markInCombat(LivingEntity boss) {
        if (boss == null) return;
        if (!bossApplier.isBoss(boss)) return;
        lastCombatMs.put(boss.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Call when boss is created.
     * This begins tracking updates. If combat_only=true, bar will only appear after combat.
     */
    public void trackBoss(LivingEntity boss) {
        if (boss == null) return;
        if (!bossApplier.isBoss(boss)) return;

        if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) return;

        UUID id = boss.getUniqueId();
        if (tasks.containsKey(id)) return; // already tracking

        int period = Math.max(5, plugin.getConfig().getInt("bossbar.update_ticks", 10));
        long combatWindowMs = plugin.getConfig().getLong("bossbar.combat_window_ms", 12000L);
        double radius = plugin.getConfig().getDouble("bossbar.radius", 40.0);
        boolean combatOnly = plugin.getConfig().getBoolean("bossbar.combat_only", true);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Entity ent = Bukkit.getEntity(id);
            if (!(ent instanceof LivingEntity live) || !live.isValid() || live.isDead()) {
                stopForInternal(id);
                return;
            }

            // If disabled mid-run, clean up.
            if (!plugin.getConfig().getBoolean("bossbar.enabled", true)) {
                stopForInternal(id);
                return;
            }

            if (combatOnly) {
                long last = lastCombatMs.getOrDefault(id, 0L);
                if (System.currentTimeMillis() - last > combatWindowMs) {
                    // Out of combat: hide bar if present, keep tracking
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

    /** Stop tracking and remove bar for this boss entity. */
    public void stopFor(LivingEntity boss) {
        if (boss == null) return;
        stopForInternal(boss.getUniqueId());
    }

    /** Stop tracking + remove bar by UUID (used by cleanup task). */
    public void stopForId(UUID bossId) {
        if (bossId == null) return;
        stopForInternal(bossId);
    }

    /** Stop all boss bars and tasks. */
    public void stopAll() {
        for (UUID id : new ArrayList<>(tasks.keySet())) {
            stopForInternal(id);
        }
        tasks.clear();
        bars.clear();
        lastCombatMs.clear();
    }

    // ===============================
    // Internals
    // ===============================

    private void stopForInternal(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();

        BossBar bar = bars.remove(id);
        if (bar != null) bar.removeAll();

        lastCombatMs.remove(id);
    }

    private BossBar createBarFor(LivingEntity boss) {
        String rankId = bossApplier.getRank(boss);

        ConfigurationSection rankSec = (rankId != null && !rankId.isBlank())
                ? plugin.getConfig().getConfigurationSection("ranks." + rankId)
                : null;

        String defaultColor = plugin.getConfig().getString("bossbar.color", "PURPLE");
        String defaultStyle = plugin.getConfig().getString("bossbar.style", "SOLID");

        String colorKey = defaultColor;
        String styleKey = defaultStyle;

        if (rankSec != null) {
            // allow: ranks.<id>.bossbar.color / ranks.<id>.bossbar.style
            ConfigurationSection barSec = rankSec.getConfigurationSection("bossbar");
            if (barSec != null) {
                colorKey = barSec.getString("color", colorKey);
                styleKey = barSec.getString("style", styleKey);
            }
        }

        BarColor color = safeBarColor(colorKey, BarColor.PURPLE);
        BarStyle style = safeBarStyle(styleKey, BarStyle.SOLID);

        // Placeholder title (updated each tick)
        return Bukkit.createBossBar("Boss", color, style);
    }

    private void updateBar(LivingEntity boss, BossBar bar, double radius) {
        // Title
        bar.setTitle(buildTitle(boss));

        // Progress
        double hp = Math.max(0.0, boss.getHealth());
        double max = 1.0;

        AttributeInstance maxHp = boss.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            max = Math.max(1.0, maxHp.getValue());
        }

        double pct = Math.min(1.0, hp / max);
        bar.setProgress(Math.max(0.0, pct));

        // Viewers (same world within radius)
        World w = boss.getWorld();
        double r2 = radius * radius;

        // Add/remove viewers
        for (Player p : w.getPlayers()) {
            boolean near = p.getLocation().distanceSquared(boss.getLocation()) <= r2;
            boolean already = bar.getPlayers().contains(p);

            if (near && !already) bar.addPlayer(p);
            if (!near && already) bar.removePlayer(p);
        }
    }

    private String buildTitle(LivingEntity boss) {
        String format = plugin.getConfig().getString(
                "bossbar.title_format",
                "&6{rank_color}{title}{mob} &7[{affixes}]"
        );

        String rankId = bossApplier.getRank(boss);

        // default rank color (fallback to boss name color)
        String rankColor = plugin.getConfig().getString("boss.name.color", "&c");

        ConfigurationSection rankSec = (rankId != null && !rankId.isBlank())
                ? plugin.getConfig().getConfigurationSection("ranks." + rankId)
                : null;

        if (rankSec != null) {
            rankColor = rankSec.getString("color", rankColor);
        }

        String title = bossApplier.getTitle(boss);
        String titlePart = (title != null && !title.isBlank()) ? title.trim() + " " : "";

        String mob = prettyMobName(boss.getType());

        String affixesCsv = bossApplier.getAffixesString(boss);
        String affixesPretty = prettyAffixes(affixesCsv);

        String out = format
                .replace("{rank}", rankId == null ? "" : rankId)
                .replace("{rank_color}", rankColor == null ? "" : rankColor)
                .replace("{title}", titlePart)
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
