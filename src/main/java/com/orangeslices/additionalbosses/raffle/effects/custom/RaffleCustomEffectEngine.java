package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import com.orangeslices.additionalbosses.raffle.effects.RaffleEffectReader;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Engine for NON-potion raffle effects (curses & custom mechanics).
 *
 * Key behavior:
 * - CURSES trigger ONCE when they become active
 * - clear() is called when the effect disappears
 *
 * GeckoGrip:
 * - runs on a fast tick, but ONLY while at least one player actually has it active
 *
 * DEFENSIVE GUARANTEE:
 * - Even if a cursed item ends up on the wrong armor slot,
 *   the curse will NOT trigger.
 */
public final class RaffleCustomEffectEngine {

    private final AdditionalBossesPlugin plugin;

    // slow scan task (detects active effects)
    private BukkitTask scanTask;

    // fast tick task (runs only while GeckoGrip is active for at least one player)
    private BukkitTask tickTask;

    // Registered custom effects (permanent registry)
    private final Map<RaffleEffectId, RaffleCustomEffect> registry = new HashMap<>();

    // Tracks which effects are currently active per player
    private final Map<UUID, Set<RaffleEffectId>> activeByPlayer = new HashMap<>();

    // Cache: GeckoGrip level per player (only players who currently have it)
    private final Map<UUID, Integer> geckoLevel = new HashMap<>();

    public RaffleCustomEffectEngine(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        register(new TerrorEffect());
        register(new DreadEffect());

        register(new GeckoGripEffect());

        // FIX: Echoes now uses plugin-owned scheduling + player-local sound
        register(new EchoesEffect(plugin));

        register(new DisarrayEffect());
        register(new MatadorEffect());

        // FIX: schedule ownership must be OUR plugin
        register(new MotherHenEffect(plugin));

        register(new ReductionEffect());
    }

    private void register(RaffleCustomEffect effect) {
        if (effect == null) return;
        registry.put(effect.getId(), effect);
    }

    public void start() {
        stop();

        // Slow scan: discover active effects (every 2s)
        scanTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> plugin.getServer().getOnlinePlayers().forEach(this::refreshPlayer),
                20L,
                40L
        );

        // tickTask is now LAZY: only starts when geckoLevel becomes non-empty
        tickTask = null;
    }

    public void stop() {
        if (scanTask != null) scanTask.cancel();
        scanTask = null;

        stopGeckoTick();

        // Cleanup all active effects
        for (Map.Entry<UUID, Set<RaffleEffectId>> entry : activeByPlayer.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;

            for (RaffleEffectId id : entry.getValue()) {
                RaffleCustomEffect effect = registry.get(id);
                if (effect != null) effect.clear(player);
            }
        }

        activeByPlayer.clear();
        geckoLevel.clear();
    }

    /**
     * Public "poke" for event-driven updates later (join/equip/respawn/etc).
     * Safe to call anytime.
     */
    public void refreshPlayerNow(Player player) {
        refreshPlayer(player);
    }

    private void refreshPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        UUID uuid = player.getUniqueId();
        Map<RaffleEffectId, Integer> highest = new HashMap<>();

        mergeArmor(highest, player.getInventory().getHelmet(), EquipmentSlot.HEAD);
        mergeArmor(highest, player.getInventory().getChestplate(), EquipmentSlot.CHEST);
        mergeArmor(highest, player.getInventory().getLeggings(), EquipmentSlot.LEGS);
        mergeArmor(highest, player.getInventory().getBoots(), EquipmentSlot.FEET);

        Set<RaffleEffectId> nowActive = new HashSet<>();
        for (Map.Entry<RaffleEffectId, Integer> e : highest.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (!registry.containsKey(e.getKey())) continue;
            nowActive.add(e.getKey());
        }

        // Update Gecko cache (so fast tick only runs for these players)
        if (nowActive.contains(RaffleEffectId.GECKO_GRIP)) {
            geckoLevel.put(uuid, highest.getOrDefault(RaffleEffectId.GECKO_GRIP, 1));
        } else {
            geckoLevel.remove(uuid);
        }

        // Start/stop gecko ticking based on whether we have anyone active
        ensureGeckoTickState();

        Set<RaffleEffectId> prev = activeByPlayer.getOrDefault(uuid, Collections.emptySet());

        // Newly activated effects (apply once)
        for (RaffleEffectId id : nowActive) {
            if (prev.contains(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect == null) continue;

            int level = highest.getOrDefault(id, 1);
            effect.apply(player, level);
        }

        // Removed effects (clear)
        for (RaffleEffectId id : prev) {
            if (nowActive.contains(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect != null) effect.clear(player);
        }

        activeByPlayer.put(uuid, nowActive);
    }

    private void ensureGeckoTickState() {
        if (geckoLevel.isEmpty()) {
            stopGeckoTick();
            return;
        }

        if (tickTask != null) return;

        // Create the fast tick ONLY when needed
        tickTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin,
                () -> {
                    if (geckoLevel.isEmpty()) {
                        stopGeckoTick();
                        return;
                    }

                    RaffleCustomEffect fx = registry.get(RaffleEffectId.GECKO_GRIP);
                    if (fx == null) return;

                    // copy keys to avoid CME if a refresh happens mid-loop
                    List<Map.Entry<UUID, Integer>> entries = new ArrayList<>(geckoLevel.entrySet());
                    for (Map.Entry<UUID, Integer> e : entries) {
                        Player p = plugin.getServer().getPlayer(e.getKey());
                        if (p == null || !p.isOnline()) continue;

                        fx.apply(p, e.getValue());
                    }
                },
                0L,
                5L // responsive, still light
        );
    }

    private void stopGeckoTick() {
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor, EquipmentSlot slot) {
        if (armor == null) return;

        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);

        // Defensive curse slot rules
        for (Iterator<Map.Entry<RaffleEffectId, Integer>> it = map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<RaffleEffectId, Integer> e = it.next();

            if (e.getKey().isCurse() && !isCurseCompatibleWithSlot(e.getKey(), slot)) {
                it.remove();
            }
        }

        RaffleEffectReader.mergeHighest(into, map);
    }

    /**
     * Defensive curse slot rules.
     * Must mirror RaffleService.
     */
    private boolean isCurseCompatibleWithSlot(RaffleEffectId id, EquipmentSlot slot) {
        if (id == null || slot == null) return false;

        return switch (id) {
            case TERROR, REDUCTION -> slot == EquipmentSlot.HEAD;
            case DREAD -> slot == EquipmentSlot.CHEST;
            case MOTHER_HEN -> slot == EquipmentSlot.LEGS;
            case MATADOR -> slot == EquipmentSlot.FEET;
            default -> false;
        };
    }
}
