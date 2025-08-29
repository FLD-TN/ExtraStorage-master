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
import me.hsgamer.extrastorage.metrics.PluginMetrics;
import me.hsgamer.extrastorage.storage.StorageBackupManager;
import me.hsgamer.extrastorage.storage.StorageSafetyManager;
import me.hsgamer.extrastorage.storage.TransactionLogger;
import me.hsgamer.extrastorage.util.PerformanceOptimizer;
import me.hsgamer.extrastorage.util.ItemFilterService;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExtraStorage extends JavaPlugin {
    private static ExtraStorage instance;
    private static boolean filterEnabled = true;
    private static final long CLEANUP_INTERVAL = 20 * 60 * 30; // 30 phút
    private static final long MONITORING_INTERVAL = 20 * 300; // 5 phút

    private boolean firstLoad = true;
    private ConfigManager configManager;
    private UserManager userManager;
    private WorthManager worthManager;
    private LogManager logManager;
    private ESPlaceholder placeholder;
    private Setting setting;
    private MaterialTypeConfig materialTypeConfig;
    private me.hsgamer.extrastorage.data.NotificationManager notificationManager;
    private PerformanceOptimizer performanceOptimizer;
    private StorageBackupManager storageBackupManager;

    public static ExtraStorage getInstance() {
        return instance;
    }

    public static boolean isFilterEnabled() {
        return filterEnabled;
    }

    public static void setFilterEnabled(boolean enabled) {
        filterEnabled = enabled;
        ItemFilterService.clearAllCache();
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

        // Initialize performance optimizer và backup manager
        performanceOptimizer = new PerformanceOptimizer(this);
        storageBackupManager = new StorageBackupManager(this);

        // Kiểm tra và vá lỗi cache filter
        ItemFilterService.validateAndRepairCache();
        getLogger().info("Kiểm tra và vá lỗi cache filter hoàn tất");

        // Kiểm tra và phục hồi dữ liệu nếu phát hiện server crash
        if (storageBackupManager.recoverFromCrash()) {
            getLogger().info("Kiểm tra tính toàn vẹn dữ liệu hoàn tất");
        } else {
            getLogger().warning("Có vấn đề trong quá trình kiểm tra tính toàn vẹn dữ liệu!");
        }

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
        startPerformanceMonitoring();

        // Bắt đầu hệ thống sao lưu tự động
        storageBackupManager.startAutomaticBackup();

        // Thay đổi thời gian auto-save từ 1 phút xuống 30 giây để đảm bảo dữ liệu được
        // lưu thường xuyên hơn
        if (userManager != null) {
            getLogger().info("Đã bật chế độ tự động lưu dữ liệu thường xuyên hơn (mỗi 30 giây)");
        }

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
                userManager.cleanupCache(); // ⚡ Cleanup user cache
            }

            // ⚡ Cleanup các component khác
            StorageSafetyManager.cleanupStaleLocks();
            TransactionLogger.cleanupOldTransactions();
            ItemFilterService.performCacheCleanup(); // Sử dụng phương thức mới thay vì clearAllCache()

            // Cleanup expired pending requests
            cleanupExpiredPendingRequests();
        }, CLEANUP_INTERVAL, CLEANUP_INTERVAL);
    }

    // THÊM: Method để cleanup expired pending requests
    private void cleanupExpiredPendingRequests() {
        for (me.hsgamer.extrastorage.api.user.User user : userManager.getUsers()) {
            // Gọi getPendingPartnerRequests() sẽ tự động remove expired requests
            user.getPendingPartnerRequests();
        }
        getLogger().info("Cleaned up expired pending partner requests");
    }

    // ⚡ Thêm performance monitoring
    private void startPerformanceMonitoring() {
        getServer().getScheduler().runTaskTimerAsynchronously(this, () -> {
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            long maxMemory = runtime.maxMemory() / 1024 / 1024;

            getLogger().info("=== Performance Monitoring ===");
            getLogger().info("Memory Usage: " + usedMemory + "MB / " + maxMemory + "MB");

            if (userManager != null) {
                getLogger().info("User Cache Size: " + userManager.getCacheSize());
            }

            getLogger().info("Filter Cache Size: " + ItemFilterService.getCacheSize());
            getLogger().info("Pending Transactions: " + TransactionLogger.getPendingTransactions().size());
            getLogger().info("==============================");

            // Làm sạch cache cũ để tránh rò rỉ bộ nhớ
            ItemFilterService.performCacheCleanup();
        }, MONITORING_INTERVAL, MONITORING_INTERVAL);
    }

    // ⚡ Thêm method để get cache size
    public int getCacheSize() {
        return userManager != null ? userManager.getCacheSize() : 0;
    }

    @Override
    public void onDisable() {
        // Đảm bảo lưu tất cả dữ liệu người dùng TRƯỚC KHI backup
        if (userManager != null) {
            getLogger().info("Đang lưu dữ liệu người dùng...");
            userManager.forceSaveAll(); // Sử dụng forceSaveAll để đảm bảo tất cả dữ liệu được lưu
        }

        // Tạo bản sao lưu trước khi tắt plugin
        if (storageBackupManager != null) {
            getLogger().info("Đang tạo bản sao lưu dữ liệu trước khi tắt...");
            storageBackupManager.shutdown();
        }

        // Dừng và dọn dẹp
        if (userManager != null) {
            userManager.stop();
        }

        // Clear all caches
        ItemFilterService.clearAllCache();

        getLogger().info("=========================");
        getLogger().info("ExtraStorage v" + getDescription().getVersion());
        getLogger().info("Status: Disabled");
        getLogger().info("=========================");
    }
}