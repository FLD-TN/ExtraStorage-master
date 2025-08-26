package me.hsgamer.extrastorage;

import me.hsgamer.extrastorage.commands.AdminCommands;
import me.hsgamer.extrastorage.commands.PlayerCommands;
import me.hsgamer.extrastorage.data.user.UserManager;
import me.hsgamer.extrastorage.data.worth.WorthManager;
import me.hsgamer.extrastorage.data.log.LogManager;
import me.hsgamer.extrastorage.configs.*;
import me.hsgamer.extrastorage.hooks.placeholder.ESPlaceholder;
import me.hsgamer.extrastorage.listeners.*;
import me.hsgamer.extrastorage.metrics.PluginMetrics;
import me.hsgamer.extrastorage.listeners.PickupListener;
import me.hsgamer.extrastorage.listeners.ItemSpawnListener;
import me.hsgamer.extrastorage.listeners.PlayerDropListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraStorage extends JavaPlugin {
    private static ExtraStorage instance;
    private static boolean filterEnabled = true;
    private static final long CLEANUP_INTERVAL = 20 * 60 * 30; // 30 mins

    private boolean firstLoad = true;
    private ConfigManager configManager;
    private UserManager userManager;
    private WorthManager worthManager;
    private LogManager logManager;
    private ESPlaceholder placeholder;
    private Setting setting;
    private MaterialTypeConfig materialTypeConfig;

    public static ExtraStorage getInstance() {
        return instance;
    }

    public static boolean isFilterEnabled() {
        return filterEnabled;
    }

    public static void setFilterEnabled(boolean enabled) {
        filterEnabled = enabled;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public UserManager getUserManager() {
        return userManager;
    }

    public WorthManager getWorthManager() {
        return worthManager;
    }

    /**
     * Get the metrics system for tracking plugin usage
     */
    private PluginMetrics metrics;

    public PluginMetrics getMetrics() {
        return metrics;
    }

    /**
     * Get the log manager for transaction logging
     */
    public LogManager getLog() {
        return logManager;
    }

    public ESPlaceholder getPlaceholder() {
        return placeholder;
    }

    public Setting getSetting() {
        return setting;
    }

    public MaterialTypeConfig getMaterialTypeConfig() {
        return materialTypeConfig;
    }

    public void reloadGuiFiles() {
        if (configManager != null) {
            configManager.reload();
        }
    }

    @Override
    public void onLoad() {
        instance = this;
        firstLoad = !getDataFolder().exists();
    }

    @Override
    public void onEnable() {
        // Initialize metrics first so it's available for hooks
        metrics = new PluginMetrics(this);

        // Load settings first since other components need them
        setting = new Setting(this);
        materialTypeConfig = new MaterialTypeConfig(this);

        // Initialize managers
        configManager = new ConfigManager(this);
        userManager = new UserManager(this);
        worthManager = new WorthManager();
        logManager = new LogManager(this);

        // Register commands and tab completers
        PlayerCommands playerCommands = new PlayerCommands();
        getCommand("extrastorage").setExecutor(playerCommands);
        getCommand("extrastorage").setTabCompleter(playerCommands);

        AdminCommands adminCommands = new AdminCommands();
        getCommand("esadmin").setExecutor(adminCommands);
        getCommand("esadmin").setTabCompleter(adminCommands);

        // Hook with PlaceholderAPI if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholder = new ESPlaceholder(this);
            placeholder.register();
        }

        // Register listeners
        registerListeners();

        if (firstLoad) {
            getLogger().warning("It seems this is the first time this plugin is run on your server.");
            getLogger().warning(
                    "Please take a look at the 'Whitelist' option in the config.yml file before the player data is loaded.");
            getLogger().warning(
                    "You can find more information about the configuration at: https://github.com/HSGamer/ExtraStorage/wiki");
        }

        // Start cleanup task
        scheduleCleanupTask();

        // Log startup info
        getLogger().info("=========================");
        getLogger().info("ExtraStorage v" + getDescription().getVersion());
        getLogger().info("Author: HSGamer");
        getLogger().info("Status: Enabled");
        getLogger().info("=========================");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ItemListener(this), this);
        getServer().getPluginManager().registerEvents(new StorageListener(this), this);
        getServer().getPluginManager().registerEvents(new FilterListener(this), this);
        getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);
        
        // Đăng ký PickupListener riêng để xử lý nhặt vật phẩm vào kho
        new PickupListener(this);
        
        // Đăng ký PlayerDropListener để xử lý vật phẩm drop ra từ người chơi
        getServer().getPluginManager().registerEvents(new PlayerDropListener(this), this);
        
        // Đăng ký ItemSpawnListener để xử lý các vật phẩm spawn trong thế giới
        getServer().getPluginManager().registerEvents(new ItemSpawnListener(this), this);
    }

    private void scheduleCleanupTask() {
        // Schedule periodic cleanup task
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (userManager != null) {
                userManager.cleanup();
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    @Override
    public void onDisable() {
        // Save data before shutdown
        if (userManager != null) {
            userManager.cleanup();
        }

        getLogger().info("=========================");
        getLogger().info("ExtraStorage v" + getDescription().getVersion());
        getLogger().info("Status: Disabled");
        getLogger().info("=========================");
    }
}