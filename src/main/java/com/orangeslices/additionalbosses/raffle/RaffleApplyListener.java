package com.orangeslices.additionalbosses.raffle;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sneak + Right-Click to apply a Raffle Token to armor.
 *
 * All validation logic lives in RaffleService.
 */
public final class RaffleApplyListener implements Listener {

    private final AdditionalBossesPlugin plugin;
    private final ConcurrentHashMap<UUID, Long> lastUseMs = new ConcurrentHashMap<>();

    public RaffleApplyListener(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onApply(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        if (!player.isSneaking()) return;

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        boolean mainIsToken = RaffleTokenFactory.isToken(main);
        boolean offIsToken = RaffleTokenFactory.isToken(off);

        if (!mainIsToken && !offIsToken) return;

        FileConfiguration cfg = plugin.getConfig();

        boolean requireTokenMainhand = cfg.getBoolean("raffle.require_token_mainhand", true);
        boolean requireArmorOffhand = cfg.getBoolean("raffle.require_armor_offhand", true);
        long cooldownMs = cfg.getLong("raffle.cooldown_ms", 250L);

        event.setCancelled(true);

        // Cooldown guard
        if (cooldownMs > 0) {
            long now = System.currentTimeMillis();
            long last = lastUseMs.getOrDefault(player.getUniqueId(), 0L);
            if (now - last < cooldownMs) return;
            lastUseMs.put(player.getUniqueId(), now);
        }

        ItemStack token;
        ItemStack armor;
        EquipmentSlot tokenSlot;

        if (requireTokenMainhand) {
            if (!mainIsToken) {
                fail(player, cfg, "Hold the token in your main hand.");
                return;
            }
            token = main;
            tokenSlot = EquipmentSlot.HAND;

            armor = off;
            if (requireArmorOffhand) {
                // Explicit: armor must be offhand
                // (if main hand token is required, this is already true structurally)
            }
        } else {
            if (mainIsToken && offIsToken) {
                fail(player, cfg, "Hold armor in one hand and the token in the other.");
                return;
            }

            if (mainIsToken) {
                token = main;
                tokenSlot = EquipmentSlot.HAND;
                armor = off;
            } else {
                token = off;
                tokenSlot = EquipmentSlot.OFF_HAND;
                armor = main;
            }

            if (requireArmorOffhand && tokenSlot == EquipmentSlot.OFF_HAND) {
                // If armor must be offhand but token is offhand, fail
                fail(player, cfg, "Hold armor in your offhand.");
                return;
            }
        }

        if (armor == null || armor.getType() == Material.AIR) {
            fail(player, cfg, null);
            return;
        }

        if (!isArmor(armor.getType())) {
            fail(player, cfg, null);
            return;
        }

        int maxSlots = plugin.raffleMaxSlotsPerArmor();
        RaffleService.ApplyResult result =
                plugin.raffleService().applyToArmor(armor, maxSlots);

        if (!result.success) {
            String msg = cfg.getString("raffle.message.fail_generic", "&c{reason}");
            player.sendMessage(color(msg.replace("{reason}", result.message)));
            playFailSound(player, cfg);
            return;
        }

        RaffleLoreUtil.updateLore(armor, maxSlots);
        decrement(player, tokenSlot);

        String success = cfg.getString(
                "raffle.message.success",
                "&dSomething shifts within the armor..."
        );
        player.sendMessage(color(success));
        playSuccessSound(player, cfg);

        // Helps prevent rare client-side ghost items (especially with Bedrock/Geyser)
        player.updateInventory();
    }

    /* =========================
       Helpers
       ========================= */

    private void decrement(Player player, EquipmentSlot slot) {
        ItemStack stack = (slot == EquipmentSlot.HAND)
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();

        if (stack == null || stack.getType() == Material.AIR) return;

        if (stack.getAmount() <= 1) {
            if (slot == EquipmentSlot.HAND) {
                player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                player.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
            }
        } else {
            stack.setAmount(stack.getAmount() - 1);
        }
    }

    private boolean isArmor(Material mat) {
        if (mat == null) return false;
        String n = mat.name();
        return n.endsWith("_HELMET")
                || n.endsWith("_CHESTPLATE")
                || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS");
    }

    private void fail(Player p, FileConfiguration cfg, String fallback) {
        String msg = cfg.getString("raffle.message.fail_generic", "&c{reason}");
        msg = msg.replace("{reason}", fallback != null ? fallback : "Invalid armor.");
        p.sendMessage(color(msg));
        playFailSound(p, cfg);
    }

    private void playSuccessSound(Player p, FileConfiguration cfg) {
        if (!cfg.getBoolean("raffle.sound.success.enabled", true)) return;
        Sound s = safeSound(cfg.getString("raffle.sound.success.key"));
        if (s != null) {
            p.playSound(
                    p.getLocation(),
                    s,
                    (float) cfg.getDouble("raffle.sound.success.volume", 0.8),
                    (float) cfg.getDouble("raffle.sound.success.pitch", 1.2)
            );
        }
    }

    private void playFailSound(Player p, FileConfiguration cfg) {
        if (!cfg.getBoolean("raffle.sound.fail.enabled", true)) return;
        Sound s = safeSound(cfg.getString("raffle.sound.fail.key"));
        if (s != null) {
            p.playSound(
                    p.getLocation(),
                    s,
                    (float) cfg.getDouble("raffle.sound.fail.volume", 0.7),
                    (float) cfg.getDouble("raffle.sound.fail.pitch", 0.9)
            );
        }
    }

    private Sound safeSound(String key) {
        if (key == null) return null;
        try {
            return Sound.valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
