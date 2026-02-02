package com.orangeslices.additionalbosses.raffle;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Creates and validates Raffle Tokens.
 *
 * Raffle Tokens:
 * - Apply to ARMOR only
 * - Single-use
 * - Non-stackable
 * - Separate from kits
 */
public final class RaffleTokenFactory {

    private static NamespacedKey TOKEN_FLAG;
    private static NamespacedKey TOKEN_UUID;

    private RaffleTokenFactory() {}

    /**
     * Must be called once in plugin onEnable().
     */
    public static void init(AdditionalBossesPlugin plugin) {
        TOKEN_FLAG = new NamespacedKey(plugin, "raffle_token");
        TOKEN_UUID = new NamespacedKey(plugin, "raffle_token_uuid");
    }

    private static void validateInit() {
        if (TOKEN_FLAG == null || TOKEN_UUID == null) {
            throw new IllegalStateException(
                    "RaffleTokenFactory not initialized. Call RaffleTokenFactory.init(plugin) in onEnable()."
            );
        }
    }

    /**
     * Creates a new raffle token item.
     */
    public static ItemStack createToken() {
        validateInit();

        ItemStack item = new ItemStack(Material.PAPER, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatColor.LIGHT_PURPLE + "Raffle Token");

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Apply to armor to add a");
        lore.add(ChatColor.GRAY + "mysterious passive effect.");
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "Sneak + Right-Click");
        meta.setLore(lore);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Mark as raffle token
        meta.getPersistentDataContainer().set(
                TOKEN_FLAG,
                PersistentDataType.BYTE,
                (byte) 1
        );

        // UUID makes it non-stackable
        meta.getPersistentDataContainer().set(
                TOKEN_UUID,
                PersistentDataType.STRING,
                UUID.randomUUID().toString()
        );

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Checks if an item is a raffle token.
     */
    public static boolean isToken(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;

        validateInit();

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        Byte flag = meta.getPersistentDataContainer().get(
                TOKEN_FLAG,
                PersistentDataType.BYTE
        );

        return flag != null && flag == (byte) 1;
    }
}
