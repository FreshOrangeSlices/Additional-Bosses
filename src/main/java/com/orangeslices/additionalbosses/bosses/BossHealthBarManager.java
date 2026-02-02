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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class BossHealthBarManager {

    private final AdditionalBossesPlugin plugin;
    private final BossApplier bossApplier;

    // bossId -> tracked state
    private final Map<UUID, TrackedBoss> tracked = new ConcurrentHashMap<>();

    // bossId -> last combat timestamp (ms)
    private final Map<UUID, Long> lastCombatMs = new ConcurrentHashMap<>();

    // single manager task
    private BukkitTask tickTask;

    // lightweight config snapshot (refresh every couple seconds)
    private volatile long cfgNextRefreshAtMs = 0L;
    private volatile ConfigSnapshot cfg = new ConfigSnapshot();

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
        refreshConfigIfNeeded();

        if (!cfg.enabled) return;

        UUID id = boss.getUniqueId();
        tracked.computeIfAbsent(id, _id -> new TrackedBoss(_id));
        ensureTaskRunning();
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
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        for (TrackedBoss tb : tracked.values()) {
            if (tb.bar != null) tb.bar.removeAll();
        }

        tracked.clear();
        lastCombatMs.clear();
    }

    // ===============================
    // Internals
    // ===============================

    private void ensureTaskRunning() {
        if (tickTask != null) return;

        // run every 1 tick, but do per-boss work based on configured intervals
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        refreshConfigIfNeeded();

        // If disabled mid-run, clean up and stop.
        if (!cfg.enabled) {
            stopAll();
            return;
        }

        if (tracked.isEmpty()) {
            if (tickTask != null) {
                tickTask.cancel();
                tickTask = null;
            }
            return;
        }

        long nowMs = System.currentTimeMillis();

        // Iterate tracked bosses
        for (UUID id : new ArrayList<>(tracked.keySet())) {
            TrackedBoss tb = tracked.get(id);
            if (tb == null) continue;

            Entity ent = Bukkit.getEntity(id);
            if (!(ent instanceof LivingEntity boss) || !boss.isValid() || boss.isDead()) {
                stopForInternal(id);
                continue;
            }

            // combat-only hide logic
            if (cfg.combatOnly) {
                long last = lastCombatMs.getOrDefault(id, 0L);
                if (nowMs - last > cfg.combatWindowMs) {
                    // out of combat: hide bar if present, keep tracking
                    if (tb.bar != null) tb.bar.removeAll();
                    tb.viewers.clear();
                    tb.hidden = true;
                    continue;
                }
            }

            // ensure bar exists
            if (tb.bar == null) {
                tb.bar = createBarFor(boss);
                tb.hidden = false;
            } else if (tb.hidden) {
                // coming back into combat; bar exists but has no viewers yet
                tb.hidden = false;
            }

            // update title/progress on its own interval
            if ((tb.tickCounter++ % cfg.updateTicks) == 0) {
                updateTitleAndProgress(boss, tb.bar);
            }

            // refresh viewers less frequently
            if ((tb.viewerCounter++ % cfg.viewerUpdateTicks) == 0) {
                updateViewers(boss, tb, cfg.radius);
            }
        }
    }

    private void stopForInternal(UUID id) {
        TrackedBoss tb = tracked.remove(id);
        if (tb != null && tb.bar != null) {
            tb.bar.removeAll();
        }
        lastCombatMs.remove(id);

        if (tracked.isEmpty() && tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
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

        // Placeholder title (updated by manager)
        return Bukkit.createBossBar("Boss", color, style);
    }

    private void updateTitleAndProgress(LivingEntity boss, BossBar bar) {
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
    }

    private void updateViewers(LivingEntity boss, TrackedBoss tb, double radius) {
        BossBar bar = tb.bar;
        if (bar == null) return;

        World w = boss.getWorld();
        double r2 = radius * radius;

        // Who SHOULD be viewing right now
        Set<UUID> should = new HashSet<>();
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) <= r2) {
                should.add(p.getUniqueId());
            }
        }

        // Add newly eligible viewers
        for (UUID pid : should) {
            if (tb.viewers.contains(pid)) continue;
            Player p = Bukkit.getPlayer(pid);
            if (p != null && p.isOnline()) {
                bar.addPlayer(p);
                tb.viewers.add(pid);
            }
        }

        // Remove viewers who are no longer eligible
        for (UUID pid : new ArrayList<>(tb.viewers)) {
            if (should.contains(pid)) continue;
            Player p = Bukkit.getPlayer(pid);
            if (p != null) {
                bar.removePlayer(p);
            }
            tb.viewers.remove(pid);
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

    private void refreshConfigIfNeeded() {
        long now = System.currentTimeMillis();
        if (now < cfgNextRefreshAtMs) return;

        // refresh snapshot every 2 seconds
        cfgNextRefreshAtMs = now + 2000L;

        ConfigSnapshot s = new ConfigSnapshot();
        s.enabled = plugin.getConfig().getBoolean("bossbar.enabled", true);

        s.updateTicks = Math.max(5, plugin.getConfig().getInt("bossbar.update_ticks", 10));

        // new: viewer refresh ticks (slower by default)
        s.viewerUpdateTicks = Math.max(5, plugin.getConfig().getInt("bossbar.viewer_update_ticks", 20));

        s.combatWindowMs = plugin.getConfig().getLong("bossbar.combat_window_ms", 12000L);
        s.radius = plugin.getConfig().getDouble("bossbar.radius", 40.0);
        s.combatOnly = plugin.getConfig().getBoolean("bossbar.combat_only", true);

        cfg = s;
    }

    // ===============================
    // Small structs
    // ===============================

    private static final class TrackedBoss {
        final UUID bossId;
        BossBar bar;
        final Set<UUID> viewers = new HashSet<>();
        long tickCounter = 0;
        long viewerCounter = 0;
        boolean hidden = false;

        TrackedBoss(UUID bossId) {
            this.bossId = bossId;
        }
    }

    private static final class ConfigSnapshot {
        boolean enabled = true;
        int updateTicks = 10;
        int viewerUpdateTicks = 20;
        long combatWindowMs = 12000L;
        double radius = 40.0;
        boolean combatOnly = true;
    }
}
