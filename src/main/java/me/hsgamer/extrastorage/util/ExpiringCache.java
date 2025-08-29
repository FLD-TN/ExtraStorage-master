package me.hsgamer.extrastorage.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Lớp hỗ trợ quản lý cache với khả năng tự động hết hạn
 * 
 * @param <K> Kiểu của key
 * @param <V> Kiểu của value
 */
public class ExpiringCache<K, V> {
    private final ConcurrentHashMap<K, CacheEntry<V>> cacheMap = new ConcurrentHashMap<>();
    private final long defaultExpiryMs;
    private final BiConsumer<K, V> onExpiry;
    private final Plugin plugin;
    private final int cleanupInterval;
    private boolean isRegistered = false;

    /**
     * Constructor với các tham số tùy chỉnh
     * 
     * @param plugin                 Plugin sử dụng cache
     * @param defaultExpiryMs        Thời gian hết hạn mặc định (ms)
     * @param cleanupIntervalSeconds Khoảng thời gian giữa các lần dọn dẹp (giây)
     * @param onExpiry               Hàm được gọi khi một mục cache hết hạn
     */
    public ExpiringCache(Plugin plugin, long defaultExpiryMs, int cleanupIntervalSeconds, BiConsumer<K, V> onExpiry) {
        this.plugin = plugin;
        this.defaultExpiryMs = defaultExpiryMs;
        this.cleanupInterval = cleanupIntervalSeconds * 20; // Convert to ticks
        this.onExpiry = onExpiry;
    }

    /**
     * Constructor đơn giản với thời gian hết hạn mặc định
     */
    public ExpiringCache(Plugin plugin, long defaultExpiryMs) {
        this(plugin, defaultExpiryMs, 60, null); // 60 giây cleanup mặc định
    }

    /**
     * Đăng ký task dọn dẹp tự động
     */
    public void registerCleanupTask() {
        if (!isRegistered) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::cleanup, cleanupInterval, cleanupInterval);
            isRegistered = true;
        }
    }

    /**
     * Thêm một mục vào cache
     */
    public void put(K key, V value) {
        put(key, value, defaultExpiryMs);
    }

    /**
     * Thêm một mục vào cache với thời gian hết hạn cụ thể
     */
    public void put(K key, V value, long expiryMs) {
        cacheMap.put(key, new CacheEntry<>(value, System.currentTimeMillis() + expiryMs));
    }

    /**
     * Lấy giá trị từ cache
     * 
     * @return Giá trị hoặc null nếu không tồn tại hoặc đã hết hạn
     */
    public V get(K key) {
        CacheEntry<V> entry = cacheMap.get(key);
        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() > entry.expiryTime) {
            cacheMap.remove(key);
            if (onExpiry != null) {
                onExpiry.accept(key, entry.value);
            }
            return null;
        }

        return entry.value;
    }

    /**
     * Xóa một mục khỏi cache
     */
    public void remove(K key) {
        cacheMap.remove(key);
    }

    /**
     * Làm trống toàn bộ cache
     */
    public void clear() {
        cacheMap.clear();
    }

    /**
     * Dọn dẹp các mục đã hết hạn
     */
    public void cleanup() {
        long currentTime = System.currentTimeMillis();
        cacheMap.forEach((key, entry) -> {
            if (currentTime > entry.expiryTime) {
                cacheMap.remove(key);
                if (onExpiry != null) {
                    onExpiry.accept(key, entry.value);
                }
            }
        });
    }

    /**
     * Kích thước hiện tại của cache
     */
    public int size() {
        return cacheMap.size();
    }

    /**
     * Lớp nội bộ đại diện cho một mục cache
     */
    private static class CacheEntry<V> {
        final V value;
        final long expiryTime;

        CacheEntry(V value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }
}
