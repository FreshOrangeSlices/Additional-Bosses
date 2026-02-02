package com.orangeslices.additionalbosses.bosses;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class BossApplier {

    private final AdditionalBossesPlugin plugin;

    // PDC keys
    private final NamespacedKey bossKey;
    private final NamespacedKey rankKey;
    private final NamespacedKey affixesKey;
    private final NamespacedKey titleKey;

    public BossApplier(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;

        this.bossKey = plugin.bossKey();
        this.rankKey = new NamespacedKey(plugin, "rank");
        this.affixesKey = new NamespacedKey(plugin, "affixes");
        this.titleKey = new NamespacedKey(plugin, "title");
    }

    /* -------------------------
       Boss flags / getters
       ------------------------- */

    public boolean isBoss(LivingEntity entity) {
        if (entity == null) return false;
        Byte val = entity.getPersistentDataContainer().get(bossKey, PersistentDataType.BYTE);
        return val != null && val == (byte) 1;
    }

    public void markBoss(LivingEntity entity) {
        if (entity == null) return;
        entity.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
    }

    public void unmarkBoss(LivingEntity entity) {
        if (entity == null) return;
        entity.getPersistentDataContainer().remove(bossKey);
    }

    public String getRank(LivingEntity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(rankKey, PersistentDataType.STRING);
    }

    public String getAffixesString(LivingEntity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(affixesKey, PersistentDataType.STRING);
    }

    public String getTitle(LivingEntity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(titleKey, PersistentDataType.STRING);
    }

    public void setRank(LivingEntity entity, String rank) {
        if (entity == null) return;

        if (rank == null || rank.isBlank()) {
            entity.getPersistentDataContainer().remove(rankKey);
            return;
        }
        entity.getPersistentDataContainer().set(
                rankKey,
                PersistentDataType.STRING,
                rank.trim().toUpperCase(Locale.ROOT)
        );
    }

    public void setAffixes(LivingEntity entity, List<String> affixes) {
        if (entity == null) return;

        PersistentDataContainer pdc = entity.getPersistentDataContainer();

        if (affixes == null || affixes.isEmpty()) {
            pdc.remove(affixesKey);
            pdc.remove(titleKey);
            return;
        }

        List<String> cleaned = new ArrayList<>();
        for (String a : affixes) {
            if (a == null) continue;
            String id = a.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) cleaned.add(id);
        }

        if (cleaned.isEmpty()) {
            pdc.remove(affixesKey);
            pdc.remove(titleKey);
            return;
        }

        pdc.set(affixesKey, PersistentDataType.STRING, String.join(",", cleaned));
        pdc.remove(titleKey); // force title rebuild
    }

    /* -------------------------
       Main apply pipeline
       ------------------------- */

    public void apply(LivingEntity entity) {
        if (entity == null) return;
        if (isBoss(entity)) return; // prevent double-apply

        // 1) Rank
        String rankId = getRank(entity);
        if (rankId == null || rankId.isBlank()) {
            rankId = rollRankId();
            if (rankId != null) {
                entity.getPersistentDataContainer().set(rankKey, PersistentDataType.STRING, rankId);
            }
        }

        // 2) Stats
        double hpMult = getRankOrDefaultDouble(rankId, "stats.health_multiplier",
                plugin.getConfig().getDouble("stats.health_multiplier", 4.0));

        double dmgMult = getRankOrDefaultDouble(rankId, "stats.damage_multiplier",
                plugin.getConfig().getDouble("stats.damage_multiplier", 1.5));

        double spdMult = getRankOrDefaultDouble(rankId, "stats.speed_multiplier",
                plugin.getConfig().getDouble("stats.speed_multiplier", 1.0));

        int maxAffixes = getMaxAffixesForRank(rankId);

        markBoss(entity);

        AttributeInstance maxHp = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) {
            double newMax = Math.max(1.0, maxHp.getBaseValue() * hpMult);
            maxHp.setBaseValue(newMax);
            entity.setHealth(newMax);
        }

        AttributeInstance dmg = entity.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) {
            dmg.setBaseValue(Math.max(0.0, dmg.getBaseValue() * dmgMult));
        }

        AttributeInstance spd = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) {
            spd.setBaseValue(Math.max(0.0, spd.getBaseValue() * spdMult));
        }

        applyAffixSelection(entity, maxAffixes);
        applyTitleFromAffixes(entity);
        applyNameplate(entity, rankId);
    }

    /* -------------------------
       Nameplate
       ------------------------- */

    private void applyNameplate(LivingEntity entity, String rankId) {
        if (!plugin.getConfig().getBoolean("boss.name.enabled", true)) return;

        String rankColor = plugin.getConfig().getString("boss.name.color", "&c");
        ConfigurationSection rankSec = getRankSection(rankId);
        if (rankSec != null) {
            rankColor = rankSec.getString("color", rankColor);
        }

        String title = getTitle(entity);
        String titlePart = (title != null && !title.isBlank()) ? title.trim() + " " : "";

        String baseText = plugin.getConfig().getString("boss.name.text", "Boss");
        if (baseText == null || baseText.isBlank()) {
            baseText = prettyMobName(entity.getType());
        }

        String finalName = ChatColor.translateAlternateColorCodes('&',
                rankColor + titlePart + baseText
        ).trim();

        entity.setCustomName(finalName);
        entity.setCustomNameVisible(true);
    }

    private String prettyMobName(EntityType type) {
        String raw = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        String[] parts = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p.isBlank()) continue;
            sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
        }
        return sb.toString().trim();
    }

    /* -------------------------
       Title from first 2 affixes
       ------------------------- */

    private void applyTitleFromAffixes(LivingEntity entity) {
        if (getTitle(entity) != null) return;

        String csv = getAffixesString(entity);
        if (csv == null || csv.isBlank()) return;

        String[] affixes = csv.split(",");
        List<String> words = new ArrayList<>(2);

        for (String a : affixes) {
            if (a == null) continue;
            String id = a.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) continue;

            String word = plugin.getConfig().getString("affix_titles." + id, "");
            if (word != null && !word.isBlank()) {
                words.add(word.trim());
            }
            if (words.size() >= 2) break;
        }

        if (!words.isEmpty()) {
            entity.getPersistentDataContainer().set(
                    titleKey,
                    PersistentDataType.STRING,
                    String.join(" ", words)
            );
        }
    }

    /* -------------------------
       Rank + affix helpers
       ------------------------- */

    private ConfigurationSection getRankSection(String rankId) {
        if (rankId == null || rankId.isBlank()) return null;
        return plugin.getConfig().getConfigurationSection("ranks." + rankId);
    }

    private double getRankOrDefaultDouble(String rankId, String relPath, double fallback) {
        ConfigurationSection sec = getRankSection(rankId);
        return sec != null ? sec.getDouble(relPath, fallback) : fallback;
    }

    private int getMaxAffixesForRank(String rankId) {
        int fallback = Math.max(0, plugin.getConfig().getInt("affixes.max_per_boss", 0));
        ConfigurationSection sec = getRankSection(rankId);
        return sec != null ? Math.max(0, sec.getInt("max_affixes", fallback)) : fallback;
    }

    private String rollRankId() {
        ConfigurationSection ranks = plugin.getConfig().getConfigurationSection("ranks");
        if (ranks == null) return null;

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String id : ranks.getKeys(false)) {
            int w = Math.max(0, ranks.getInt(id + ".weight", 0));
            if (w > 0) entries.add(Map.entry(id, w));
        }

        if (entries.isEmpty()) return null;

        int total = entries.stream().mapToInt(Map.Entry::getValue).sum();
        int roll = ThreadLocalRandom.current().nextInt(total);

        int running = 0;
        for (var e : entries) {
            running += e.getValue();
            if (roll < running) return e.getKey();
        }

        return entries.get(entries.size() - 1).getKey();
    }

    private void applyAffixSelection(LivingEntity entity, int max) {
        if (!plugin.getConfig().getBoolean("affixes.enabled", true)) return;
        if (max <= 0) return;

        if (getAffixesString(entity) != null) return; // respect forced affixes

        ConfigurationSection pool = plugin.getConfig().getConfigurationSection("affixes.pool");
        if (pool == null) return;

        List<Map.Entry<String, Integer>> entries = new ArrayList<>();
        for (String id : pool.getKeys(false)) {
            int w = Math.max(0, pool.getInt(id + ".weight", 1));
            if (w > 0) entries.add(Map.entry(id.trim().toLowerCase(Locale.ROOT), w));
        }

        if (entries.isEmpty()) return;

        List<String> chosen = new ArrayList<>();

        for (int i = 0; i < max && !entries.isEmpty(); i++) {
            int total = 0;
            for (var e : entries) total += Math.max(0, e.getValue());
            if (total <= 0) break;

            int roll = ThreadLocalRandom.current().nextInt(total);
            int running = 0;

            int pickedIndex = -1;
            for (int idx = 0; idx < entries.size(); idx++) {
                running += Math.max(0, entries.get(idx).getValue());
                if (roll < running) {
                    pickedIndex = idx;
                    break;
                }
            }

            if (pickedIndex < 0) pickedIndex = entries.size() - 1;

            String picked = entries.get(pickedIndex).getKey();
            chosen.add(picked);

            // Remove picked so we don't duplicate affixes
            entries.remove(pickedIndex);
        }

        if (!chosen.isEmpty()) {
            setAffixes(entity, chosen);
        }
    }
}
