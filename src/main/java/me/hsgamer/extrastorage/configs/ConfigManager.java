package me.hsgamer.extrastorage.configs;

import me.hsgamer.extrastorage.ExtraStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final ExtraStorage plugin;
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();

    private Setting setting;

    public ConfigManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.setting = new Setting(plugin);
        reload();
    }

    public void reload() {
        // Save default config.yml
        plugin.saveDefaultConfig();
        // Ensure messages.yml exists and load with defaults
        File messageFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messageFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        // Load user messages
        FileConfiguration messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        // Load default messages from jar and apply defaults
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                messageConfig.setDefaults(defConfig);
                messageConfig.options().copyDefaults(true);
                messageConfig.save(messageFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to load default messages: " + e.getMessage());
            }
        }
        // Initialize messages
        Message.init(messageConfig);
        plugin.saveResource("worth.yml", false);


        // Xóa cache của GuiConfig để đảm bảo tải lại từ file
        me.hsgamer.extrastorage.gui.config.GuiConfig.clearCache();

        // Load GUI files
        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (!guiFolder.exists()) {
            guiFolder.mkdirs();
            saveDefaultGuis();
        }
        loadGuiConfigs();
    }

    private void saveDefaultGuis() {
        plugin.saveResource("gui/add_item_to_storage.yml", false);
        plugin.saveResource("gui/confirm_remove_partner.yml", false);
        plugin.saveResource("gui/filter.yml", false);
        plugin.saveResource("gui/partner_request.yml", false);
        plugin.saveResource("gui/partner.yml", false);
        plugin.saveResource("gui/sell.yml", false);
        plugin.saveResource("gui/storage.yml", false);
        plugin.saveResource("gui/whitelist.yml", false);
    }

    private void loadGuiConfigs() {
        guiConfigs.clear();
        File guiFolder = new File(plugin.getDataFolder(), "gui");
        if (guiFolder.exists()) {
            File[] files = guiFolder.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) {
                    String name = file.getName().replace(".yml", "");
                    guiConfigs.put(name, YamlConfiguration.loadConfiguration(file));
                }
            }
        }
    }

    public FileConfiguration getGuiConfig(String name) {
        return guiConfigs.get(name);
    }
}
