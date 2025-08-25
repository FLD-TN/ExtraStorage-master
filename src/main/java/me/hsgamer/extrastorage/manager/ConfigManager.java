package me.hsgamer.extrastorage.manager;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.configs.Setting;
import me.hsgamer.extrastorage.configs.Message;
import me.hsgamer.extrastorage.configs.MaterialTypeConfig;

public class ConfigManager {
    private final ExtraStorage plugin;
    private final Setting setting;
    private final MaterialTypeConfig materialTypeConfig;

    public ConfigManager(ExtraStorage plugin) {
        this.plugin = plugin;
        this.setting = new Setting(plugin);
        this.materialTypeConfig = MaterialTypeConfig.getInstance(plugin);

        this.setting.setup();
        Message.init(plugin.getConfig().getConfigurationSection("messages"));
    }

    public Setting getSetting() {
        return setting;
    }

    public MaterialTypeConfig getMaterialTypeConfig() {
        return materialTypeConfig;
    }

    public void reload() {
        this.setting.setup();
        Message.init(plugin.getConfig().getConfigurationSection("messages"));
        this.materialTypeConfig.loadConfig();
    }
}
