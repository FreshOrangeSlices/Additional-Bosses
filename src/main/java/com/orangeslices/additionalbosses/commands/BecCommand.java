package com.orangeslices.additionalbosses.commands;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import com.orangeslices.additionalbosses.bosses.BossApplier;
import com.orangeslices.additionalbosses.raffle.RaffleTokenFactory;
import org.bukkit.ChatColor;
import org.bukkit.EntityEffect;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

public final class BecCommand implements CommandExecutor {

    private final AdditionalBossesPlugin plugin;
    private final BossApplier bossApplier;

    private final NamespacedKey bossKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey affixesKey;

    public BecCommand(AdditionalBossesPlugin plugin, BossApplier bossApplier) {
        this.plugin = plugin;
        this.bossApplier = bossApplier;

        // Centralized keys (same IDs as before)
        this.bossKey = plugin.bossKey();
        this.rankKey = new NamespacedKey(plugin, "rank");
        this.affixesKey = new NamespacedKey(plugin, "affixes");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Player only.");
            return true;
        }

        if (args.length == 0) {
            help(player);
            return true;
        }

        // ---------------------------------
        // /bec raffle [amount]  (OP only)
        // ---------------------------------
        if (args[0].equalsIgnoreCase("raffle")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
                return true;
            }

            int amount = 1;
            if (args.length >= 2) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[1]));
                } catch (NumberFormatException ignored) {
                }
            }

            ItemStack token = RaffleTokenFactory.createToken();
            for (int i = 0; i < amount; i++) {
                player.getInventory().addItem(token.clone());
            }

            player.sendMessage(ChatColor.LIGHT_PURPLE + "Given " + amount + " raffle token(s).");
            return true;
        }

        // ---------------------------------
        // /bec test <RANK> <MOB> [affixes]
        // ---------------------------------
        if (args[0].equalsIgnoreCase("test")) {
            handleTest(player, args);
            return true;
        }

        help(player);
        return true;
    }

    private void handleTest(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(ChatColor.RED + "Usage: /bec test <RANK> <MOB> [affixes]");
            player.sendMessage(ChatColor.GRAY + "Example: /bec test GOLD ZOMBIE lifesteal,mark,thorns");
            return;
        }

        String rank = args[1].toUpperCase(Locale.ROOT);

        // Temporary validation: relies on config until ranks are code-driven
        if (!plugin.getConfig().isConfigurationSection("ranks." + rank)) {
            player.sendMessage(ChatColor.RED + "Unknown rank: " + rank);
            return;
        }

        EntityType type;
        try {
            type = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            player.sendMessage(ChatColor.RED + "Unknown mob type: " + args[2]);
            return;
        }

        if (!type.isAlive() || type.getEntityClass() == null
                || !LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
            player.sendMessage(ChatColor.RED + "That entity isn't a living mob: " + type.name());
            return;
        }

        World world = player.getWorld();
        LivingEntity mob = (LivingEntity) world.spawnEntity(player.getLocation(), type);

        // Force boss + rank BEFORE normal pipeline
        mob.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        mob.getPersistentDataContainer().set(rankKey, PersistentDataType.STRING, rank);

        // Optional forced affixes (csv)
        if (args.length >= 4) {
            String csv = args[3].trim();
            if (!csv.isBlank()) {
                mob.getPersistentDataContainer().set(
                        affixesKey,
                        PersistentDataType.STRING,
                        csv.toLowerCase(Locale.ROOT)
                );
            }
        }

        // Run normal boss pipeline
        plugin.onBossCreated(mob);
        bossApplier.apply(mob);

        mob.playEffect(EntityEffect.ENTITY_POOF);

        player.sendMessage(ChatColor.GREEN + "Spawned " + rank + " boss " + type.name()
                + (args.length >= 4 ? (" with affixes: " + args[3]) : ""));
    }

    private void help(Player player) {
        player.sendMessage(ChatColor.GOLD + "Additional-Bosses Commands:");
        player.sendMessage(ChatColor.YELLOW + "/bec test <RANK> <MOB> [affixes]");
        player.sendMessage(ChatColor.GRAY + "Example: /bec test GOLD ZOMBIE lifesteal,mark,thorns");
        player.sendMessage(ChatColor.LIGHT_PURPLE + "/bec raffle [amount]");
        player.sendMessage(ChatColor.GRAY + "Gives raffle tokens (OP only).");
        player.sendMessage(ChatColor.GRAY + "Ranks: GRAY, GREEN, RED, PURPLE, GOLD");
    }
}
