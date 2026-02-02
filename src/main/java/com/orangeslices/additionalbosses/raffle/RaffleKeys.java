package com.orangeslices.additionalbosses.raffle;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.NamespacedKey;

/**
 * PersistentDataContainer keys used by the raffle system.
 * Must be initialized once on plugin startup.
 */
public final class RaffleKeys {

    private RaffleKeys() {}

    public static NamespacedKey EFFECTS;     // stored effects + levels
    public static NamespacedKey SLOT_COUNT;  // how many slots are used
    public static NamespacedKey TOKEN_MARKER; // optional: marks an item as a raffle token (byte=1)

    /**
     * Must be called once during plugin startup.
     */
    public static void init(AdditionalBossesPlugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null when initializing RaffleKeys.");
        }

        // Idempotent init
        if (EFFECTS != null && SLOT_COUNT != null) return;

        EFFECTS = new NamespacedKey(plugin, "raffle_effects");
        SLOT_COUNT = new NamespacedKey(plugin, "raffle_slots");
        TOKEN_MARKER = new NamespacedKey(plugin, "raffle_token");
    }

    /**
     * Defensive check to prevent silent null usage.
     */
    public static void validateInit() {
        if (EFFECTS == null || SLOT_COUNT == null) {
            throw new IllegalStateException(
                    "RaffleKeys not initialized. Call RaffleKeys.init(plugin) in onEnable()."
            );
        }
    }
}
