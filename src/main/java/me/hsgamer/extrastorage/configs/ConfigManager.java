package me.hsgamer.extrastorage.configs;

import me.hsgamer.extrastorage.ExtraStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {
    private final ExtraStorage plugin;
    private final Map<String, FileConfiguration> guiConfigs = new HashMap<>();
    private final Setting setting;
    private final MaterialTypeConfig materialTypeConfig;

    public ConfigManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.setting = new Setting(plugin);
        this.materialTypeConfig = MaterialTypeConfig.getInstance(plugin);
        reload();
    }

    public Setting getSetting() {
        return setting;
    }

    public MaterialTypeConfig getMaterialTypeConfig() {
        return materialTypeConfig;
    }

    public void reload() {
        // Save default config.yml and ensure data folder
        plugin.saveDefaultConfig();
        plugin.getDataFolder().mkdirs();

        // Setup settings
        setting.setup();

        // Process messages.yml
        plugin.saveResource("messages.yml", false);
        File messageFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messageFile.exists()) {
            plugin.getLogger().warning("messages.yml file not found! Creating a new one...");
            try {
                messageFile.createNewFile();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to create messages.yml: " + e.getMessage());
            }
        }
        FileConfiguration messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        Message.init(messageConfig);

        // Load material types
        materialTypeConfig.loadConfig();

        // Ensure worth.yml is copied
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
        try {
            plugin.saveResource("gui/add_item_to_storage.yml", false);
            plugin.saveResource("gui/confirm_remove_partner.yml", false);
            plugin.saveResource("gui/confirm_unfilter.yml", false);
            plugin.saveResource("gui/filter_manager.yml", false);
            plugin.saveResource("gui/filter.yml", false);
            plugin.saveResource("gui/partner_request.yml", false);
            plugin.saveResource("gui/partner.yml", false);
            plugin.saveResource("gui/sell.yml", false);
            plugin.saveResource("gui/storage.yml", false);
            plugin.saveResource("gui/whitelist.yml", false);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save GUI files: " + e.getMessage());
            e.printStackTrace();
        }
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