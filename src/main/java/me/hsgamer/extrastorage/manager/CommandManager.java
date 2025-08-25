package me.hsgamer.extrastorage.manager;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.commands.abstraction.CommandListener;
import me.hsgamer.extrastorage.commands.AdminCommands;
import me.hsgamer.extrastorage.commands.PlayerCommands;

public class CommandManager {
    private final ExtraStorage plugin;
    private final CommandListener playerCommands;
    private final CommandListener adminCommands;

    public CommandManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.playerCommands = new PlayerCommands();
        this.adminCommands = new AdminCommands();
    }

    public void registerCommands() {
        if (plugin.getCommand("storage") != null) {
            plugin.getCommand("storage").setExecutor(playerCommands);
            plugin.getCommand("storage").setTabCompleter(playerCommands);
        }
        if (plugin.getCommand("storageadmin") != null) {
            plugin.getCommand("storageadmin").setExecutor(adminCommands);
            plugin.getCommand("storageadmin").setTabCompleter(adminCommands);
        }
    }

    public void unregisterCommands() {
        if (plugin.getCommand("storage") != null) {
            plugin.getCommand("storage").setExecutor(null);
            plugin.getCommand("storage").setTabCompleter(null);
        }
        if (plugin.getCommand("storageadmin") != null) {
            plugin.getCommand("storageadmin").setExecutor(null);
            plugin.getCommand("storageadmin").setTabCompleter(null);
        }
    }
}
