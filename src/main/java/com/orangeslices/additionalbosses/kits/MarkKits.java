package com.orangeslices.additionalbosses.kits;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class MarkKits {

    private MarkKits() {}

    public static ItemStack makeMarkKit(AdditionalBossesPlugin plugin, int level) {
        level = Math.min(2, Math.max(1, level));

        ItemStack item = new ItemStack(Material.AMETHYST_CLUSTER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(level == 1 ? "§dMark Kit I" : "§5Mark Kit II");

        meta.setLore(List.of(
                "§7Upgrade Token",
                "§aEffect: Chance on hit to Mark targets (6s)",
                level == 1
                        ? "§aMarked target takes x1.5 damage"
                        : "§aMarked target takes x2.0 damage",
                "§eMarked targets Glow",
                "§7Applies to: Weapons",
                "§7Use: Apply to a weapon",
                "§8One marked target at a time"
        ));

        NamespacedKey tokenTypeKey = new NamespacedKey(plugin, "token_type");
        NamespacedKey tokenLevelKey = new NamespacedKey(plugin, "token_level");

        meta.getPersistentDataContainer().set(tokenTypeKey, PersistentDataType.STRING, "MARK");
        meta.getPersistentDataContainer().set(tokenLevelKey, PersistentDataType.INTEGER, level);

        // Optional legacy tag (safe to keep)
        NamespacedKey legacyKey = new NamespacedKey(plugin, "mark_kit_level");
        meta.getPersistentDataContainer().set(legacyKey, PersistentDataType.INTEGER, level);

        item.setItemMeta(meta);
        return item;
    }
}
