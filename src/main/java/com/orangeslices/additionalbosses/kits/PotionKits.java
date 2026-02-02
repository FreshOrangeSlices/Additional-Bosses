package com.orangeslices.additionalbosses.kits;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class PotionKits {

    private PotionKits() {}

    /**
     * Trial phase potion kits.
     * ONLY combat-relevant effects are allowed.
     */
    public enum PotionTokenType {
        HASTE,
        STRENGTH
    }

    public static ItemStack makePotionKit(
            AdditionalBossesPlugin plugin,
            PotionTokenType type,
            int level
    ) {
        level = clampLevel(level);

        ItemStack item = new ItemStack(materialFor(type), 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(displayNameFor(type, level));
        meta.setLore(loreFor(type, level));

        NamespacedKey tokenTypeKey = new NamespacedKey(plugin, "token_type");
        NamespacedKey tokenLevelKey = new NamespacedKey(plugin, "token_level");

        meta.getPersistentDataContainer().set(
                tokenTypeKey,
                PersistentDataType.STRING,
                type.name()
        );
        meta.getPersistentDataContainer().set(
                tokenLevelKey,
                PersistentDataType.INTEGER,
                level
        );

        item.setItemMeta(meta);
        return item;
    }

    /* =========================
       Definitions
       ========================= */

    private static Material materialFor(PotionTokenType type) {
        return switch (type) {
            case HASTE -> Material.QUARTZ;
            case STRENGTH -> Material.BLAZE_POWDER;
        };
    }

    private static String displayNameFor(PotionTokenType type, int level) {
        String roman = (level == 1) ? "I" : "II";
        return switch (type) {
            case HASTE -> "§eHaste Kit " + roman;
            case STRENGTH -> "§cStrength Kit " + roman;
        };
    }

    private static List<String> loreFor(PotionTokenType type, int level) {
        String roman = (level == 1) ? "I" : "II";

        return switch (type) {
            case HASTE -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Haste " + roman + " while holding",
                    "§7Applies to: Tools / Weapons",
                    "§7Use: Apply to a tool or weapon",
                    "§8(No particles • Icon shown)"
            );
            case STRENGTH -> List.of(
                    "§7Upgrade Token",
                    "§aEffect: Strength " + roman + " while holding",
                    "§7Applies to: Weapons",
                    "§7Use: Apply to a weapon",
                    "§8(No particles • Icon shown)"
            );
        };
    }

    private static int clampLevel(int lvl) {
        if (lvl < 1) return 1;
        if (lvl > 2) return 2;
        return lvl;
    }
}
