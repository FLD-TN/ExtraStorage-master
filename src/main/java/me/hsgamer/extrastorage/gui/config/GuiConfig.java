package me.hsgamer.extrastorage.gui.config;

import me.hsgamer.extrastorage.configs.types.BukkitConfig;
import me.hsgamer.extrastorage.gui.abstraction.GuiCreator;
import me.hsgamer.extrastorage.util.Digital;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;

public class GuiConfig
        extends BukkitConfig {

    protected String title;
    protected int rows;
    protected Sound sound;

    // Cache để lưu các file đã tải
    private static final java.util.Map<String, Long> fileLastModified = new java.util.HashMap<>();

    public GuiConfig(String fileName) {
        super(fileName + ".yml");

        // Luôn tải lại cấu hình từ file
        reload();

        // Cập nhật cache
        fileLastModified.put(file.getPath(), file.lastModified());
    }

    @Override
    public void setup() {
        this.title = this.config.getString("Settings.Title", "§lNo Title");
        this.rows = Digital.getBetween(9, 54, this.config.getInt("Settings.Rows") * 9);

        String soundName = this.config.getString("Settings.Sound", "unknown").toUpperCase();
        try {
            this.sound = Sound.valueOf(soundName);
        } catch (Exception ignored) {
        }
    }

    /**
     * Xóa cache cho tất cả các file cấu hình GUI
     */
    public static void clearCache() {
        fileLastModified.clear();
    }

    @Override
    public void reload() {
        // Load lại file cấu hình
        super.reload();

        // Setup lại các thông số GUI
        this.colorize(config);
        this.setup();

        // Tạo lại inventory nếu là GuiCreator
        if (this instanceof GuiCreator) {
            ((GuiCreator) this).createNewInventory();
        }

        // Cập nhật cache
        fileLastModified.put(file.getPath(), file.lastModified());
    }
}
