package com.orangeslices.additionalbosses.bosses.listeners;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import com.orangeslices.additionalbosses.kits.MarkKits;
import com.orangeslices.additionalbosses.kits.PotionKits;
import com.orangeslices.additionalbosses.kits.SharpeningKits;
import com.orangeslices.additionalbosses.raffle.RaffleTokenFactory;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Random;

public final class BossDropListener implements Listener {

    private final AdditionalBossesPlugin plugin;
    private final Random random = new Random();

    public BossDropListener(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        LivingEntity boss = event.getEntity();
        if (!plugin.bossApplier().isBoss(boss)) return;

        String rank = safeRank(plugin.bossApplier().getRank(boss));
        World world = boss.getWorld();

        // 1) Raffle token drops
        rollRaffleToken(world, boss, rank);

        // 2) Kit drops (rarer)
        rollKitDrops(world, boss, rank);

        // 3) Vanilla bonus loot — reduced
        rollReducedBonusLoot(world, boss, mapRankToTier(rank));
    }

    /* =========================
       1) RAFFLE TOKEN
       ========================= */

    private void rollRaffleToken(World world, LivingEntity boss, String rankUpper) {
        double chance = switch (rankUpper) {
            case "GRAY" -> 0.15;
            case "GREEN" -> 0.22;
            case "RED" -> 0.30;
            case "PURPLE" -> 0.35;
            case "GOLD" -> 0.40;
            default -> 0.15;
        };

        if (random.nextDouble() < chance) {
            world.dropItemNaturally(boss.getLocation(), RaffleTokenFactory.createToken());
        }
    }

    /* =========================
       2) KIT DROPS (RARER)
       KEEP: Sharpening / Mark / Haste / Strength
       REMOVE: FireRes / HealthBoost / NightVision / WaterBreathing
       ========================= */

    private void rollKitDrops(World world, LivingEntity boss, String rankUpper) {
        double kitChance = switch (rankUpper) {
            case "GRAY" -> 0.10;
            case "GREEN" -> 0.15;
            case "RED" -> 0.22;
            case "PURPLE" -> 0.30;
            case "GOLD" -> 0.40;
            default -> 0.10;
        };

        if (random.nextDouble() >= kitChance) return;

        // First kit
        dropOneKit(world, boss, rankUpper);

        // Optional second kit (high tiers only)
        double secondChance = switch (rankUpper) {
            case "PURPLE" -> 0.10;
            case "GOLD" -> 0.15;
            default -> 0.0;
        };

        if (secondChance > 0 && random.nextDouble() < secondChance) {
            dropOneKit(world, boss, rankUpper);
        }
    }

    private void dropOneKit(World world, LivingEntity boss, String rankUpper) {
        TokenType type = rollKitType(rankUpper);
        int level = rollKitLevel(type, rankUpper);

        ItemStack token = switch (type) {
            case SHARPENING -> SharpeningKits.makeSharpeningKit(plugin, level);
            case MARK -> MarkKits.makeMarkKit(plugin, level);
            case HASTE -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.HASTE, level);
            case STRENGTH -> PotionKits.makePotionKit(plugin, PotionKits.PotionTokenType.STRENGTH, level);
        };

