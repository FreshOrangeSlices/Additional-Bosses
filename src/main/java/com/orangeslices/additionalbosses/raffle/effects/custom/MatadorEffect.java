package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.raffle.RaffleEffectId;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zoglin;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MatadorEffect implements RaffleCustomEffect {

    private static final int DESPAWN_TICKS = 20 * 12; // ~12 seconds
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
        if (spawned.containsKey(id)) return;

        JavaPlugin plugin = JavaPlugin.getProvidingPlugin(getClass());
        NamespacedKey matadorBullKey = new NamespacedKey(plugin, "matador_bull");

        Zoglin bull = player.getWorld().spawn(
                player.getLocation().add(2, 0, 2),
                Zoglin.class,
                z -> {
                    z.setAdult();
                    z.setRemoveWhenFarAway(true);
                    z.setCanPickupItems(false);

                    // Tag this Zoglin so the knockback listener can detect it
                    z.getPersistentDataContainer().set(matadorBullKey, PersistentDataType.BYTE, (byte) 1);
                }
        );

        bull.setTarget(player);

        // Speed I (no particles/icon)
        bull.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, DESPAWN_TICKS, 0, true, false, false));

        // Weakness so it does little damage
        bull.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, DESPAWN_TICKS, 1, true, false, false));

        // Optional: slightly slower so it's not oppressive
        bull.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, DESPAWN_TICKS, 0, true, false, false));

        // Held item is flavor; knockback is handled by listener
        equipBaton(bull);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOGLIN_ANGRY, 0.9f, 0.9f);

        spawned.put(id, bull);

        BukkitTask despawn = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity e = spawned.remove(id);
            if (e != null && e.isValid()) e.remove();

            BukkitTask t = despawnTasks.remove(id);
            if (t != null) t.cancel();
        }, DESPAWN_TICKS);

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

            eq.setHelmetDropChance(0.0f);
            eq.setChestplateDropChance(0.0f);
            eq.setLeggingsDropChance(0.0f);
            eq.setBootsDropChance(0.0f);
        }
    }
}
