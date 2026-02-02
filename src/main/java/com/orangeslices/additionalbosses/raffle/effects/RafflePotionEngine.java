package com.orangeslices.additionalbosses.raffle.effects;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Raffle potion-style effects refresher.
 *
 * Mirrors the stable Potion Add-On approach:
 * - periodic refresh
 * - applyIfBetter()
 * - highest level across armor
 *
 * This engine is AUTHORITATIVE for raffle-applied potion effects:
 * - It will re-apply every refresh to keep them hidden (no HUD/inventory icons).
 * - It will not downgrade stronger external effects.
 *
 * Effect mappings live in RafflePotionTable (expandable).
 */
public final class RafflePotionEngine {

    private final AdditionalBossesPlugin plugin;
    private BukkitTask task;

    // Marks that a specific PotionEffectType is being managed by this engine
    private final NamespacedKey managedKey;

    public RafflePotionEngine(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
        this.managedKey = new NamespacedKey(plugin, "raffle_potion_managed"); // stores CSV list
    }

    public void start() {
        stop();

        // start after 1s, refresh every 2s
        task = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(this::refreshPlayer),
                20L,
                40L
        );
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;

        // Optional cleanup: remove only effects we previously marked as managed
        // (prevents lingering hidden effects across reloads/shutdowns)
        try {
            plugin.getServer().getOnlinePlayers().forEach(this::clearManagedIfPresent);
        } catch (Throwable ignored) {}
    }

    private void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        // Highest across all armor (for ANY_ARMOR effects and quick lookup)
        Map<RaffleEffectId, Integer> highest = new HashMap<>();
        mergeArmor(highest, player.getInventory().getHelmet());
        mergeArmor(highest, player.getInventory().getChestplate());
        mergeArmor(highest, player.getInventory().getLeggings());
        mergeArmor(highest, player.getInventory().getBoots());

        for (RafflePotionTable.Entry entry : RafflePotionTable.entries()) {
            if (entry == null || entry.id == null || entry.potion == null) continue;

            int level = resolveLevelForSlotRule(player, entry, highest);
            if (level <= 0) continue;

            // Enforce non-leveling effects
            if (!entry.canLevel) {
                level = 1;
            }

            applyAuthoritative(player, entry.potion, level, entry.durationTicks, entry.canLevel);
        }
    }

    private int resolveLevelForSlotRule(Player player, RafflePotionTable.Entry entry, Map<RaffleEffectId, Integer> highest) {
        return switch (entry.slotRule) {
            case ANY_ARMOR -> highest.getOrDefault(entry.id, 0);
            case HELMET_ONLY -> readLevelFromPiece(player.getInventory().getHelmet(), entry.id);
            case CHESTPLATE_ONLY -> readLevelFromPiece(player.getInventory().getChestplate(), entry.id);
            case LEGGINGS_ONLY -> readLevelFromPiece(player.getInventory().getLeggings(), entry.id);
            case BOOTS_ONLY -> readLevelFromPiece(player.getInventory().getBoots(), entry.id);
        };
    }

    private int readLevelFromPiece(ItemStack armor, RaffleEffectId id) {
        if (armor == null) return 0;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        return map.getOrDefault(id, 0);
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor) {
        if (armor == null) return;
        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);
        RaffleEffectReader.mergeHighest(into, map);
    }

    /**
     * AUTHORITATIVE application:
     * - Always re-applies our hidden version to prevent HUD/inventory showing.
     * - Never downgrades stronger external effects.
     * - If an external effect exists with same amplifier but longer duration, we still overwrite to hide.
     */
    private void applyAuthoritative(Player player, PotionEffectType type, int level, int duration, boolean canLevel) {
        if (type == null || level <= 0) return;

        int amplifier = canLevel ? Math.max(0, level - 1) : 0;

        PotionEffect current = player.getPotionEffect(type);
        if (current != null) {
            // Never downgrade stronger effects from other sources
            if (current.getAmplifier() > amplifier) return;
        }

        // Apply hidden visuals (ambient=true, particles=false, icon=false)
        player.addPotionEffect(new PotionEffect(type, duration, amplifier, true, false, false));

        // Mark this type as managed so we can reason about cleanup later if needed
        markManaged(player, type);
    }

    private void markManaged(Player player, PotionEffectType type) {
        try {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            String existing = pdc.get(managedKey, PersistentDataType.STRING);
            String token = type.getKey().toString();

            if (existing == null || existing.isBlank()) {
                pdc.set(managedKey, PersistentDataType.STRING, token);
                return;
            }

            // If already tracked, do nothing
            if (containsToken(existing, token)) return;

            pdc.set(managedKey, PersistentDataType.STRING, existing + "," + token);
        } catch (Throwable ignored) {
            // Tracking is optional; effect application still works.
        }
    }

    private boolean containsToken(String csv, String token) {
        for (String part : csv.split(",")) {
            if (part.trim().equals(token)) return true;
        }
        return false;
    }

    private void clearManagedIfPresent(Player player) {
        if (player == null) return;

        try {
            PersistentDataContainer pdc = player.getPersistentDataContainer();
            String csv = pdc.get(managedKey, PersistentDataType.STRING);
            if (csv == null || csv.isBlank()) return;

            for (String part : csv.split(",")) {
                String key = part.trim();
                if (key.isEmpty()) continue;

                PotionEffectType type = PotionEffectType.getByKey(org.bukkit.NamespacedKey.fromString(key));
                if (type != null) {
                    player.removePotionEffect(type);
                }
            }

            // Clear the marker
            pdc.remove(managedKey);
        } catch (Throwable ignored) {}
    }
}
