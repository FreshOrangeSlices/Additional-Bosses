package com.orangeslices.additionalbosses.raffle;

import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Lightweight debug logger for the raffle system.
 *
 * Usage:
 * - Call RaffleDebug.init(plugin) once in onEnable()
 * - Call RaffleDebug.setEnabled(true/false) based on config
 * - Use RaffleDebug.log("...") anywhere in raffle package
 */
public final class RaffleDebug {

    private static Logger logger;
    private static boolean enabled = false;

    private RaffleDebug() {}

    public static void init(Plugin plugin) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin cannot be null when initializing RaffleDebug.");
        }
        logger = plugin.getLogger();
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void log(String message) {
        if (!enabled) return;

        Logger l = (logger != null) ? logger : Logger.getLogger("Additional-Bosses");
        l.info("[RaffleDebug] " + message);
    }
}