        world.dropItemNaturally(boss.getLocation(), token);
    }

    /**
     * Weighted selection per-rank.
     * Early ranks: mostly Sharpening/Mark.
     * Higher ranks: more Haste/Strength.
     */
    private TokenType rollKitType(String rankUpper) {
        double r = random.nextDouble();

        return switch (rankUpper) {
            case "GRAY" -> pickWeighted(r,
                    0.58, TokenType.SHARPENING,
                    0.42, TokenType.MARK
            );

            case "GREEN" -> pickWeighted(r,
                    0.50, TokenType.SHARPENING,
                    0.34, TokenType.MARK,
                    0.10, TokenType.HASTE,
                    0.06, TokenType.STRENGTH
            );

            case "RED" -> pickWeighted(r,
                    0.40, TokenType.SHARPENING,
                    0.30, TokenType.MARK,
                    0.16, TokenType.HASTE,
                    0.14, TokenType.STRENGTH
            );

            case "PURPLE" -> pickWeighted(r,
                    0.30, TokenType.SHARPENING,
                    0.22, TokenType.MARK,
                    0.24, TokenType.HASTE,
                    0.24, TokenType.STRENGTH
            );

            case "GOLD" -> pickWeighted(r,
                    0.22, TokenType.SHARPENING,
                    0.18, TokenType.MARK,
                    0.30, TokenType.HASTE,
                    0.30, TokenType.STRENGTH
            );

            default -> pickWeighted(r,
                    0.50, TokenType.SHARPENING,
                    0.50, TokenType.MARK
            );
        };
    }

    /**
     * Rank-scaled Level II chance (only for sharpening/mark/haste/strength).
     */
    private int rollKitLevel(TokenType type, String rankUpper) {
        double r = random.nextDouble();

        return switch (rankUpper) {
            case "RED" -> (r < 0.20) ? 2 : 1;
            case "PURPLE" -> (r < 0.35) ? 2 : 1;
            case "GOLD" -> (r < 0.50) ? 2 : 1;
            default -> 1;
        };
    }

    private TokenType pickWeighted(double r, Object... pairs) {
        double cumulative = 0.0;
        for (int i = 0; i < pairs.length; i += 2) {
            double w = (double) pairs[i];
            TokenType t = (TokenType) pairs[i + 1];
            cumulative += w;
            if (r < cumulative) return t;
        }
        return (TokenType) pairs[pairs.length - 1];
    }

    private enum TokenType {
        SHARPENING,
        MARK,
        HASTE,
        STRENGTH
    }

    /* =========================
       3) VANILLA BONUS LOOT (REDUCED)
       ========================= */

    private void rollReducedBonusLoot(World world, LivingEntity boss, Tier tier) {
        double roll = random.nextDouble();

        switch (tier) {
            case LOW -> {
                if (roll < 0.35) dropCommon(world, boss);
                else if (roll < 0.45) dropUncommon(world, boss);
            }
            case MID -> {
                if (roll < 0.30) dropCommon(world, boss);
                else if (roll < 0.45) dropUncommon(world, boss);
                else if (roll < 0.50) dropRare(world, boss, false);
            }
            case HIGH -> {
                if (roll < 0.25) dropCommon(world, boss);
                else if (roll < 0.45) dropUncommon(world, boss);
                else if (roll < 0.55) dropRare(world, boss);
            }
        }
    }

    private void dropCommon(World world, LivingEntity boss) {
        switch (random.nextInt(4)) {
            case 0 -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.IRON_INGOT, rand(1, 4)));
            case 1 -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.GOLD_INGOT, rand(1, 4)));
            case 2 -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.REDSTONE, rand(4, 10)));
            default -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.LAPIS_LAZULI, rand(4, 10)));
        }
    }

    private void dropUncommon(World world, LivingEntity boss) {
        switch (random.nextInt(4)) {
            case 0 -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.GOLD_BLOCK, 1));
            case 1 -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.DIAMOND, 1));
            case 2 -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.GOLDEN_APPLE, 1));
            default -> world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.COAL, rand(12, 24)));
        }
    }

    private void dropRare(World world, LivingEntity boss, boolean allowNetheriteScrap) {
        if (random.nextDouble() < 0.15) {
            world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1));
            return;
        }

        if (allowNetheriteScrap) {
            world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.NETHERITE_SCRAP, 1));
        } else {
            world.dropItemNaturally(boss.getLocation(), new ItemStack(Material.DIAMOND, 1));
        }
    }

    private void dropRare(World world, LivingEntity boss) {
        // Higher tier uses netherite scrap occasionally
        dropRare(world, boss, true);
    }

    /* =========================
       Helpers
       ========================= */

    private String safeRank(String rank) {
        if (rank == null || rank.isBlank()) return "GRAY";
        return rank.trim().toUpperCase(Locale.ROOT);
    }

    private Tier mapRankToTier(String rankUpper) {
        return switch (rankUpper) {
            case "RED" -> Tier.MID;
            case "PURPLE", "GOLD" -> Tier.HIGH;
            default -> Tier.LOW;
        };
    }

    private int rand(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    private enum Tier {
        LOW, MID, HIGH
    }
}
