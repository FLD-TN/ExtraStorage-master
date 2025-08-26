package me.hsgamer.extrastorage.configs;

import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.configs.types.BukkitConfig;

public class MessageConfig extends BukkitConfig {

    public MessageConfig(ExtraStorage plugin) {
        super(plugin, "messages.yml");
    }

    @Override
    public void setup() {
        // Không cần setup gì đặc biệt, chỉ cần tải file
    }
}