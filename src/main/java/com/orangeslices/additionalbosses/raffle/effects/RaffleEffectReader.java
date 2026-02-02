package com.orangeslices.additionalbosses.raffle.effects;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import com.orangeslices.additionalbosses.raffle.RaffleKeys;
import org.bukkit.inventory.ItemStack;
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

    /**
     * Reads effects from an item into a map.
     */
    public static Map<RaffleEffectId, Integer> readFromItem(ItemStack item) {
        Map<RaffleEffectId, Integer> map = new EnumMap<>(RaffleEffectId.class);
        if (item == null) return map;
        if (item.getItemMeta() == null) return map;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        // Stored as "ID:level,ID:level"
        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) return map;

        String[] parts = raw.split(",");
        for (String part : parts) {
            String[] kv = part.split(":");
            if (kv.length != 2) continue;

            RaffleEffectId id = RaffleEffectId.fromString(kv[0]);
            if (id == null) continue;

            int level;
            try {
                level = Integer.parseInt(kv[1]);
            } catch (NumberFormatException ex) {
                continue;
            }

            if (level <= 0) continue;

            // Clamp non-leveling effects
            if (!id.canLevel()) {
                level = 1;
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
            }

            target.merge(id, level, Math::max);
        }
    }
}
