package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * GECKO_GRIP (GOOD)
 *
 * While sneaking near a wall, the player gets a gentle upward nudge,
 * letting them "climb" without full flight.
 *
 * Designed to be called repeatedly (engine tick).
 */
public final class GeckoGripEffect implements RaffleCustomEffect {

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.GECKO_GRIP;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.BOOTS;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        // Intent gate: only active when sneaking
        if (!player.isSneaking()) return;

        // Must be near a solid wall (cheap 4-direction check)
        if (!isNearWall(player.getLocation())) return;

        // Gentle climb nudge
        Vector v = player.getVelocity();

        // If they're falling or not rising much, boost up a bit
        if (v.getY() < 0.18) {
            // Slight horizontal damping to reduce "slippery" wall-skating
            double damp = 0.85;

            // Level scaling (very mild)
            // level 1: 0.26, level 2: 0.28, level 3: 0.30 (cap-ish)
            double up = 0.26 + Math.min(0.04, (level - 1) * 0.02);

            player.setVelocity(new Vector(
                    v.getX() * damp,
                    up,
                    v.getZ() * damp
            ));
        }
    }

    @Override
    public void clear(Player player) {
        // no persistent state to clear
    }

    private static boolean isNearWall(Location loc) {
        // Check around chest-ish height so the floor doesn't count as a "wall"
        Location c = loc.clone().add(0, 1.0, 0);
        Block b = c.getBlock();

        return isSolid(b.getRelative(BlockFace.NORTH))
                || isSolid(b.getRelative(BlockFace.SOUTH))
                || isSolid(b.getRelative(BlockFace.EAST))
                || isSolid(b.getRelative(BlockFace.WEST));
    }

    private static boolean isSolid(Block block) {
        return block != null && block.getType().isSolid();
    }
}
