package com.orangeslices.additionalbosses.kits;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class SharpeningKits {

    private SharpeningKits() {}

    /**
     * TRIAL PHASE (Sharpening-only):
     * - No crafting recipes
     * - No essences
     * - Drop-only via BossDropListener
     *
     * Safe no-op in case older code still calls it.
     */
    public static void registerRecipes(AdditionalBossesPlugin plugin) {
        // Intentionally empty
    }

    /**
     * Creates the Sharpening Kit token.
     *
     * Tags:
     * - token_type = "SHARPENING"
     * - token_level = 1|2
     * - sharpen_kit_level (legacy) = 1|2
     */
    public static ItemStack makeSharpeningKit(AdditionalBossesPlugin plugin, int level) {
        level = Math.min(2, Math.max(1, level));

        ItemStack item = new ItemStack(Material.AMETHYST_SHARD, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(level == 1
                ? "§fSharpening Kit I"
                : "§bSharpening Kit II"
        );

        meta.setLore(List.of(
                "§7Upgrade Token",
                level == 1
                        ? "§aEffect: +1 bonus damage on hit"
                        : "§aEffect: +2 bonus damage on hit",
                "§7Applies to: Swords, Axes, Spears, Tridents",
                "§7Use: Apply to a weapon",
                "§8Does not overwrite enchants"
        ));

        NamespacedKey tokenTypeKey = new NamespacedKey(plugin, "token_type");
        NamespacedKey tokenLevelKey = new NamespacedKey(plugin, "token_level");

        meta.getPersistentDataContainer().set(
                tokenTypeKey,
                PersistentDataType.STRING,
                "SHARPENING"
        );
        meta.getPersistentDataContainer().set(
                tokenLevelKey,
                PersistentDataType.INTEGER,
                level
        );

        // Legacy compatibility tag
        NamespacedKey legacyKey = new NamespacedKey(plugin, "sharpen_kit_level");
        meta.getPersistentDataContainer().set(
                legacyKey,
                PersistentDataType.INTEGER,
                level
        );

        item.setItemMeta(meta);
        return item;
    }
}
