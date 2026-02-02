package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECHOES curse
 *
 * Auditory hallucinations around the player.
 * Purely atmospheric, player-local.
 */
public final class EchoesEffect implements RaffleCustomEffect {

    private static final int RUN_TICKS = 20 * 10; // ~10s
    private static final int PERIOD_TICKS = 20;   // 1s

    private final Random rng = new Random();
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.ECHOES;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.HELMET;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (tasks.containsKey(id)) return; // already active

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p == null || !p.isOnline()) {
                        clear(player);
                        return;
                    }

                    Location loc = p.getLocation().clone();
                    loc.add(
                            rng.nextInt(11) - 5,
                            0,
                            rng.nextInt(11) - 5
                    );

                    Sound s = switch (rng.nextInt(5)) {
                        case 0 -> Sound.ENTITY_SPIDER_AMBIENT;
                        case 1 -> Sound.ENTITY_ZOMBIE_AMBIENT;
                        case 2 -> Sound.ENTITY_ENDERMAN_STARE;
                        case 3 -> Sound.ENTITY_SKELETON_AMBIENT;
                        default -> Sound.ENTITY_WITCH_AMBIENT;
                    };

                    p.getWorld().playSound(loc, s, 0.35f, 0.9f);
                },
                0L,
                PERIOD_TICKS
        );

        tasks.put(id, task);

        // Auto-stop after duration
        Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) clear(p);
                },
                RUN_TICKS
        );
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        BukkitTask t = tasks.remove(player.getUniqueId());
        if (t != null) t.cancel();
    }
}
