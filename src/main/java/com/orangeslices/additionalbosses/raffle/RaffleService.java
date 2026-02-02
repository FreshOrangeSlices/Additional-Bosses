package com.orangeslices.additionalbosses.raffle;

import com.orangeslices.additionalbosses.raffle.effects.RafflePotionTable;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Raffle rules:
 * - Armor only
 * - Max 3 slots (default)
 * - Unified equal pool
 * - Max 1 curse per item
 * - After curse → GOOD-only rolls
 * - GOOD duplicates level up (only if canLevel)
 * - Curses never level
 *
 * Slot Authority:
 * - When applying to a specific armor piece, ONLY roll effects compatible with that armor slot.
 * - GOOD effects are slot-locked via RafflePotionTable.
 * - CURSES are slot-locked here (roll-time) to prevent dead/incorrect curse rolls.
 */
public final class RaffleService {

    public static final int DEFAULT_MAX_SLOTS = 3;
    private static final Random RNG = new Random();

    private final RafflePool pool;

    public RaffleService(RafflePool pool) {
        this.pool = pool;
    }

    public ApplyResult applyToArmor(ItemStack armor, int maxSlots) {
        if (armor == null || armor.getType() == Material.AIR) {
            return ApplyResult.fail("No armor item.");
        }
        if (!isArmor(armor.getType())) {
            return ApplyResult.fail("Item is not armor.");
        }

        // Defensive init check
        RaffleKeys.validateInit();

        if (maxSlots <= 0) maxSlots = DEFAULT_MAX_SLOTS;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) {
            return ApplyResult.fail("Armor has no meta.");
        }

        EquipmentSlot targetSlot = armorSlot(armor.getType());
        if (targetSlot == null) {
            return ApplyResult.fail("Could not determine armor slot.");
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        Map<RaffleEffectId, Integer> effects = readEffects(pdc);
        int slotsUsed = readSlotsUsed(pdc, effects.size());
        boolean hasCurse = hasCurse(effects);

        if (slotsUsed >= maxSlots) {
            return ApplyResult.fail("Max add-ons reached.");
        }

        if (pool.isEmpty()) {
            return ApplyResult.fail("Raffle pool is empty.");
        }

        RaffleEffectId rolled = rollForSlot(hasCurse, targetSlot);
        if (rolled == null) {
            return ApplyResult.fail("No valid effects to roll for this armor slot.");
        }

        int newLevel;

        if (rolled.isCurse()) {
            effects.put(rolled, 1);
            newLevel = 1;
        } else {
            if (!rolled.canLevel()) {
                boolean alreadyHas = effects.containsKey(rolled);
                effects.put(rolled, 1);
                newLevel = 1;

                if (alreadyHas) {
                    writeEffects(pdc, effects);
                    armor.setItemMeta(meta);
                    return ApplyResult.success(rolled, newLevel, slotsUsed, maxSlots);
                }
            } else {
                int current = effects.getOrDefault(rolled, 0);
                newLevel = current + 1;
                effects.put(rolled, newLevel);
            }
        }

        slotsUsed++;

        writeEffects(pdc, effects);
        pdc.set(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER, slotsUsed);
        armor.setItemMeta(meta);

        return ApplyResult.success(rolled, newLevel, slotsUsed, maxSlots);
    }

    /* =========================
       Slot-authoritative roll
       ========================= */

    private RaffleEffectId rollForSlot(boolean hasCurse, EquipmentSlot targetSlot) {
        List<RaffleEffectId> candidates = new ArrayList<>();

        for (RaffleEffectId id : pool.snapshot()) {
            if (id == null) continue;

            if (id == RaffleEffectId.UNEASE || id == RaffleEffectId.MISSTEP) continue;
            if (hasCurse && id.isCurse()) continue;

            if (id.isCurse()) {
                if (isCurseCompatibleWithSlot(id, targetSlot)) {
                    candidates.add(id);
                }
                continue;
            }

            if (isGoodEffectCompatibleWithSlot(id, targetSlot)) {
                candidates.add(id);
            }
        }

        if (candidates.isEmpty()) return null;
        return candidates.get(RNG.nextInt(candidates.size()));
    }

