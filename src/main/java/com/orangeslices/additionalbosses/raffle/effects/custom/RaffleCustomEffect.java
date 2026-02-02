package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.entity.Player;

/**
 * Contract for NON-potion raffle effects (curses & custom mechanics).
 *
 * Design rules:
 * - Effects are driven by armor being equipped
 * - Levels come from RaffleEffectReader (highest across armor)
 * - Engines/listeners should NEVER hardcode logic for specific effects
 *
 * Implementations live in:
 *   raffle/effects/custom/
 */
public interface RaffleCustomEffect {

    /**
     * Which raffle ID this effect implements.
     */
    RaffleEffectId getId();

    /**
     * Called periodically while the effect should be active.
     * Note: do NOT assume this fires every tick.
     */
    void apply(Player player, int level);

    /**
     * Called when the effect should be removed or cleaned up.
     * (Unequip, death, logout, etc.)
     */
    default void clear(Player player) {
        // optional
    }

    /**
     * Optional: restrict effect to a specific armor slot.
     * Return null for ANY armor.
     */
    default ArmorSlot slotRestriction() {
        return null;
    }

    enum ArmorSlot {
        HELMET,
        CHESTPLATE,
        LEGGINGS,
        BOOTS
    }
}
