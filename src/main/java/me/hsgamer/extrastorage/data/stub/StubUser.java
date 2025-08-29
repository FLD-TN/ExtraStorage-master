package me.hsgamer.extrastorage.data.stub;

import me.hsgamer.extrastorage.api.storage.Storage;
import me.hsgamer.extrastorage.api.user.Partner;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.user.UserImpl;
import me.hsgamer.extrastorage.data.user.UserManager;
import me.hsgamer.topper.data.core.DataEntry;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

public class StubUser implements User {
    final DataEntry<UUID, UserImpl> entry;
    private final OfflinePlayer offlinePlayer;

    public StubUser(DataEntry<UUID, UserImpl> entry) {
        this.entry = entry;
        this.offlinePlayer = Bukkit.getOfflinePlayer(entry.getKey());
    }

    @Override
    public OfflinePlayer getOfflinePlayer() {
        return offlinePlayer;
    }

    @Override
    public Player getPlayer() {
        return offlinePlayer.getPlayer();
    }

    @Override
    public UUID getUUID() {
        return offlinePlayer.getUniqueId();
    }

    @Override
    public String getName() {
        return offlinePlayer.getName();
    }

    @Override
    public boolean isOnline() {
        return offlinePlayer.isOnline();
    }

    @Override
    public void save() {
        ((UserManager) entry.getHolder()).save(entry.getKey());
    }

    @Override
    public boolean hasPermission(String permission) {
        Player player = this.getPlayer();
        if ((player == null) || (!player.isOnline()))
            return false;
        return (player.isOp() || player.hasPermission(permission));
    }

    @Override
    public String getTexture() {
        return entry.getValue().texture;
    }

    @Override
    public Storage getStorage() {
        return new StubStorage(this);
    }

    @Override
    public Collection<Partner> getPartners() {
        return entry.getValue().partners.entrySet().stream()
                .map(entry -> new StubPartner(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean isPartner(UUID player) {
        return entry.getValue().partners.containsKey(player);
    }

    /**
     * Thời gian hết hạn tính bằng ms (30 phút)
     */
    private static final long REQUEST_EXPIRE_MS = 30 * 60 * 1000;

    @Override
    public boolean hasPendingPartnerRequest(String username) {
        String key = username.toLowerCase().trim();
        // Lấy map requests cho user này, hoặc tạo mới nếu chưa có
        Map<String, Long> pendingRequests = UserImpl.pendingPartnerRequestsMap.computeIfAbsent(getUUID(),
                k -> new ConcurrentHashMap<>());

        Long time = pendingRequests.get(key);
        if (time == null)
            return false;

        boolean valid = System.currentTimeMillis() - time < REQUEST_EXPIRE_MS;
        if (!valid) {
            // Remove expired request
            removePendingPartnerRequest(username);
        }
        return valid;
    }

    @Override
    public void addPendingPartnerRequest(String username) {
        String key = username.toLowerCase().trim();
        // Lấy map requests cho user này, hoặc tạo mới nếu chưa có
        Map<String, Long> pendingRequests = UserImpl.pendingPartnerRequestsMap.computeIfAbsent(getUUID(),
                k -> new ConcurrentHashMap<>());
        // Thêm request mới
        pendingRequests.put(key, System.currentTimeMillis());
    }

    @Override
    public void removePendingPartnerRequest(String username) {
        String key = username.toLowerCase().trim();
        // Lấy map requests cho user này, hoặc tạo mới nếu chưa có
        Map<String, Long> pendingRequests = UserImpl.pendingPartnerRequestsMap.computeIfAbsent(getUUID(),
                k -> new ConcurrentHashMap<>());
        // Xóa request
        pendingRequests.remove(key);
    }

    @Override
    public Collection<String> getPendingPartnerRequests() {
        long now = System.currentTimeMillis();
        // Lấy map requests cho user này, hoặc tạo mới nếu chưa có
        Map<String, Long> pendingRequests = UserImpl.pendingPartnerRequestsMap.computeIfAbsent(getUUID(),
                k -> new ConcurrentHashMap<>());

        // Remove expired requests first
        pendingRequests.entrySet().removeIf(entry -> now - entry.getValue() >= REQUEST_EXPIRE_MS);

        return pendingRequests.keySet().stream()
                .collect(Collectors.toList());
    }

    @Override
    public void addPartner(UUID player) {
        entry.setValue(user -> user.withPartner(player));
    }

    @Override
    public void removePartner(UUID player) {
        entry.setValue(user -> user.withPartnerRemoved(player));
    }

    @Override
    public void clearPartners() {
        entry.setValue(user -> user.withPartners(Collections.emptyMap()));
    }
}