    private boolean isCurseCompatibleWithSlot(RaffleEffectId id, EquipmentSlot slot) {
        if (id == null || slot == null) return false;

        return switch (id) {
            case TERROR, REDUCTION -> slot == EquipmentSlot.HEAD;
            case DREAD -> slot == EquipmentSlot.CHEST;
            case MOTHER_HEN -> slot == EquipmentSlot.LEGS;
            case MATADOR -> slot == EquipmentSlot.FEET;
            default -> false;
        };
    }

    private boolean isGoodEffectCompatibleWithSlot(RaffleEffectId id, EquipmentSlot slot) {
        if (id == null || slot == null) return false;

        for (RafflePotionTable.Entry e : RafflePotionTable.entries()) {
            if (e.id == id) {
                return switch (e.slotRule) {
                    case ANY_ARMOR -> true;
                    case HELMET_ONLY -> slot == EquipmentSlot.HEAD;
                    case CHESTPLATE_ONLY -> slot == EquipmentSlot.CHEST;
                    case LEGGINGS_ONLY -> slot == EquipmentSlot.LEGS;
                    case BOOTS_ONLY -> slot == EquipmentSlot.FEET;
                };
            }
        }

        return false;
    }

    /* =========================
       Helpers
       ========================= */

    private static EquipmentSlot armorSlot(Material mat) {
        if (mat == null) return null;
        String n = mat.name();
        if (n.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return null;
    }

    private static boolean hasCurse(Map<RaffleEffectId, Integer> effects) {
        for (RaffleEffectId id : effects.keySet()) {
            if (id != null && id.isCurse()) return true;
        }
        return false;
    }

    private static int readSlotsUsed(PersistentDataContainer pdc, int fallback) {
        Integer stored = pdc.get(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER);
        return stored != null ? stored : fallback;
    }

    private static Map<RaffleEffectId, Integer> readEffects(PersistentDataContainer pdc) {
        String raw = pdc.get(RaffleKeys.EFFECTS, PersistentDataType.STRING);
        Map<RaffleEffectId, Integer> out = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return out;

        for (String part : raw.split(",")) {
            String[] kv = part.trim().split(":");
            if (kv.length != 2) continue;

            RaffleEffectId id = RaffleEffectId.fromString(kv[0]);
            if (id == null) continue;

            int lvl;
            try {
                lvl = Integer.parseInt(kv[1]);
            } catch (NumberFormatException e) {
                lvl = 1;
            }

            if (id.isCurse() || !id.canLevel()) lvl = 1;
            out.put(id, Math.max(1, lvl));
        }

        return out;
    }

    private static void writeEffects(PersistentDataContainer pdc, Map<RaffleEffectId, Integer> effects) {
        if (effects == null || effects.isEmpty()) {
            pdc.remove(RaffleKeys.EFFECTS);
            return;
        }

        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (var e : effects.entrySet()) {
            if (!first) sb.append(",");
            sb.append(e.getKey().name()).append(":").append(Math.max(1, e.getValue()));
            first = false;
        }

        pdc.set(RaffleKeys.EFFECTS, PersistentDataType.STRING, sb.toString());
    }

    private static boolean isArmor(Material m) {
        if (m == null) return false;
        String n = m.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS");
    }

    public static final class ApplyResult {
        public final boolean success;
        public final String message;
        public final RaffleEffectId effect;
        public final int level, slotsUsed, maxSlots;

        private ApplyResult(boolean s, String m, RaffleEffectId e, int l, int u, int max) {
            success = s;
            message = m;
            effect = e;
            level = l;
            slotsUsed = u;
            maxSlots = max;
        }

        public static ApplyResult success(RaffleEffectId id, int lvl, int used, int max) {
            return new ApplyResult(true, "Applied " + id.name(), id, lvl, used, max);
        }

        public static ApplyResult fail(String msg) {
            return new ApplyResult(false, msg, null, 0, 0, 0);
        }
    }
}
