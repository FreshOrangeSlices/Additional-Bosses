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
 * DISARRAY curse
 *
 * Sudden mental collapse:
 * - Nausea
 * - Slowness
 * - Weakness
 *
 * Short, sharp disruption. No long-term state.
 */
public final class DisarrayEffect implements RaffleCustomEffect {

    private static final int DURATION_TICKS = 20 * 8; // ~8s

    // Prevents re-trigger spam while equipped
    private final Map<UUID, Boolean> armed = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.DISARRAY;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.HELMET;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();
        if (Boolean.TRUE.equals(armed.get(id))) return;
        armed.put(id, true);

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.NAUSEA,
                DURATION_TICKS,
                0,
                true,
                false,
                false
        ));

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                DURATION_TICKS,
                0,
                true,
                false,
                false
        ));

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS,
                DURATION_TICKS,
                0,
                true,
                false,
                false
        ));

        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                0.25f,
                1.2f
        );
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        armed.remove(player.getUniqueId());

        player.removePotionEffect(PotionEffectType.NAUSEA);
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        player.removePotionEffect(PotionEffectType.WEAKNESS);
    }
}
