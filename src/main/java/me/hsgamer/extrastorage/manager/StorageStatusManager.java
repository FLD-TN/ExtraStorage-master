package me.hsgamer.extrastorage.manager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class StorageStatusManager {
    private static final StorageStatusManager instance = new StorageStatusManager();
    private final Map<UUID, Boolean> storageStatusMap = new ConcurrentHashMap<>();

    private StorageStatusManager() {
    }

    public static StorageStatusManager getInstance() {
        return instance;
    }

    public boolean getStatus(UUID playerId) {
        // Mặc định là true (bật) nếu chưa có trong map
        return storageStatusMap.getOrDefault(playerId, true);
    }

    public void setStatus(UUID playerId, boolean status) {
        storageStatusMap.put(playerId, status);
    }

    public void toggleStatus(UUID playerId) {
        boolean currentStatus = getStatus(playerId);
        setStatus(playerId, !currentStatus);
    }

    public void removeStatus(UUID playerId) {
        storageStatusMap.remove(playerId);
    }

    public void clearAll() {
        storageStatusMap.clear();
    }
}