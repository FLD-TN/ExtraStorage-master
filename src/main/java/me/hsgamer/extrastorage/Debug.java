package me.hsgamer.extrastorage;

import org.bukkit.plugin.java.JavaPlugin;

public class Debug {
    public static boolean enabled = false;

    public static void log(String... messages) {
        if (enabled) {
            JavaPlugin plugin = ExtraStorage.getInstance();
            for (String message : messages) {
                plugin.getLogger().info("[Debug] " + message);
            }
        }
    }

    public static void debug(String message, Exception e) {
        if (enabled) {
            JavaPlugin plugin = ExtraStorage.getInstance();
            plugin.getLogger().warning("[Debug] " + message);
            if (e != null) {
                e.printStackTrace();
            }
        }
    }
}
