package me.hsgamer.extrastorage.manager;

import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.util.ItemUtil;

import java.util.*;
import java.util.stream.Collectors;

public class ItemManager {
    private final Map<String, Item> items = new HashMap<>();

    public void registerItem(Item item) {
        items.put(item.getKey(), item);
    }

    public void unregisterItem(Item item) {
        items.remove(item.getKey());
    }

    public Optional<Item> getItem(String key) {
        return Optional.ofNullable(items.get(key));
    }

    public Collection<Item> getItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    public Collection<Item> getItems(ItemUtil.ItemType type) {
        return items.values().stream()
                .filter(item -> item.getType() == type)
                .collect(Collectors.toList());
    }
}
