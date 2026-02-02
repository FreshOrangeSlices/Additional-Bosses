package com.orangeslices.additionalbosses.kits.listeners;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class KitApplyListener implements Listener {

    private final AdditionalBossesPlugin plugin;

    // Token identity keys (on the KIT item)
    private final NamespacedKey tokenTypeKey;
    private final NamespacedKey tokenLevelKey;

    // Upgrade keys (on the TARGET item)
    private final NamespacedKey sharpenLevelKey;
    private final NamespacedKey markLevelKey;
    private final NamespacedKey hasteLevelKey;
    private final NamespacedKey strengthLevelKey;

    // Legacy kit tags (in case older tokens exist)
    private final NamespacedKey legacySharpenKitKey;
    private final NamespacedKey legacyMarkKitKey;

    public KitApplyListener(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;

        this.tokenTypeKey = new NamespacedKey(plugin, "token_type");
        this.tokenLevelKey = new NamespacedKey(plugin, "token_level");

        this.sharpenLevelKey = new NamespacedKey(plugin, "sharpen_level");
        this.markLevelKey = new NamespacedKey(plugin, "mark_level");
        this.hasteLevelKey = new NamespacedKey(plugin, "haste_level");
        this.strengthLevelKey = new NamespacedKey(plugin, "strength_level");

        this.legacySharpenKitKey = new NamespacedKey(plugin, "sharpen_kit_level");
        this.legacyMarkKitKey = new NamespacedKey(plugin, "mark_kit_level");
    }

    @EventHandler
    public void onApplyKit(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        // Bedrock-safe input: sneak + right click
        if (!player.isSneaking()) return;
        if (!event.getAction().isRightClick()) return;

        // Prevent double-firing (main hand + offhand)
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        HandTokenTarget tt = resolveTokenAndTarget(main, off);
        if (tt == null) return;

        boolean applied = applyTokenToTarget(tt.tokenType, tt.tokenLevel, tt.target);
        if (!applied) return;

        consumeOneFromHand(player, tt.tokenHand);
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.7f, 1.4f);
        event.setCancelled(true);
    }

    /* =========================
       Apply kit -> write PDC on target item
       ========================= */

    private boolean applyTokenToTarget(String tokenTypeRaw, int tokenLevel, ItemStack target) {
        if (target == null || target.getType() == Material.AIR) return false;

        String tokenType = tokenTypeRaw == null ? "" : tokenTypeRaw.trim().toUpperCase();
        tokenLevel = clampLevel(tokenLevel);

        return switch (tokenType) {

            // Weapon upgrades
            case "SHARPENING" -> {
                if (!isWeapon(target)) yield false;
                yield applyLevelToItem(target, tokenLevel, sharpenLevelKey, "§7Sharpened: ", "I", "II");
            }
            case "MARK" -> {
                if (!isWeapon(target)) yield false;
                yield applyLevelToItem(target, tokenLevel, markLevelKey, "§7Mark: ", "I", "II");
            }

            // Potion-style kits (held item)
            case "HASTE" -> {
                if (!isToolOrWeapon(target)) yield false;
                yield applyLevelToItem(target, tokenLevel, hasteLevelKey, "§7Haste: ", "I", "II");
            }
            case "STRENGTH" -> {
                if (!isWeapon(target)) yield false;
                yield applyLevelToItem(target, tokenLevel, strengthLevelKey, "§7Strength: ", "I", "II");
            }

            // Everything else is intentionally unsupported
            default -> false;
        };
    }

    /**
     * Writes/updates PDC level, and updates/overwrites a single lore line
     * without nuking the rest of the lore.
     */
    private boolean applyLevelToItem(ItemStack item, int level, NamespacedKey itemKey,
                                     String lorePrefix, String roman1, String roman2) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        int current = meta.getPersistentDataContainer().getOrDefault(itemKey, PersistentDataType.INTEGER, 0);
        int next = clampLevel(Math.max(current, level));

        // No upgrade to apply
        if (next == current) return false;

        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.INTEGER, next);

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore.removeIf(line -> line != null && line.startsWith(lorePrefix));
        lore.add(lorePrefix + (next == 1 ? roman1 : roman2));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return true;
    }

    private int clampLevel(int lvl) {
        if (lvl < 1) return 1;
        if (lvl > 2) return 2;
        return lvl;
    }

    /* =========================
       Resolve token + target
       ========================= */

    private HandTokenTarget resolveTokenAndTarget(ItemStack main, ItemStack off) {
        TokenInfo mainToken = getTokenInfo(main);
        TokenInfo offToken = getTokenInfo(off);

        if (mainToken != null && off != null && off.getType() != Material.AIR) {
            return new HandTokenTarget(EquipmentSlot.HAND, mainToken.type, mainToken.level, off);
        }
        if (offToken != null && main != null && main.getType() != Material.AIR) {
            return new HandTokenTarget(EquipmentSlot.OFF_HAND, offToken.type, offToken.level, main);
        }
        return null;
    }

    /**
     * Recognize kit tokens:
     * - New: token_type + token_level
     * - Legacy: sharpen_kit_level / mark_kit_level
     */
    private TokenInfo getTokenInfo(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        var pdc = meta.getPersistentDataContainer();

        String type = pdc.get(tokenTypeKey, PersistentDataType.STRING);
        Integer lvl = pdc.get(tokenLevelKey, PersistentDataType.INTEGER);
        if (type != null && lvl != null && lvl > 0) {
            return new TokenInfo(type, lvl);
        }

        Integer s = pdc.get(legacySharpenKitKey, PersistentDataType.INTEGER);
        if (s != null && s > 0) return new TokenInfo("SHARPENING", s);

        Integer m = pdc.get(legacyMarkKitKey, PersistentDataType.INTEGER);
        if (m != null && m > 0) return new TokenInfo("MARK", m);

        return null;
    }

    /* =========================
       Target type checks
       ========================= */

    private boolean isWeapon(ItemStack item) {
        Material m = item.getType();
        String name = m.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_SPEAR")
                || m == Material.TRIDENT;
    }

    private boolean isToolOrWeapon(ItemStack item) {
        Material m = item.getType();
        String name = m.name();

        boolean tool = name.endsWith("_PICKAXE")
                || name.endsWith("_AXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE");

        return tool || isWeapon(item);
    }

    /* =========================
       Consume token
       ========================= */

    private void consumeOneFromHand(Player player, EquipmentSlot hand) {
        ItemStack item = (hand == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (item == null || item.getType() == Material.AIR) return;

        int amt = item.getAmount();
        if (amt <= 1) {
            if (hand == EquipmentSlot.HAND) player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            else player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
        } else {
            item.setAmount(amt - 1);
        }
    }

    /* =========================
       Small structs
       ========================= */

    private record TokenInfo(String type, int level) {}

    private static final class HandTokenTarget {
        final EquipmentSlot tokenHand;
        final String tokenType;
        final int tokenLevel;
        final ItemStack target;

        HandTokenTarget(EquipmentSlot tokenHand, String tokenType, int tokenLevel, ItemStack target) {
            this.tokenHand = tokenHand;
            this.tokenType = tokenType;
            this.tokenLevel = tokenLevel;
            this.target = target;
        }
    }
}
