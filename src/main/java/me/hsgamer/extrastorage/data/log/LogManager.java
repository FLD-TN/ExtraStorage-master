package me.hsgamer.extrastorage.data.log;

import me.hsgamer.extrastorage.ExtraStorage;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class LogManager {
    private final ExtraStorage plugin;
    private final File logFolder;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public LogManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.logFolder = new File(plugin.getDataFolder(), "logs");
        if (!logFolder.exists()) {
            logFolder.mkdirs();
        }
    }

    // Base logging methods
    public void log(String type, String player, String message) {
        File logFile = new File(logFolder, "storage.log");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            String date = dateFormat.format(new Date());
            writer.write(String.format("[%s] [%s] %s: %s%n", date, type, player, message));
        } catch (IOException e) {
            plugin.getLogger().warning("Could not write to log file: " + e.getMessage());
        }
    }

    // Standard logging methods
    public void logTransaction(String player, String item, long amount, String action) {
        log("TRANSACTION", player, String.format("%s %d %s", action, amount, item));
    }

    public void logError(String player, String error) {
        log("ERROR", player, error);
    }

    public void logFilter(String player, String item, boolean added) {
        log("FILTER", player, String.format("%s %s", added ? "added" : "removed", item));
    }

    // Player and OfflinePlayer based logging methods
    public void log(Player player, OfflinePlayer target, Log.Action action, String item, int amount, double price) {
        Log logger = new Log(plugin);
        logger.log(player, target, action, item, amount, price);
    }

    public void log(Player player, OfflinePlayer target, Log.Action action, String item, int amount, int points) {
        Log logger = new Log(plugin);
        logger.log(player, target, action, item, amount, Double.valueOf(points));
    }

    public void log(Player player, OfflinePlayer target, Log.Action action, String item, int amount, long tokens) {
        Log logger = new Log(plugin);
        logger.log(player, target, action, item, amount, Double.valueOf(tokens));
    }
}
