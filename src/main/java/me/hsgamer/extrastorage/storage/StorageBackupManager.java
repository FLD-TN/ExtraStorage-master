package me.hsgamer.extrastorage.storage;

import me.hsgamer.extrastorage.Debug;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.data.user.UserImpl;
import me.hsgamer.extrastorage.data.user.UserManager;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Quản lý sao lưu và khôi phục dữ liệu kho
 */
public class StorageBackupManager {
    private static final ScheduledExecutorService backupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "StorageBackupManager");
        thread.setDaemon(true);
        return thread;
    });

    private static final String BACKUP_FOLDER = "backups";
    private static final int MAX_BACKUPS = 5;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private final ExtraStorage plugin;
    private final File backupDir;
    private File lastBackupFile;
    private long lastBackupTime = 0;

    public StorageBackupManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.backupDir = new File(plugin.getDataFolder(), BACKUP_FOLDER);
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    /**
     * Khởi động hệ thống sao lưu tự động
     */
    public void startAutomaticBackup() {
        // Backup mỗi 30 phút
        backupExecutor.scheduleAtFixedRate(() -> {
            try {
                createBackup("scheduled");
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "Không thể tạo bản sao lưu tự động", e);
            }
        }, 30, 30, TimeUnit.MINUTES);

        plugin.getLogger().info("Đã bật tính năng sao lưu tự động (mỗi 30 phút)");
    }

    /**
     * Tạo sao lưu mới
     */
    public CompletableFuture<File> createBackup(String reason) {
        CompletableFuture<File> future = new CompletableFuture<>();

        // Nếu sao lưu quá sớm, bỏ qua
        if (System.currentTimeMillis() - lastBackupTime < TimeUnit.MINUTES.toMillis(5)) {
            future.complete(lastBackupFile);
            return future;
        }

        backupExecutor.submit(() -> {
            try {
                String timestamp = DATE_FORMAT.format(new Date());
                File backupFile = new File(backupDir, "storage_backup_" + timestamp + "_" + reason + ".zip");

                // Backup SQLite database
                if (plugin.getSetting().getDBType().equalsIgnoreCase("sqlite")) {
                    File dbFile = new File(plugin.getDataFolder(),
                            plugin.getSetting().getSqlDatabaseSetting().getDatabase());
                    if (dbFile.exists()) {
                        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
                            addFileToZip(zos, dbFile, "database.db");

                            // Thêm file trạng thái
                            File stateFile = new File(plugin.getDataFolder(), "backup_state.yml");
                            YamlConfiguration config = new YamlConfiguration();
                            config.set("backup_time", System.currentTimeMillis());
                            config.set("reason", reason);
                            config.set("plugin_version", plugin.getDescription().getVersion());
                            config.save(stateFile);
                            addFileToZip(zos, stateFile, "backup_state.yml");
                            stateFile.delete();
                        }
                    }
                } else {
                    // MySQL - sao lưu các cache dữ liệu quan trọng
                    File cacheFile = new File(plugin.getDataFolder(), "mysql_cache_backup.yml");
                    YamlConfiguration config = new YamlConfiguration();

                    // Lưu các thông tin cache
                    UserManager userManager = plugin.getUserManager();
                    if (userManager != null) {
                        int savedCount = 0;
                        for (UUID uuid : userManager.getEntryMap().keySet()) {
                            try {
                                // Lưu cache của người chơi
                                config.set("users." + uuid.toString(), "cached");
                                savedCount++;
                            } catch (Exception e) {
                                Debug.log("Failed to save cache for " + uuid + ": " + e.getMessage());
                            }
                        }
                        config.set("saved_count", savedCount);
                    }

                    config.save(cacheFile);

                    try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
                        addFileToZip(zos, cacheFile, "mysql_cache.yml");
                    }

                    cacheFile.delete();
                }

                plugin.getLogger().info("Đã tạo bản sao lưu: " + backupFile.getName());
                lastBackupFile = backupFile;
                lastBackupTime = System.currentTimeMillis();

                // Xóa các bản sao lưu cũ
                cleanupOldBackups();

                future.complete(backupFile);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Lỗi khi tạo bản sao lưu", e);
                future.completeExceptionally(e);
            }
        });

        return future;
    }

    /**
     * Thử khôi phục dữ liệu nếu phát hiện server crash
     */
    public boolean recoverFromCrash() {
        File crashMarker = new File(plugin.getDataFolder(), "crash_detected.marker");

        if (crashMarker.exists()) {
            plugin.getLogger().warning("Phát hiện server crash trước đó! Đang kiểm tra tính toàn vẹn dữ liệu...");

            // Tìm bản sao lưu gần nhất
            File latestBackup = findLatestBackup();
            if (latestBackup != null) {
                plugin.getLogger().info("Đã tìm thấy bản sao lưu gần nhất: " + latestBackup.getName());

                // Nếu cần thiết, có thể thêm mã để khôi phục từ bản sao lưu này
                // Hiện tại chỉ ghi log để admin tự quyết định

                crashMarker.delete();
                return true;
            } else {
                plugin.getLogger().warning("Không tìm thấy bản sao lưu gần nhất!");
                crashMarker.delete();
                return false;
            }
        }

        // Tạo marker để phát hiện crash lần sau
        try {
            crashMarker.createNewFile();

            // Tạo bản sao lưu khi plugin bắt đầu
            createBackup("startup");

            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Không thể tạo crash marker", e);
            return false;
        }
    }

    /**
     * Tìm bản sao lưu gần nhất
     */
    private File findLatestBackup() {
        File[] backups = backupDir
                .listFiles((dir, name) -> name.startsWith("storage_backup_") && name.endsWith(".zip"));
        if (backups == null || backups.length == 0) {
            return null;
        }

        // Sắp xếp theo thời gian sửa đổi
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified).reversed());
        return backups[0];
    }

    /**
     * Xóa các bản sao lưu cũ để tiết kiệm dung lượng
     */
    private void cleanupOldBackups() {
        File[] backups = backupDir
                .listFiles((dir, name) -> name.startsWith("storage_backup_") && name.endsWith(".zip"));
        if (backups == null || backups.length <= MAX_BACKUPS) {
            return;
        }

        // Sắp xếp theo thời gian sửa đổi
        Arrays.sort(backups, Comparator.comparingLong(File::lastModified));

        // Xóa các bản sao lưu cũ nhất
        for (int i = 0; i < backups.length - MAX_BACKUPS; i++) {
            if (backups[i].delete()) {
                plugin.getLogger().info("Đã xóa bản sao lưu cũ: " + backups[i].getName());
            }
        }
    }

    /**
     * Đóng và dọn dẹp
     */
    public void shutdown() {
        try {
            // Sao lưu trước khi tắt
            File backup = createBackup("shutdown").get(30, TimeUnit.SECONDS);
            plugin.getLogger().info("Đã tạo bản sao lưu trước khi tắt: " + backup.getName());

            // Xóa crash marker vì plugin đã tắt đúng cách
            File crashMarker = new File(plugin.getDataFolder(), "crash_detected.marker");
            if (crashMarker.exists()) {
                crashMarker.delete();
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Không thể tạo bản sao lưu khi tắt", e);
        }

        backupExecutor.shutdown();
        try {
            if (!backupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Thêm file vào zip
     */
    private void addFileToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, length);
            }

            zos.closeEntry();
        }
    }
}
