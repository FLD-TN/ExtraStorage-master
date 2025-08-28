package me.hsgamer.extrastorage.hooks.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.item.Item;
import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.util.Digital;
import org.bukkit.OfflinePlayer;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class ESPlaceholder extends PlaceholderExpansion {
    private final ExtraStorage instance; // Thêm biến instance
    private static final Cache<String, String> PLACEHOLDER_CACHE = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .build();

    // Thêm constructor
    public ESPlaceholder(ExtraStorage instance) {
        this.instance = instance;
    }

    @Override
    public String getIdentifier() {
        return "exstorage";
    }

    @Override
    public String getAuthor() {
        return "HyronicTeam";
    }

    @Override
    public String getVersion() {
        return instance.getDescription().getVersion(); // Sửa thành instance
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String args) {
        if (!player.isOnline())
            return null;

        String cacheKey = player.getUniqueId() + ":" + args;
        try {
            return PLACEHOLDER_CACHE.get(cacheKey, () -> calculatePlaceholder(player, args));
        } catch (Exception e) {
            return calculatePlaceholder(player, args);
        }
    }

    private String calculatePlaceholder(OfflinePlayer player, String args) {
        String argsLowerCase = args.toLowerCase();
        Storage storage = instance.getUserManager().getUser(player).getStorage(); // Sửa thành instance

        switch (argsLowerCase) {
            case "space":
                return (storage.getSpace() == -1) ? "-1" : Long.toString(storage.getSpace());
            case "space_formatted":
                return (storage.getSpace() == -1) ? "-1" : Digital.formatThousands(storage.getSpace());
            case "used_space":
                return Long.toString(storage.getUsedSpace());
            case "used_space_formatted":
                return Digital.formatThousands(storage.getUsedSpace());
            case "free_space":
                return (storage.getFreeSpace() == -1) ? "-1" : Long.toString(storage.getFreeSpace());
            case "free_space_formatted":
                return (storage.getFreeSpace() == -1) ? "-1" : Digital.formatThousands(storage.getFreeSpace());
            case "used_percent":
                return (storage.getSpaceAsPercent(true) == -1) ? "-1"
                        : Double.toString(storage.getSpaceAsPercent(true));
            case "used_percent_formatted":
                return (storage.getSpaceAsPercent(true) == -1) ? "-1" : (storage.getSpaceAsPercent(true) + "%");
            case "free_percent":
                return (storage.getSpaceAsPercent(false) == -1) ? "-1"
                        : Double.toString(storage.getSpaceAsPercent(false));
            case "free_percent_formatted":
                return (storage.getSpaceAsPercent(false) == -1) ? "-1" : (storage.getSpaceAsPercent(false) + "%");
        }

        if (argsLowerCase.startsWith("quantity")) {
            String key = args.substring(args.indexOf('_') + 1);
            boolean isFormatted = key.toUpperCase().startsWith("FORMATTED");
            if (isFormatted)
                key = key.substring(key.indexOf('_') + 1);

            Optional<Item> item = storage.getItem(key);

            // QUAN TRỌNG: Kiểm tra cả filtered status và quantity
            if (!item.isPresent() || (!item.get().isFiltered() && item.get().getQuantity() == 0)) {
                return "0";
            }

            long quantity = item.get().getQuantity();
            if (isFormatted) {
                return Digital.formatThousands(Math.min(quantity, Integer.MAX_VALUE));
            } else {
                return Long.toString(quantity);
            }
        }

        return null;
    }

    public static void clearCacheForPlayer(UUID playerId) {
        PLACEHOLDER_CACHE.asMap().keySet().removeIf(key -> key.startsWith(playerId.toString()));
    }
}