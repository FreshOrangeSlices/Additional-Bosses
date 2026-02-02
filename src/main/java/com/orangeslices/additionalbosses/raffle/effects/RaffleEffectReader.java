package com.orangeslices.additionalbosses.raffle.effects;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import com.orangeslices.additionalbosses.raffle.RaffleKeys;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Reads and merges raffle effects stored in PDC.
 *
 * Normalization rules:
 * - Unknown IDs are ignored
 * - Old IDs are resolved via RaffleEffectId.fromString()
 * - Non-leveling effects are CLAMPED to level 1
 * - Highest level always wins when merging
 */
public final class RaffleEffectReader {

    private RaffleEffectReader() {}

    // Optional safety clamp (prevents corrupted tokens from going insane)
    private static final int MAX_LEVEL = 10;

    /**
     * Reads effects from an item into a map.
     */
    public static Map<RaffleEffectId, Integer> readFromItem(ItemStack item) {
        Map<RaffleEffectId, Integer> map = new EnumMap<>(RaffleEffectId.class);
        if (item == null) return map;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return map;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        // Stored as "ID:level,ID:level"
        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return map;

        String[] parts = raw.split(",");
        for (String part : parts) {
            if (part == null) continue;

            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;

            String[] kv = trimmed.split(":", 2);
            if (kv.length != 2) continue;

            RaffleEffectId id = RaffleEffectId.fromString(kv[0].trim());
            if (id == null) continue;

            int level;
            try {
                level = Integer.parseInt(kv[1].trim());
            } catch (NumberFormatException ex) {
                continue;
            }

            if (level <= 0) continue;

            // Clamp non-leveling effects
            if (!id.canLevel()) {
                level = 1;
            } else {
                // safety clamp (optional but recommended)
                level = Math.min(level, MAX_LEVEL);
            }

            map.merge(id, level, Math::max);
        }

        return map;
    }

    /**
     * Merges source into target, keeping highest level per effect.
     */
    public static void mergeHighest(
            Map<RaffleEffectId, Integer> target,
            Map<RaffleEffectId, Integer> source
    ) {
        if (source == null || target == null) return;

        for (Map.Entry<RaffleEffectId, Integer> e : source.entrySet()) {
            RaffleEffectId id = e.getKey();
            int level = e.getValue();
            if (level <= 0) continue;

            if (!id.canLevel()) {
                level = 1;
            } else {
                level = Math.min(level, MAX_LEVEL);
            }

            target.merge(id, level, Math::max);
        }
    }
}
