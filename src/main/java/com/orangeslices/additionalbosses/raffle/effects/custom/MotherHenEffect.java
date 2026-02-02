package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MOTHER_HEN curse
 *
 * Spawns baby chickens that:
 * - Follow the player
 * - Face the player
 * - Teleport if too far
 * - Despawn cleanly
 */
public final class MotherHenEffect implements RaffleCustomEffect {

    private static final int COUNT = 10;
    private static final int SPAWN_INTERVAL_TICKS = 5;
    private static final int DESPAWN_TICKS = 20 * 12;

    private static final int FOLLOW_PERIOD_TICKS = 10;
    private static final double FOLLOW_SPEED = 0.22;
    private static final double TELEPORT_IF_FAR = 10.0;
    private static final double STOP_DISTANCE = 1.6;

    private final Map<UUID, List<Entity>> spawned = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> followTasks = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> spawnTasks = new ConcurrentHashMap<>();

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
        if (spawned.containsKey(id)) return;

        List<Entity> list = new ArrayList<>();
        spawned.put(id, list);

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_CHICKEN_AMBIENT,
                0.6f,
                1.2f
        );

        // Spawn chicks gradually
        BukkitTask spawnTask = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugins()[0],
                new Runnable() {
                    int i = 0;

                    @Override
                    public void run() {
                        if (!player.isOnline() || i >= COUNT) {
                            BukkitTask t = spawnTasks.remove(id);
                            if (t != null) t.cancel();
                            return;
                        }

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

                        i++;
                    }
                },
                0L,
                SPAWN_INTERVAL_TICKS
        );

        spawnTasks.put(id, spawnTask);

        // Follow + face task
        BukkitTask followTask = Bukkit.getScheduler().runTaskTimer(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> {
                    if (!player.isOnline()) return;

                    List<Entity> ents = spawned.get(id);
                    if (ents == null) return;

                    Location pLoc = player.getLocation();

                    Iterator<Entity> it = ents.iterator();
                    while (it.hasNext()) {
                        Entity e = it.next();
                        if (!(e instanceof Chicken chick) || !chick.isValid()) {
                            it.remove();
                            continue;
                        }

                        Location cLoc = chick.getLocation();
                        double dist = cLoc.distance(pLoc);

                        // Teleport back if too far
                        if (dist > TELEPORT_IF_FAR) {
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

                        if (dist <= STOP_DISTANCE) continue;

                        Vector dir = pLoc.toVector().subtract(cLoc.toVector()).setY(0);
                        if (dir.lengthSquared() < 0.001) continue;

                        Vector vel = dir.normalize().multiply(FOLLOW_SPEED);
                        vel.setY(chick.getVelocity().getY());
                        chick.setVelocity(vel);
                    }
                },
                0L,
                FOLLOW_PERIOD_TICKS
        );

        followTasks.put(id, followTask);

        // Auto cleanup
        BukkitTask despawn = Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> cleanup(id),
                DESPAWN_TICKS
        );

        despawnTasks.put(id, despawn);
    }

    @Override
    public void clear(Player player) {
        if (player != null) cleanup(player.getUniqueId());
    }

    private void cleanup(UUID id) {
        BukkitTask t;

        t = spawnTasks.remove(id);
        if (t != null) t.cancel();

        t = followTasks.remove(id);
        if (t != null) t.cancel();

        t = despawnTasks.remove(id);
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
