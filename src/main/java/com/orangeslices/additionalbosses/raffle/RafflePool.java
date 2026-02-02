package com.orangeslices.additionalbosses.raffle;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class RafflePool {

    private final AdditionalBossesPlugin plugin;
    private List<RaffleEffectId> pool = new ArrayList<>();

    public RafflePool(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
    }

    public void reloadFromConfig() {
        FileConfiguration cfg = plugin.getConfig();

        List<String> raw = cfg.getStringList("raffle.effects");
        List<RaffleEffectId> parsed = new ArrayList<>();

        for (String s : raw) {
            RaffleEffectId id = RaffleEffectId.fromString(s);
            if (id != null) {
                parsed.add(id);
            }
        }

        this.pool = parsed;
    }

    public boolean isEmpty() {
        return pool == null || pool.isEmpty();
    }

    /**
     * Immutable view of the current pool.
     */
    public List<RaffleEffectId> snapshot() {
        return Collections.unmodifiableList(pool);
    }

    /**
     * Equal chance roll from the configured list.
     * Returns null if the pool is empty.
     */
    public RaffleEffectId roll() {
        if (isEmpty()) return null;
        int idx = ThreadLocalRandom.current().nextInt(pool.size());
        return pool.get(idx);
    }
}
