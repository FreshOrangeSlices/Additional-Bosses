package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zoglin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MatadorEffect implements RaffleCustomEffect {

    private static final int DESPAWN_TICKS = 20 * 12; // ~12 seconds

    // NOTE: Held-item knockback often does NOT apply for Zoglins.
    // We'll add code-based knockback later if you want it 100% reliable.
    private static final int DISPLAY_KNOCKBACK_LEVEL = 3;

    private final Map<UUID, Entity> spawned = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> despawnTasks = new ConcurrentHashMap<>();

    @Override
    public RaffleEffectId getId() {
        return RaffleEffectId.MATADOR;
    }

    @Override
    public ArmorSlot slotRestriction() {
        return ArmorSlot.BOOTS;
    }

    @Override
    public void apply(Player player, int level) {
        if (player == null || !player.isOnline()) return;

        UUID id = player.getUniqueId();

        // One-time trigger while armor is worn
        if (spawned.containsKey(id)) return;

        Zoglin bull = player.getWorld().spawn(
                player.getLocation().add(2, 0, 2),
                Zoglin.class,
                z -> {
                    z.setAdult();
                    z.setRemoveWhenFarAway(true);
                    z.setCanPickupItems(false);
                }
        );

        // Aggro immediately
        bull.setTarget(player);

        // Speed I for lifetime (no particles / icon)
        bull.addPotionEffect(new PotionEffect(
                PotionEffectType.SPEED,
                DESPAWN_TICKS,
                0,
                true,
                false,
                false
        ));

        // WEAKNESS so it deals little damage (I or II)
        bull.addPotionEffect(new PotionEffect(
                PotionEffectType.WEAKNESS,
                DESPAWN_TICKS,
                1, // Weakness II; change to 0 for Weakness I
                true,
                false,
                false
        ));

        // Optional: make it less oppressive (comment out if you want it scarier)
        bull.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS,
                DESPAWN_TICKS,
                0, // Slowness I
                true,
                false,
                false
        ));

        // Equip a "baton" purely for flavor (may not apply knockback in practice)
        equipBaton(bull);

        // Audio cue
        player.getWorld().playSound(
                player.getLocation(),
                Sound.ENTITY_ZOGLIN_ANGRY,
                0.9f,
                0.9f
        );

        spawned.put(id, bull);

        // Despawn after duration
        BukkitTask despawn = Bukkit.getScheduler().runTaskLater(
                Bukkit.getPluginManager().getPlugins()[0],
                () -> {
                    Entity e = spawned.remove(id);
                    if (e != null && e.isValid()) e.remove();

                    BukkitTask t = despawnTasks.remove(id);
                    if (t != null) t.cancel();
                },
                DESPAWN_TICKS
        );

        despawnTasks.put(id, despawn);
    }

    @Override
    public void clear(Player player) {
        if (player == null) return;

        UUID id = player.getUniqueId();

        BukkitTask t = despawnTasks.remove(id);
        if (t != null) t.cancel();

        Entity e = spawned.remove(id);
        if (e != null && e.isValid()) e.remove();
    }

    private static void equipBaton(Zoglin bull) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Matador's Baton");
            meta.addEnchant(Enchantment.KNOCKBACK, DISPLAY_KNOCKBACK_LEVEL, true);
            stick.setItemMeta(meta);
        } else {
            stick.addUnsafeEnchantment(Enchantment.KNOCKBACK, DISPLAY_KNOCKBACK_LEVEL);
        }

        EntityEquipment eq = bull.getEquipment();
        if (eq != null) {
            eq.setItemInMainHand(stick);
            eq.setItemInMainHandDropChance(0.0f);

            // Safety: no armor drops either
            eq.setHelmetDropChance(0.0f);
            eq.setChestplateDropChance(0.0f);
            eq.setLeggingsDropChance(0.0f);
            eq.setBootsDropChance(0.0f);
        }
    }
}
