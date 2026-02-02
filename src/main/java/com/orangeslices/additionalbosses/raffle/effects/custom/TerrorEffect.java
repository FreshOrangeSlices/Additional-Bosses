package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TERROR curse
 *
 * Behavior:
 * - Applies Darkness for ~10 seconds (refreshed by engine ticks)
 * - Plays Warden roar with a cooldown (no spam)
 * - Clears Darkness immediately when removed
 *
 * Visuals:
 * - No particles
 * - No inventory icon (hidden)
 */
public final class TerrorEffect implements RaffleCustomEffect {

    private static final int DARKNESS_DURATION_TICKS = 200; // 10s
    private static final long ROAR_COOLDOWN_MS = 8_000;

    private final Map<UUID, Long> lastRoar = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.TERROR;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.HELMET;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Darkness (ambient, no particles, icon hidden)
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.DARKNESS,
                DARKNESS_DURATION_TICKS,
                0,
                true,   // ambient
                false,  // particles
                false   // icon
        ));

        // Roar with cooldown
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = lastRoar.getOrDefault(id, 0L);

        if (now - last >= ROAR_COOLDOWN_MS) {
            lastRoar.put(id, now);

            player.getWorld().playSound(
                    player.getLocation(),
                    Sound.ENTITY_WARDEN_ROAR,
                    0.6f,
                    1.0f
            );
        }
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        player.removePotionEffect(PotionEffectType.DARKNESS);
        lastRoar.remove(player.getUniqueId());
    }
}
