package com.orangeslices.additionalbosses.bosses;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BossHealthBarManager {

    private final AdditionalBossesPlugin plugin;

    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public BossHealthBarManager(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void startFor(LivingEntity boss) {
        if (boss == null || !boss.isValid()) return;
        if (!plugin.bossApplier().isBoss(boss)) return;

        UUID id = boss.getUniqueId();
        if (bars.containsKey(id)) return;

        boolean enabled = plugin.getConfig().getBoolean("bossbar.enabled", true);
        if (!enabled) return;

        BossBar bar = Bukkit.createBossBar(
                boss.getCustomName() != null ? boss.getCustomName() : "Boss",
                BarColor.valueOf(plugin.getConfig().getString("bossbar.color", "PURPLE").toUpperCase()),
                BarStyle.valueOf(plugin.getConfig().getString("bossbar.style", "SOLID").toUpperCase())
        );

        bars.put(id, bar);

        int period = Math.max(5, plugin.getConfig().getInt("bossbar.update_ticks", 10));
        double radius = plugin.getConfig().getDouble("bossbar.radius", 40.0);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            LivingEntity live = (LivingEntity) Bukkit.getEntity(id);
            if (live == null || !live.isValid() || live.isDead()) {
                stopFor(id);
                return;
            }

            updateBar(live, bar, radius);
        }, 1L, period);

        tasks.put(id, task);
    }

    public void stopFor(LivingEntity boss) {
        if (boss == null) return;
        stopFor(boss.getUniqueId());
    }

    public void stopAll() {
        for (UUID id : bars.keySet()) stopFor(id);
        bars.clear();
        tasks.clear();
    }

    private void stopFor(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();

        BossBar bar = bars.remove(id);
        if (bar != null) {
            bar.removeAll();
        }
    }

    private void updateBar(LivingEntity boss, BossBar bar, double radius) {
        // title
        String name = boss.getCustomName();
        if (name == null || name.isBlank()) name = "Boss";
        bar.setTitle(name);

        // progress
        double hp = Math.max(0.0, boss.getHealth());
        double max = boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) != null
                ? boss.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()
                : 1.0;
        if (max <= 0) max = 1.0;

        double pct = Math.min(1.0, hp / max);
        bar.setProgress(Math.max(0.0, pct));

        // viewers in same world within radius
        World w = boss.getWorld();
        double r2 = radius * radius;

        for (Player p : w.getPlayers()) {
            boolean near = p.getLocation().distanceSquared(boss.getLocation()) <= r2;
            boolean already = bar.getPlayers().contains(p);

            if (near && !already) bar.addPlayer(p);
            if (!near && already) bar.removePlayer(p);
        }
    }
}
