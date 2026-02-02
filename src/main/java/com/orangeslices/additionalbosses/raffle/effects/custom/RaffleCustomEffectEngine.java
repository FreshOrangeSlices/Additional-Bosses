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
 * - GOOD custom effects (future) may apply repeatedly if desired
 * - CURSES trigger ONCE when they become active
 * - clear() is called when the effect disappears
 *
 * DEFENSIVE GUARANTEE:
 * - Even if a cursed item ends up on the wrong armor slot,
 *   the curse will NOT trigger.
 */
public final class RaffleCustomEffectEngine {

    private final AdditionalBossesPlugin plugin;
    private BukkitTask task;

    // Registered custom effects (permanent registry)
    private final Map<RaffleEffectId, RaffleCustomEffect> registry = new HashMap<>();

    // Tracks which effects are currently active per player
    private final Map<UUID, Set<RaffleEffectId>> activeByPlayer = new HashMap<>();

    public RaffleCustomEffectEngine(AdditionalBossesPlugin plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        register(new TerrorEffect());
        register(new DreadEffect());

        // BENCHED:
        // register(new MisstepEffect());
        // register(new UneaseEffect());

        register(new GeckoGripEffect());
        register(new EchoesEffect());
        register(new DisarrayEffect());
        register(new OnAllFoursEffect());
        register(new MatadorEffect());
        register(new MotherHenEffect());
        register(new ReductionEffect());
    }

    private void register(RaffleCustomEffect effect) {
        if (effect == null) return;
        registry.put(effect.getId(), effect);
    }

    public void start() {
        stop();

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

        // Cleanup all active effects
        for (Map.Entry<UUID, Set<RaffleEffectId>> entry : activeByPlayer.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) continue;

            for (RaffleEffectId id : entry.getValue()) {
                RaffleCustomEffect effect = registry.get(id);
                if (effect != null) {
                    effect.clear(player);
                }
            }
        }

        activeByPlayer.clear();
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

        Set<RaffleEffectId> prev = activeByPlayer.getOrDefault(uuid, Collections.emptySet());

        // Newly activated effects
        for (RaffleEffectId id : nowActive) {
            if (prev.contains(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect == null) continue;

            int level = highest.getOrDefault(id, 1);
            effect.apply(player, level);
        }

        // Removed effects
        for (RaffleEffectId id : prev) {
            if (nowActive.contains(id)) continue;

            RaffleCustomEffect effect = registry.get(id);
            if (effect != null) {
                effect.clear(player);
            }
        }

        activeByPlayer.put(uuid, nowActive);
    }

    private void mergeArmor(Map<RaffleEffectId, Integer> into, ItemStack armor, EquipmentSlot slot) {
        if (armor == null) return;

        Map<RaffleEffectId, Integer> map = RaffleEffectReader.readFromItem(armor);

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
