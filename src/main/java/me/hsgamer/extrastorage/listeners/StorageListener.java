package me.hsgamer.extrastorage.listeners;

import me.hsgamer.extrastorage.ExtraStorage;
import org.bukkit.event.Listener;

public class StorageListener implements Listener {
    private final ExtraStorage plugin;

    public StorageListener(ExtraStorage plugin) {
        this.plugin = plugin;
    }
}
