package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REDUCTION curse
 *
 * Shrinks the player while equipped.
 * Restores original scale cleanly on removal.
 */
public final class ReductionEffect implements RaffleCustomEffect {

    // Requested change: smaller scale
    private static final double REDUCED_SCALE = 0.5;

    private final Map<UUID, Double> originalScale = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.REDUCTION;
    }

    @Override
    public ArmorSlot slotRestriction() {
        // Body/physical presence fits chestplate best
        return ArmorSlot.CHESTPLATE;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        AttributeInstance scale = getScaleAttribute(player);
        if (scale == null) return; // Server/version doesn't support scale

        UUID id = player.getUniqueId();

        // Store original scale once
        originalScale.putIfAbsent(id, scale.getBaseValue());

        scale.setBaseValue(REDUCED_SCALE);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        AttributeInstance scale = getScaleAttribute(player);
        if (scale == null) return;

        Double original = originalScale.remove(player.getUniqueId());
        if (original != null) {
            scale.setBaseValue(original);
        }
    }

    /**
     * Some compile targets don't expose Attribute.GENERIC_SCALE directly.
     * Resolve by name safely; if unavailable, do nothing.
     */
    private static AttributeInstance getScaleAttribute(Player player) {
        try {
            Attribute scaleAttr = Attribute.valueOf("GENERIC_SCALE");
            return player.getAttribute(scaleAttr);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
