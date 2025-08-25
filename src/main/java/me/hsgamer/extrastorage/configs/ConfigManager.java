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

    private Setting setting;

    public ConfigManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.setting = new Setting(plugin);
        reload();
    }

    public void reload() {
        // Save default config files
        plugin.saveDefaultConfig();
        plugin.saveResource("messages.yml", false);
        plugin.saveResource("worth.yml", false);

        // Initialize messages
        File messageFile = new File(plugin.getDataFolder(), "messages.yml");
        FileConfiguration messageConfig = YamlConfiguration.loadConfiguration(messageFile);
        Message.init(messageConfig);

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
