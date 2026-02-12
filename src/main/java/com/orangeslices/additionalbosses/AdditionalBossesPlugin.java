package com.orangeslices.additionalbosses;

import com.orangeslices.additionalbosses.bosses.BossApplier;
import com.orangeslices.additionalbosses.bosses.BossHealthBarManager;
import com.orangeslices.additionalbosses.bosses.listeners.BossBarCombatListener;
import com.orangeslices.additionalbosses.bosses.listeners.BossCombatListener;
import com.orangeslices.additionalbosses.bosses.listeners.BossDropListener;
import com.orangeslices.additionalbosses.bosses.listeners.SpawnBossListener;
import com.orangeslices.additionalbosses.commands.BecCommand;
import com.orangeslices.additionalbosses.kits.listeners.KitApplyListener;
import com.orangeslices.additionalbosses.raffle.RaffleApplyListener;
import com.orangeslices.additionalbosses.raffle.RaffleDebug;
import com.orangeslices.additionalbosses.raffle.RaffleKeys;
import com.orangeslices.additionalbosses.raffle.RafflePool;
import com.orangeslices.additionalbosses.raffle.RaffleService;
import com.orangeslices.additionalbosses.raffle.RaffleTokenFactory;
import com.orangeslices.additionalbosses.raffle.effects.RafflePotionEngine;
import com.orangeslices.additionalbosses.raffle.effects.custom.MatadorKnockbackListener;
import com.orangeslices.additionalbosses.raffle.effects.custom.RaffleCustomEffectEngine;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AdditionalBossesPlugin extends JavaPlugin {

    // ===============================
    // Keys + shared services
    // ===============================
    private NamespacedKey bossKey;
    private BossApplier bossApplier;

    // ===============================
    // Bossbar system
    // ===============================
    private BossHealthBarManager bossHealthBars;

    // ===============================
    // Raffle system core
    // ===============================
    private RafflePool rafflePool;
    private RaffleService raffleService;

    // ===============================
    // Raffle engines
    // ===============================
    private RafflePotionEngine rafflePotionEngine;
    private RaffleCustomEffectEngine raffleCustomEffectEngine;

    // ===============================
    // Boss lifecycle tracking
    // ===============================
    private final Map<UUID, Integer> activeBossesByWorld = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new ConcurrentHashMap<>();

    // Track bosses so we can cleanup even if removed without a death event
    private final Map<UUID, UUID> bossWorldByBossId = new ConcurrentHashMap<>();
    private BukkitTask bossCleanupTask;

    // Listener reference (spawn uses callbacks)
    private SpawnBossListener spawnBossListener;

    // ===============================
    // Config paths (minimal for now)
    // ===============================
    private static final String CFG_RAFFLE_DEBUG = "raffle.debug";
    private static final String CFG_RAFFLE_MAX_SLOTS = "raffle.max_slots_per_armor";

    private static final String CFG_MESSAGES_MODE = "messages.mode";
    private static final String CFG_MESSAGES_MODE_DEFAULT = "CHAT";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        // -------------------------
        // Core init
        // -------------------------
        bossKey = new NamespacedKey(this, "is_boss");
        bossApplier = new BossApplier(this);

        // -------------------------
        // Bossbar init
        // -------------------------
        bossHealthBars = new BossHealthBarManager(this, bossApplier);

        // -------------------------
        // Raffle init
        // -------------------------
        RaffleKeys.init(this);
        RaffleTokenFactory.init(this);

        RaffleDebug.init(this);
        RaffleDebug.setEnabled(getConfig().getBoolean(CFG_RAFFLE_DEBUG, false));

        rafflePool = new RafflePool(this);
        rafflePool.reloadFromConfig();

        raffleService = new RaffleService(rafflePool);

        // -------------------------
        // Raffle engines
        // -------------------------
        rafflePotionEngine = new RafflePotionEngine(this);
        rafflePotionEngine.start();

        raffleCustomEffectEngine = new RaffleCustomEffectEngine(this);
        raffleCustomEffectEngine.start();

        // -------------------------
        // Listeners
        // -------------------------
        spawnBossListener = new SpawnBossListener(this);

        getServer().getPluginManager().registerEvents(new RaffleApplyListener(this), this);

        getServer().getPluginManager().registerEvents(spawnBossListener, this);
        getServer().getPluginManager().registerEvents(new BossCombatListener(this), this);
        getServer().getPluginManager().registerEvents(new BossDropListener(this), this);

        // Bossbar combat-only trigger
        getServer().getPluginManager().registerEvents(new BossBarCombatListener(this), this);

        // Kits
        getServer().getPluginManager().registerEvents(new KitApplyListener(this), this);

        // Matador knockback
        getServer().getPluginManager().registerEvents(new MatadorKnockbackListener(this), this);

        // -------------------------
        // Boss cleanup task (API-stable replacement for remove-from-world events)
        // -------------------------
        startBossCleanupTask();

        // -------------------------
        // Command
        // -------------------------
        if (getCommand("bec") != null) {
            getCommand("bec").setExecutor(new BecCommand(this, bossApplier));
        }

        getLogger().info("Additional-Bosses enabled.");
    }

    @Override
    public void onDisable() {
        if (bossCleanupTask != null) {
            bossCleanupTask.cancel();
            bossCleanupTask = null;
        }

        if (bossHealthBars != null) {
            bossHealthBars.stopAll();
            bossHealthBars = null;
        }

        if (raffleCustomEffectEngine != null) {
            raffleCustomEffectEngine.stop();
            raffleCustomEffectEngine = null;
        }

        if (rafflePotionEngine != null) {
            rafflePotionEngine.stop();
            rafflePotionEngine = null;
        }

        for (UUID id : despawnTasks.keySet()) {
            cancelBossDespawn(id);
        }
        despawnTasks.clear();
        activeBossesByWorld.clear();
        bossWorldByBossId.clear();

        getLogger().info("Additional-Bosses disabled.");
    }

    // ===============================
    // Required getters
    // ===============================
    public NamespacedKey bossKey() {
        return bossKey;
    }

    public BossApplier bossApplier() {
        return bossApplier;
    }

    public BossHealthBarManager bossHealthBars() {
        return bossHealthBars;
    }

    // ===============================
    // Raffle accessors
    // ===============================
    public RafflePool rafflePool() {
        return rafflePool;
    }

    public RaffleService raffleService() {
        return raffleService;
    }

    public int raffleMaxSlotsPerArmor() {
        return getConfig().getInt(CFG_RAFFLE_MAX_SLOTS, RaffleService.DEFAULT_MAX_SLOTS);
    }

    // ===============================
    // Broadcast helpers
    // ===============================
    public void broadcastLocal(Location at, double radius, String msgColored) {
        if (at == null || at.getWorld() == null) return;

        World w = at.getWorld();
        double r2 = radius * radius;

        String mode = getConfig().getString(CFG_MESSAGES_MODE, CFG_MESSAGES_MODE_DEFAULT);
        if (mode == null) mode = CFG_MESSAGES_MODE_DEFAULT;
        mode = mode.trim().toUpperCase();

        String colored = ChatColor.translateAlternateColorCodes('&', msgColored);

        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(at) > r2) continue;

            switch (mode) {
                case "ACTIONBAR":
                    sendActionBar(p, colored);
                    break;
                case "TITLE":
                    p.sendTitle(colored, "", 5, 40, 10);
                    break;
                default:
                    p.sendMessage(colored);
                    break;
            }
        }
    }

    private void sendActionBar(Player player, String coloredMessage) {
        try {
            player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacyText(coloredMessage)
            );
        } catch (Throwable t) {
            player.sendMessage(coloredMessage);
        }
    }

    // ===============================
    // Boss lifecycle helpers
    // ===============================
    public void onBossCreated(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        UUID worldId = boss.getWorld().getUID();
        activeBossesByWorld.merge(worldId, 1, Integer::sum);

        bossWorldByBossId.put(boss.getUniqueId(), worldId);

        if (spawnBossListener != null) {
            spawnBossListener.onBossCreated(boss);
        }

        if (bossHealthBars != null) {
            bossHealthBars.trackBoss(boss);
        }
    }

    public void onBossRemoved(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        UUID worldId = boss.getWorld().getUID();
        activeBossesByWorld.compute(worldId, (k, v) -> Math.max(0, (v == null ? 0 : v) - 1));
        cancelBossDespawn(boss.getUniqueId());

        bossWorldByBossId.remove(boss.getUniqueId());

        if (bossHealthBars != null) {
            bossHealthBars.stopFor(boss);
        }
    }

    public int activeBossesInWorld(World world) {
        if (world == null) return 0;
        return activeBossesByWorld.getOrDefault(world.getUID(), 0);
    }

    public void scheduleBossDespawn(UUID bossId, BukkitTask task) {
        cancelBossDespawn(bossId);
        despawnTasks.put(bossId, task);
    }

    public void cancelBossDespawn(UUID bossId) {
        BukkitTask old = despawnTasks.remove(bossId);
        if (old != null) old.cancel();
    }

    /**
     * Step 1: Safe "boss creation" entry point.
     * For now it just applies the boss and reuses existing lifecycle tracking.
     * (We’ll re-route natural spawns + /bec test to call this next.)
     */
    public void createBoss(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;
        if (!boss.isValid() || boss.isDead()) return;

        // Don't double-apply
        if (bossApplier.isBoss(boss)) return;

        // Apply rank/stats/name/etc
        bossApplier.apply(boss);

        // Reuse existing lifecycle tracking + spawn FX/despawn scheduling (via SpawnBossListener)
        onBossCreated(boss);
    }

    // ===============================
    // Cleanup task (replaces remove-from-world events)
    // ===============================
    private void startBossCleanupTask() {
        int periodTicks = Math.max(40, getConfig().getInt("cleanup.boss_check_ticks", 200)); // default 10s
        bossCleanupTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Iterator<Map.Entry<UUID, UUID>> it = bossWorldByBossId.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, UUID> e = it.next();

                UUID bossId = e.getKey();
                UUID worldId = e.getValue();

                Entity ent = Bukkit.getEntity(bossId);
                if (!(ent instanceof LivingEntity live) || !ent.isValid() || live.isDead()) {
                    // boss vanished -> cleanup by IDs
                    it.remove();

                    activeBossesByWorld.compute(worldId, (k, v) -> Math.max(0, (v == null ? 0 : v) - 1));
                    cancelBossDespawn(bossId);

                    if (bossHealthBars != null) {
                        // requires BossHealthBarManager.stopForId(UUID)
                        bossHealthBars.stopForId(bossId);
                    }
                }
            }
        }, periodTicks, periodTicks);
    }
}
