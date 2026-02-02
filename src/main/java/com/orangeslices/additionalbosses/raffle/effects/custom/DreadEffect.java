package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DREAD curse
 *
 * Intentional GLOBAL world effect:
 * - Enables storm + thunder while active (private server vibes)
 * - Restores previous weather when the last DREAD player clears
 *
 * Also adds local atmosphere:
 * - Thunder sound
 * - Lightning effect near the player (visual only)
 */
public final class DreadEffect implements RaffleCustomEffect {

    // Tracks whether this player is currently "armed" (so apply() runs once)
    private final Map<UUID, Boolean> armedPlayers = new ConcurrentHashMap<>();

    // World-scoped reference counting + restore state
    private static final Map<UUID, Integer> worldRefCount = new ConcurrentHashMap<>();
    private static final Map<UUID, WeatherSnapshot> worldSnapshot = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.DREAD;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.CHESTPLATE;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Only trigger once per equip
        UUID pid = player.getUniqueId();
        if (Boolean.TRUE.equals(armedPlayers.get(pid))) return;
        armedPlayers.put(pid, true);

        World world = player.getWorld();
        UUID wid = world.getUID();

        // First DREAD in this world -> snapshot + enable storm
        int next = worldRefCount.merge(wid, 1, Integer::sum);
        if (next == 1) {
            worldSnapshot.put(wid, WeatherSnapshot.capture(world));
            world.setStorm(true);
            world.setThundering(true);
        }

        // Local atmosphere (doesn't damage blocks)
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 0.9f);
        strikeAroundPlayer(player);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        UUID pid = player.getUniqueId();
        Boolean wasArmed = armedPlayers.remove(pid);
        if (wasArmed == null || !wasArmed) return;

        World world = player.getWorld();
        UUID wid = world.getUID();

        int next = worldRefCount.merge(wid, -1, Integer::sum);
        if (next <= 0) {
            worldRefCount.remove(wid);

            WeatherSnapshot snap = worldSnapshot.remove(wid);
            if (snap != null) {
                snap.restore(world);
            } else {
                // If somehow missing snapshot, fail safe: stop thunder
                world.setThundering(false);
                world.setStorm(false);
            }
        }
    }

    private void strikeAroundPlayer(Player player) {
        Location base = player.getLocation();
        int strikes = ThreadLocalRandom.current().nextInt(1, 3);

        for (int i = 0; i < strikes; i++) {
            double dx = ThreadLocalRandom.current().nextDouble(2.5, 6.0) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            double dz = ThreadLocalRandom.current().nextDouble(2.5, 6.0) * (ThreadLocalRandom.current().nextBoolean() ? 1 : -1);
            Location strike = base.clone().add(dx, 0, dz);
            base.getWorld().strikeLightningEffect(strike);
        }
    }

    private record WeatherSnapshot(boolean storm, boolean thunder, int weatherDuration) {
        static WeatherSnapshot capture(World w) {
            return new WeatherSnapshot(w.hasStorm(), w.isThundering(), w.getWeatherDuration());
        }

        void restore(World w) {
            w.setStorm(storm);
            w.setThundering(thunder);
            w.setWeatherDuration(weatherDuration);
        }
    }
}
