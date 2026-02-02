package com.orangeslices.additionalbosses.raffle;

import com.orangeslices.additionalbosses.raffle.effects.RaffleEffectReader;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

/**
 * Builds lore for raffle armor based on PDC.
 *
 * TEMP TESTING NOTE:
 * - We ARE showing curses in lore right now so you can test.
 * - Later we can hide curses again if you want.
 */
public final class RaffleLoreUtil {

    private RaffleLoreUtil() {}

    /**
     * Backwards-compat: older code calls updateLore().
     * Keep this so we don't have to touch listeners.
     */
    public static void updateLore(ItemStack armor, int maxSlots) {
        applyLore(armor, maxSlots);
    }

    public static void applyLore(ItemStack armor, int maxSlots) {
        if (armor == null) return;

        ItemMeta meta = armor.getItemMeta();
        if (meta == null) return;

        Map<RaffleEffectId, Integer> effects = RaffleEffectReader.readFromItem(armor);

        // Slots used comes from SLOT_COUNT (token applications), not effects.size()
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer storedSlots = pdc.get(RaffleKeys.SLOT_COUNT, PersistentDataType.INTEGER);
        int usedSlots = (storedSlots != null ? storedSlots : effects.size());

        // Clamp safety (just in case old items got weird)
        if (usedSlots < effects.size()) usedSlots = effects.size();
        if (maxSlots < 0) maxSlots = 0;

        List<String> lore = new ArrayList<>();
        lore.add(color("&8\u25A0 &7Add-On Slots: &f" + usedSlots + "&7/&f" + maxSlots));

        if (effects.isEmpty()) {
            lore.add(color("&8\u25A0 &7No lingering influence."));
        } else {
            lore.add(color("&8\u25A0 &7Imprinted Effects:"));

            // Sort: GOOD first then CURSE, then stable by name
            List<Map.Entry<RaffleEffectId, Integer>> sorted = new ArrayList<>(effects.entrySet());
            sorted.sort((a, b) -> {
                boolean ac = a.getKey().isCurse();
                boolean bc = b.getKey().isCurse();
                if (ac != bc) return ac ? 1 : -1; // curses last
                return a.getKey().name().compareTo(b.getKey().name());
            });

            for (Map.Entry<RaffleEffectId, Integer> e : sorted) {
                RaffleEffectId id = e.getKey();
                int level = e.getValue();

                String name = displayName(id);
                String line;

                if (id.isCurse()) {
                    // Curse: no levels shown (but visible for testing)
                    line = "&8\u25A0 &5" + name;
                } else {
                    // Good: show level only if it can level
                    if (id.canLevel()) {
                        line = "&8\u25A0 &d" + name + " &7(" + roman(Math.max(1, level)) + ")";
                    } else {
                        line = "&8\u25A0 &d" + name;
                    }
                }

                lore.add(color(line));
            }
        }

        lore.add(color("&8\u25A0 &8&o\"The armor remembers...\""));

        meta.setLore(lore);
        armor.setItemMeta(meta);
    }

    /**
     * Explicit names for all current IDs.
     * Fallback stays for future IDs.
     */
    private static String displayName(RaffleEffectId id) {
        return switch (id) {
            // -------------------------
            // GOOD (levelable)
            // -------------------------
            case VITALITY -> "Vitality";
            case IRON_WILL -> "Iron Will";
            case BLOOD_MENDING -> "Blood Mending";
            case SKYBOUND -> "Skybound";

            // -------------------------
            // GOOD (flat)
            // -------------------------
            case EMBER_WARD -> "Ember Ward";
            case SIGHTBEYOND -> "Sight Beyond";
            case TIDEBOUND -> "Tidebound";
            case OCEAN_GRACE -> "Ocean Grace";
            case FORTUNE -> "Fortune";
            case VILLAGER_FAVOR -> "Villager's Favor";
            case GECKO_GRIP -> "Gecko Grip";

            // -------------------------
            // CURSES (explicit)
            // -------------------------
            case DREAD -> "Dread";
            case TERROR -> "Terror";
            case MISSTEP -> "Misstep";
            case UNEASE -> "Unease";
            case ECHOES -> "Echoes";
            case DISARRAY -> "Disarray";
            case ON_ALL_FOURS -> "On All Fours";
            case MATADOR -> "Matador";
            case MOTHER_HEN -> "Mother Hen";
            case IMPOSTER -> "Imposter";
            case REDUCTION -> "Reduction";

            // Anything new safely falls back
            default -> titleCase(id.name());
        };
    }

    private static String titleCase(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            String p = parts[i];
            if (p.isEmpty()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
            if (i < parts.length - 1) sb.append(' ');
        }
        return sb.toString();
    }

    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
