package com.orangeslices.additionalbosses.raffle;

import java.util.Locale;

/**
 * Raffle effect IDs stored in PDC as "ID:level,ID:level".
 *
 * Raffle = random chaos (includes utility potion effects).
 * Kits = controlled rewards (short curated list).
 */
public enum RaffleEffectId {

    // -------------------------
    // GOOD (levelable)
    // -------------------------
    VITALITY(false, true),        // Health Boost (ANY_ARMOR)
    IRON_WILL(false, true),       // Resistance (CHEST only)
    BLOOD_MENDING(false, true),   // Regeneration (LEGS only)
    SKYBOUND(false, true),        // Jump Boost (BOOTS only)

    // -------------------------
    // GOOD (flat / non-leveling)
    // -------------------------
    EMBER_WARD(false, false),     // Fire Resistance (CHEST only)
    SIGHTBEYOND(false, false),    // Night Vision (HELMET only)
    TIDEBOUND(false, false),      // Conduit Power (HELMET only)
    OCEAN_GRACE(false, false),    // Dolphin's Grace (BOOTS only)

    FORTUNE(false, false),        // Luck (HELMENT only)
    VILLAGER_FAVOR(false, false), // Hero of the Village (CHEST only)

    GECKO_GRIP(false, false),     // Wall Climb (BOOTS only)

    // -------------------------
    // CURSES (non-leveling)
    // -------------------------
    DREAD(true, false),
    MISSTEP(true, false),
    TERROR(true, false),

    UNEASE(true, false),
    ECHOES(true, false),
    DISARRAY(true, false),
    ON_ALL_FOURS(true, false),
    MATADOR(true, false),
    MOTHER_HEN(true, false),
    IMPOSTER(true, false),
    REDUCTION(true, false);

    private final boolean curse;
    private final boolean canLevel;

    RaffleEffectId(boolean curse, boolean canLevel) {
        this.curse = curse;
        this.canLevel = canLevel;
    }

    public boolean isCurse() {
        return curse;
    }

    public boolean isGood() {
        return !curse;
    }

    /**
     * True only for GOOD effects that are allowed to level up.
     */
    public boolean canLevel() {
        return !curse && canLevel;
    }

    /**
     * Case-insensitive parse with backwards-compatible aliases.
     * Returns null if unknown.
     */
    public static RaffleEffectId fromString(String raw) {
        if (raw == null) return null;

        String key = raw.trim().toUpperCase(Locale.ROOT);
        if (key.isEmpty()) return null;

        // ---- Backwards compatibility aliases ----
        switch (key) {
            // Health boost naming confusion
            case "HEALTH_BOOST", "HEALTHBOOST" -> key = "VITALITY";

            // Night vision variants
            case "NIGHT_VISION", "NIGHTVISION", "SIGHT_BEYOND" -> key = "SIGHTBEYOND";

            // Conduit naming variants
            case "CONDUIT", "CONDUIT_POWER", "CONDUITPOWER" -> key = "TIDEBOUND";

            // Dolphin naming variants
            case "DOLPHINS_GRACE", "DOLPHIN_GRACE", "DOLPHINGRACE" -> key = "OCEAN_GRACE";

            // Fire resistance variants
            case "FIRE_RES", "FIRERES", "FIRE_RESIST", "FIRERESIST", "FIRE_RESISTANCE" -> key = "EMBER_WARD";

            // Wall climb aliases
            case "WALL_CLIMB", "WALLCLIMB", "GECKO" -> key = "GECKO_GRIP";
        }

        try {
            return RaffleEffectId.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
