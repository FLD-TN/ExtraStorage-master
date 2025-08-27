package me.hsgamer.extrastorage;

import me.hsgamer.extrastorage.commands.AdminCommands;
import me.hsgamer.extrastorage.commands.PlayerCommands;
import me.hsgamer.extrastorage.commands.completion.AdminTabCompleter; // Đảm bảo đã import
import me.hsgamer.extrastorage.data.user.UserManager;
import me.hsgamer.extrastorage.data.worth.WorthManager;
import me.hsgamer.extrastorage.data.log.LogManager;
import me.hsgamer.extrastorage.configs.*;
import me.hsgamer.extrastorage.hooks.placeholder.ESPlaceholder;
import me.hsgamer.extrastorage.listeners.*;
import me.hsgamer.extrastorage.configs.ConfigManager;
import me.hsgamer.extrastorage.metrics.PluginMetrics;
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
    private me.hsgamer.extrastorage.data.NotificationManager notificationManager;

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

    private PluginMetrics metrics;

    public PluginMetrics getMetrics() {
        return metrics;
    }

    public LogManager getLog() {
        return logManager;
    }

    public me.hsgamer.extrastorage.data.NotificationManager getNotificationManager() {
        return notificationManager;
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
        metrics = new PluginMetrics(this);

        // Load configs through ConfigManager
        configManager = new ConfigManager(this);
        this.setting = configManager.getSetting();
        this.materialTypeConfig = configManager.getMaterialTypeConfig();

        // Initialize other managers
        userManager = new UserManager(this);
        worthManager = new WorthManager();
        logManager = new LogManager(this);
        notificationManager = new me.hsgamer.extrastorage.data.NotificationManager(this);

        // Register commands and tab completers
        PlayerCommands playerCommands = new PlayerCommands();
        getCommand("extrastorage").setExecutor(playerCommands);
        getCommand("extrastorage").setTabCompleter(playerCommands);

        AdminCommands adminCommands = new AdminCommands();
        getCommand("esadmin").setExecutor(adminCommands);
        // SỬA LỖI: Đăng ký AdminTabCompleter
        getCommand("esadmin").setTabCompleter(new AdminTabCompleter(this));

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

        scheduleCleanupTask();

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
        getServer().getPluginManager().registerEvents(new PickupListener(this), this);
        getServer().getPluginManager().registerEvents(new PlayerDropListener(this), this);
        getServer().getPluginManager().registerEvents(new EntityDeathListener(this), this);
    }

    private void scheduleCleanupTask() {
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (userManager != null) {
                userManager.cleanup();
            }
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    @Override
    public void onDisable() {
        if (userManager != null) {
            userManager.cleanup();
        }

        getLogger().info("=========================");
        getLogger().info("ExtraStorage v" + getDescription().getVersion());
        getLogger().info("Status: Disabled");
        getLogger().info("=========================");
    }
}