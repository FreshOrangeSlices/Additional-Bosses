package com.orangeslices.additionalbosses.raffle.effects;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RafflePotionTable {

    private RafflePotionTable() {}

    public enum SlotRule {
        ANY_ARMOR,
        HELMET_ONLY,
        CHESTPLATE_ONLY,
        LEGGINGS_ONLY,
        BOOTS_ONLY
    }

    public static final class Entry {
        public final RaffleEffectId id;
        public final PotionEffectType potion;
        public final SlotRule slotRule;
        public final int durationTicks;
        public final boolean canLevel;

        public Entry(
                RaffleEffectId id,
                PotionEffectType potion,
                SlotRule slotRule,
                int durationTicks,
                boolean canLevel
        ) {
            this.id = id;
            this.potion = potion;
            this.slotRule = slotRule;
            this.durationTicks = durationTicks;
            this.canLevel = canLevel;
        }
    }

    private static final List<Entry> ENTRIES;

    static {
        List<Entry> list = new ArrayList<>();

        // -------------------------
        // GOOD (levelable)
        // -------------------------

        // VITALITY is the ONLY "ANY_ARMOR" effect
        list.add(new Entry(
                RaffleEffectId.VITALITY,
                PotionEffectType.HEALTH_BOOST,
                SlotRule.ANY_ARMOR,
                120,
                true
        ));

        list.add(new Entry(
                RaffleEffectId.IRON_WILL,
                PotionEffectType.RESISTANCE,
                SlotRule.CHESTPLATE_ONLY,
                80,
                true
        ));

        list.add(new Entry(
                RaffleEffectId.BLOOD_MENDING,
                PotionEffectType.REGENERATION,
                SlotRule.LEGGINGS_ONLY,
                60,
                true
        ));

        list.add(new Entry(
                RaffleEffectId.SKYBOUND,
                PotionEffectType.JUMP_BOOST,
                SlotRule.BOOTS_ONLY,
                120,
                true
        ));

        // -------------------------
        // GOOD (flat / non-leveling)
        // -------------------------

        list.add(new Entry(
                RaffleEffectId.EMBER_WARD,
                PotionEffectType.FIRE_RESISTANCE,
                SlotRule.CHESTPLATE_ONLY,
                120,
                false
        ));

        // Luck is LEGGINGS-only (intentional)
        list.add(new Entry(
                RaffleEffectId.FORTUNE,
                PotionEffectType.LUCK,
                SlotRule.LEGGINGS_ONLY,
                200,
                false
        ));

        list.add(new Entry(
                RaffleEffectId.TIDEBOUND,
                PotionEffectType.CONDUIT_POWER,
                SlotRule.HELMET_ONLY,
                120,
                false
        ));

        list.add(new Entry(
                RaffleEffectId.OCEAN_GRACE,
                PotionEffectType.DOLPHINS_GRACE,
                SlotRule.BOOTS_ONLY,
                80,
                false
        ));

        list.add(new Entry(
                RaffleEffectId.VILLAGER_FAVOR,
                PotionEffectType.HERO_OF_THE_VILLAGE,
                SlotRule.CHESTPLATE_ONLY,
                200,
                false
        ));

        // Night Vision (Helmet-only)
        list.add(new Entry(
                RaffleEffectId.SIGHTBEYOND,
                PotionEffectType.NIGHT_VISION,
                SlotRule.HELMET_ONLY,
                400,
                false
        ));

        ENTRIES = Collections.unmodifiableList(list);
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }
}
