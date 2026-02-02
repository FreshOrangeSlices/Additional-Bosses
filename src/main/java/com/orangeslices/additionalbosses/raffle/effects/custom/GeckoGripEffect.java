package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * GECKO_GRIP (GOOD)
 *
 * Boots-only wall climbing effect.
 *
 * Behavior:
 * - While sneaking and touching a wall, gently pushes the player upward.
 * - No potion effects.
 * - No infinite climbing without player intent.
 * - Clears automatically when boots are removed.
 */
public final class GeckoGripEffect implements RaffleCustomEffect {

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.GECKO_GRIP;
    }

    /**
     * Explicit slot restriction (defensive).
     */
    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.BOOTS;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Must be sneaking to activate
        if (!player.isSneaking()) return;

        // Rough wall contact heuristic
        if (!player.isOnGround() && !player.isCollidable()) return;

        if (!player.isOnGround() && player.getVelocity().getY() < 0.2) {
            Vector v = player.getVelocity();

            // Gentle upward nudge (climb-y, not flight-y)
            player.setVelocity(new Vector(
                    v.getX() * 0.85,
                    0.26,
                    v.getZ() * 0.85
            ));
        }
    }

    @Override
    public void clear(Player player) {
        // No persistent state to clean up
    }
}
