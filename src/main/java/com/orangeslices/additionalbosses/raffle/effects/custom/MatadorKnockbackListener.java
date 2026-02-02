package com.orangeslices.additionalbosses.raffle.effects.custom;

import com.orangeslices.additionalbosses.AdditionalBossesPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zoglin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Reliable knockback for Matador.
 *
 * Zoglins don't consistently apply held-item enchant knockback.
 * So we apply a controlled push when a tagged Matador Zoglin hits a player.
 */
public final class MatadorKnockbackListener implements Listener {

    private final NamespacedKey matadorBullKey;

    // tweak feel here
    private static final double HORIZONTAL_STRENGTH = 1.10;
    private static final double VERTICAL_STRENGTH = 0.22;

    public MatadorKnockbackListener(AdditionalBossesPlugin plugin) {
        this.matadorBullKey = new NamespacedKey(plugin, "matador_bull");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMatadorBullHit(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (!(damager instanceof Zoglin bull)) return;
        if (!(victim instanceof Player player)) return;

        Byte tagged = bull.getPersistentDataContainer().get(matadorBullKey, PersistentDataType.BYTE);
        if (tagged == null || tagged != (byte) 1) return;

        Vector dir = player.getLocation().toVector().subtract(bull.getLocation().toVector());
        if (dir.lengthSquared() < 0.0001) return;

        dir.normalize().multiply(HORIZONTAL_STRENGTH);

        double y = Math.max(player.getVelocity().getY(), VERTICAL_STRENGTH);
        player.setVelocity(new Vector(dir.getX(), y, dir.getZ()));
    }
}
