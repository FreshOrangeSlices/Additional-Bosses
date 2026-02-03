package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOTHER_HEN curse
 *
 * Optimized: ONE repeating task per affected player.
 *
 * Spawns baby chickens that:
 * - Follow the player
 * - Face the player
 * - Teleport if too far
 * - Despawn cleanly
 */
public final class MotherHenEffect implements RaffleCustomEffect {

    private static final int COUNT = 10;

    // spawn pacing: every 5 ticks (same as before)
    private static final int SPAWN_INTERVAL_TICKS = 5;

    // effect duration: 12 seconds (same as before)
    private static final int DESPAWN_TICKS = 20 * 12;

    // main loop runs every 10 ticks (same as old follow period)
    private static final int LOOP_PERIOD_TICKS = 10;

    private static final double FOLLOW_SPEED = 0.22;
    private static final double TELEPORT_IF_FAR = 10.0;
    private static final double STOP_DISTANCE = 1.6;

    // squared thresholds (avoid sqrt)
    private static final double TELEPORT_IF_FAR_SQ = TELEPORT_IF_FAR * TELEPORT_IF_FAR;
    private static final double STOP_DISTANCE_SQ = STOP_DISTANCE * STOP_DISTANCE;

    private final JavaPlugin plugin;

    private final Map<UUID, List<Entity>> spawned = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> tasks = new ConcurrentHashMap<>();

    public MotherHenEffect(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MOTHER_HEN;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.LEGGINGS;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (tasks.containsKey(id)) return; // already running

        List<Entity> list = new ArrayList<>(COUNT);
        spawned.put(id, list);

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_CHICKEN_AMBIENT,
                0.6f,
                1.2f
        );

        final long startTick = Bukkit.getCurrentTick();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int spawnedCount = 0;
            long nextSpawnTick = startTick; // spawn immediately

            @Override
            public void run() {
                // expire / offline -> cleanup
                if (!player.isOnline()) {
                    cleanup(id);
                    return;
                }

                long nowTick = Bukkit.getCurrentTick();
                if (nowTick - startTick >= DESPAWN_TICKS) {
                    cleanup(id);
                    return;
                }

                // Spawn gradually (every SPAWN_INTERVAL_TICKS)
                while (spawnedCount < COUNT && nowTick >= nextSpawnTick) {
                    spawnChick(player, spawnedCount, list);
                    spawnedCount++;
                    nextSpawnTick += SPAWN_INTERVAL_TICKS;
                }

                // Follow + face
                Location pLoc = player.getLocation();

                Iterator<Entity> it = list.iterator();
                while (it.hasNext()) {
                    Entity e = it.next();
                    if (!(e instanceof Chicken chick) || !chick.isValid()) {
                        it.remove();
                        continue;
                    }

                    Location cLoc = chick.getLocation();
                    double distSq = cLoc.distanceSquared(pLoc);

                    // Teleport back if too far
                    if (distSq > TELEPORT_IF_FAR_SQ) {
                        chick.teleport(
                                pLoc.clone().add(
                                        random(-1.5, 1.5),
                                        0,
                                        random(-1.5, 1.5)
                                )
                        );
                        continue;
                    }

                    // Rotate to face player
                    faceEntity(chick, pLoc);

                    if (distSq <= STOP_DISTANCE_SQ) continue;

                    Vector dir = pLoc.toVector().subtract(cLoc.toVector()).setY(0);
                    if (dir.lengthSquared() < 0.001) continue;

                    Vector vel = dir.normalize().multiply(FOLLOW_SPEED);
                    vel.setY(chick.getVelocity().getY());
                    chick.setVelocity(vel);
                }

                // If we've spawned all and none remain, just end early
                if (spawnedCount >= COUNT && list.isEmpty()) {
                    cleanup(id);
                }
            }
        }, 0L, LOOP_PERIOD_TICKS);

        tasks.put(id, task);
    }

    @Override
    public void clear(Player player) {
        if (player != null) cleanup(player.getUniqueId());
    }

    private void spawnChick(Player player, int i, List<Entity> list) {
        Location base = player.getLocation();
        Location spawnLoc = base.clone().add(
                (i - COUNT / 2.0) * 0.25,
                0,
                0.6
        );

        Chicken chick = player.getWorld().spawn(spawnLoc, Chicken.class, c -> {
            c.setBaby();
            c.setRemoveWhenFarAway(true);
        });

        list.add(chick);

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_CHICKEN_AMBIENT,
                0.25f,
                1.6f
        );
    }

    private void cleanup(UUID id) {
        BukkitTask t = tasks.remove(id);
        if (t != null) t.cancel();

        List<Entity> ents = spawned.remove(id);
        if (ents != null) {
            for (Entity e : ents) {
                if (e != null && e.isValid()) e.remove();
            }
        }
    }

    private static void faceEntity(Entity entity, Location target) {
        Location loc = entity.getLocation();
        Vector dir = target.toVector().subtract(loc.toVector());

        float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        loc.setYaw(yaw);

        entity.teleport(loc);
    }

    private static double random(double min, double max) {
        return min + (Math.random() * (max - min));
    }
}
