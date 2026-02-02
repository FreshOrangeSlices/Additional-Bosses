package com.orangeslices.additionalbosses;

import com.orangeslices.additionalbosses.bosses.BossApplier;
import com.orangeslices.additionalbosses.bosses.listeners.BossCombatListener;
import com.orangeslices.additionalbosses.bosses.listeners.BossDropListener;
import com.orangeslices.additionalbosses.bosses.listeners.SpawnBossListener;
import com.orangeslices.additionalbosses.commands.BecCommand;
import com.orangeslices.additionalbosses.kits.PotionAddOnListener;
import com.orangeslices.additionalbosses.raffle.RaffleApplyListener;
import com.orangeslices.additionalbosses.raffle.RaffleDebug;
import com.orangeslices.additionalbosses.raffle.RaffleKeys;
import com.orangeslices.additionalbosses.raffle.RafflePool;
import com.orangeslices.additionalbosses.raffle.RaffleService;
import com.orangeslices.additionalbosses.raffle.RaffleTokenFactory;
import com.orangeslices.additionalbosses.raffle.effects.RafflePotionEngine;
import com.orangeslices.additionalbosses.raffle.effects.custom.RaffleCustomEffectEngine;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

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

    // ===============================
    // Legacy (optional) kit system
    // ===============================
    private PotionAddOnListener potionAddOnListener;

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
        // We’re doing config LAST, but this is safe even if config is empty.
        saveDefaultConfig();
        reloadConfig();

        // -------------------------
        // Core init
        // -------------------------
        bossKey = new NamespacedKey(this, "is_boss");
        bossApplier = new BossApplier(this);

        // -------------------------
        // Raffle init
        // -------------------------
        RaffleKeys.init(this);
        RaffleTokenFactory.init(this);

        RaffleDebug.init(this);
        RaffleDebug.setEnabled(getConfig().getBoolean(CFG_RAFFLE_DEBUG, false));

        rafflePool = new RafflePool(this);
        // If config is minimal, the pool should still have safe defaults internally.
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

        // -------------------------
        // Legacy kits / potion add-ons (optional)
        // -------------------------
        potionAddOnListener = new PotionAddOnListener(this);
        getServer().getPluginManager().registerEvents(potionAddOnListener, this);
        potionAddOnListener.start();

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
        if (raffleCustomEffectEngine != null) {
            raffleCustomEffectEngine.stop();
            raffleCustomEffectEngine = null;
        }

        if (rafflePotionEngine != null) {
            rafflePotionEngine.stop();
            rafflePotionEngine = null;
        }

        if (potionAddOnListener != null) {
            potionAddOnListener.stop();
            potionAddOnListener = null;
        }

        for (UUID id : despawnTasks.keySet()) {
            cancelBossDespawn(id);
        }
        despawnTasks.clear();
        activeBossesByWorld.clear();

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
            // Fallback (older builds / forks)
            player.sendMessage(coloredMessage);
        }
    }

    // ===============================
    // Boss lifecycle helpers
    // ===============================
    public void onBossCreated(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        UUID w = boss.getWorld().getUID();
        activeBossesByWorld.merge(w, 1, Integer::sum);

        if (spawnBossListener != null) {
            spawnBossListener.onBossCreated(boss);
        }
    }

    public void onBossRemoved(LivingEntity boss) {
        if (boss == null || boss.getWorld() == null) return;

        UUID w = boss.getWorld().getUID();
        activeBossesByWorld.compute(w, (k, v) -> Math.max(0, (v == null ? 0 : v) - 1));
        cancelBossDespawn(boss.getUniqueId());
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
}
