package me.hsgamer.extrastorage.data.user;

import io.github.projectunified.minelib.scheduler.async.AsyncScheduler;
import io.github.projectunified.minelib.scheduler.common.task.Task;
import io.github.projectunified.minelib.scheduler.global.GlobalScheduler;
import me.hsgamer.extrastorage.ExtraStorage;
import me.hsgamer.extrastorage.api.events.StorageLoadEvent;
import me.hsgamer.extrastorage.api.user.User;
import me.hsgamer.extrastorage.data.stub.StubUser;
import me.hsgamer.extrastorage.fetcher.TextureFetcher;
import me.hsgamer.extrastorage.util.ItemUtil;
import me.hsgamer.hscore.database.client.sql.java.JavaSqlClient;
import me.hsgamer.topper.data.core.DataEntry;
import me.hsgamer.topper.data.simple.SimpleDataHolder;
import me.hsgamer.topper.storage.core.DataStorage;
import me.hsgamer.topper.storage.sql.converter.UUIDSqlValueConverter;
import me.hsgamer.topper.storage.sql.core.SqlDataStorageSupplier;
import me.hsgamer.topper.storage.sql.mysql.MySqlDataStorageSupplier;
import me.hsgamer.topper.storage.sql.sqlite.SqliteDataStorageSupplier;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;

public final class UserManager extends SimpleDataHolder<UUID, UserImpl> {
    private final Map<UUID, StubUser> userCache = new ConcurrentHashMap<>();
    private final ExtraStorage instance;
    private final DataStorage<UUID, UserImpl> storage;
    private final AtomicBoolean loaded = new AtomicBoolean(false);
    private final AtomicReference<ConcurrentHashMap<UUID, UserImpl>> saveMapRef = new AtomicReference<>(
            new ConcurrentHashMap<>());
    private final Map<UUID, Long> lastSaveTime = new ConcurrentHashMap<>();
    private final Task autoSaveTask;

    private static final long SAVE_DEBOUNCE_MS = 5000; // 5 giây
    private static final long AUTO_SAVE_INTERVAL = 20L * 30L; // 30 giây (giảm từ 1 phút)

    public UserManager(ExtraStorage instance) {
        this.instance = instance;
        boolean isMySql = instance.getSetting().getDBType().equalsIgnoreCase("mysql");
        SqlDataStorageSupplier.Options options = SqlDataStorageSupplier.options()
                .setIncrementalKey("id")
                .setClientFunction(JavaSqlClient::new);
        SqlDataStorageSupplier supplier = isMySql
                ? new MySqlDataStorageSupplier(instance.getSetting().getSqlDatabaseSetting(), options)
                : new SqliteDataStorageSupplier(instance.getDataFolder(), instance.getSetting().getSqlDatabaseSetting(),
                        options);
        this.storage = supplier.getStorage(
                instance.getSetting().getDBTable(),
                new UUIDSqlValueConverter("uuid"),
                UserImpl.getConverter(isMySql));

        AsyncScheduler.get(instance).run(() -> {
            final StorageLoadEvent event = new StorageLoadEvent();
            try {
                this.storage.onRegister();
                this.storage.load().forEach((uuid, user) -> getOrCreateEntry(uuid).setValue(user, false));
                event.setLoaded(true);
                instance.getLogger().info("Đã tải dữ liệu người chơi thành công");
            } catch (Exception e) {
                instance.getLogger().log(Level.SEVERE, "Error while loading user", e);
                event.setLoaded(false);
                instance.getLogger().warning("Lỗi khi tải dữ liệu người chơi. Plugin có thể không hoạt động đúng!");
            } finally {
                loaded.set(true);
                GlobalScheduler.get(instance).run(() -> Bukkit.getServer().getPluginManager().callEvent(event));
            }
        });

        // Auto save task - giảm thời gian xuống 30 giây để đảm bảo dữ liệu được lưu
        // thường xuyên hơn
        this.autoSaveTask = AsyncScheduler.get(instance).runTimer(
                () -> {
                    Map<UUID, UserImpl> map = saveMapRef.get();
                    if (!map.isEmpty()) {
                        try {
                            instance.getLogger().fine("Đang lưu dữ liệu cho " + map.size() + " người chơi...");
                            save();
                            instance.getLogger().fine("Đã lưu dữ liệu thành công");
                        } catch (Exception e) {
                            instance.getLogger().log(Level.WARNING, "Lỗi khi tự động lưu dữ liệu", e);
                        }
                    }
                },
                AUTO_SAVE_INTERVAL,
                AUTO_SAVE_INTERVAL);
    }

    public void save() {
        Map<UUID, UserImpl> map = saveMapRef.get();
        if (map.isEmpty())
            return;
        saveMapRef.set(new ConcurrentHashMap<>());

        Optional<DataStorage.Modifier<UUID, UserImpl>> optionalModifier = storage.modify();
        if (!optionalModifier.isPresent()) { // SỬA LỖI isEmpty()
            instance.getLogger().log(Level.WARNING, "Failed to get modifier for user storage");
            return;
        }
        DataStorage.Modifier<UUID, UserImpl> modifier = optionalModifier.get();

        try {
            modifier.save(map);
            modifier.commit();
        } catch (Exception e) {
            instance.getLogger().log(Level.SEVERE, "Error while saving user data", e);
            modifier.rollback();
        }
    }

    public void save(UUID uuid) {
        Map<UUID, UserImpl> saveMap = saveMapRef.get();
        UserImpl toSave = saveMap.get(uuid);
        if (toSave == null)
            return;
        saveMap.remove(uuid);

        Optional<DataStorage.Modifier<UUID, UserImpl>> optionalModifier = storage.modify();
        if (!optionalModifier.isPresent()) { // SỬA LỖI isEmpty()
            ExtraStorage.getInstance().getLogger().log(Level.WARNING, "Failed to get modifier for user storage");
            return;
        }
        DataStorage.Modifier<UUID, UserImpl> modifier = optionalModifier.get();
        try {
            modifier.save(Collections.singletonMap(uuid, toSave));
            modifier.commit();
        } catch (Exception e) {
            ExtraStorage.getInstance().getLogger().log(Level.SEVERE, "Error while saving user data for " + uuid, e);
            modifier.rollback();
        }
    }

    public void saveDebounced(UUID uuid) {
        long currentTime = System.currentTimeMillis();
        Long lastSave = lastSaveTime.get(uuid);

        if (lastSave == null || currentTime - lastSave > SAVE_DEBOUNCE_MS) {
            save(uuid);
            lastSaveTime.put(uuid, currentTime);
        }
    }

    // THÊM METHOD UNLOAD
    public void unload(UUID uuid) {
        // Đảm bảo lưu dữ liệu trước khi xóa khỏi cache
        try {
            UserImpl userData = null;
            Map<UUID, UserImpl> saveMap = saveMapRef.get();
            if (saveMap.containsKey(uuid)) {
                userData = saveMap.get(uuid);
            } else {
                DataEntry<UUID, UserImpl> entry = getEntryMap().get(uuid);
                if (entry != null) {
                    userData = entry.getValue();
                }
            }

            // Lưu dữ liệu
            if (userData != null) {
                save(uuid);
                instance.getLogger().info("Đã lưu dữ liệu của người chơi " + uuid + " trước khi unload");
            }
        } catch (Exception e) {
            instance.getLogger().log(Level.WARNING, "Lỗi khi lưu dữ liệu cho " + uuid, e);
        }

        // Remove from cache
        userCache.remove(uuid);

        // Remove from entry map if needed
        getEntryMap().remove(uuid);

        // Remove từ cả saveMap để đảm bảo không bị mất dữ liệu
        saveMapRef.get().remove(uuid);

        // Remove khỏi lastSaveTime
        lastSaveTime.remove(uuid);

        instance.getLogger().info("Unloaded user data for UUID: " + uuid);
    }

    public void cleanupCache() {
        // Cleanup user cache - chỉ giữ người chơi online
        Iterator<UUID> iterator = userCache.keySet().iterator();
        while (iterator.hasNext()) {
            UUID uuid = iterator.next();
            if (Bukkit.getPlayer(uuid) == null) {
                iterator.remove();
            }
        }

        // Cleanup save debounce cache
        long currentTime = System.currentTimeMillis();
        lastSaveTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > TimeUnit.MINUTES.toMillis(30));
    }

    public int getCacheSize() {
        return userCache.size();
    }

    public boolean isLoaded() {
        return loaded.get();
    }

    @Override
    public @NotNull UserImpl getDefaultValue() {
        return UserImpl.EMPTY;
    }

    @Override
    public void onCreate(DataEntry<UUID, UserImpl> entry) {
        Map<String, ItemImpl> map = instance.getSetting().getWhitelist().stream()
                .map(ItemUtil::normalizeMaterialKey)
                .filter(Objects::nonNull)
                .filter(key -> !key.trim().isEmpty())
                .distinct()
                .collect(Collectors.toMap(
                        key -> key,
                        key -> ItemImpl.EMPTY.withFiltered(true)));
        // SỬA LỖI NaN%
        long defaultSpace = instance.getSetting().getMaxSpace();

        // THÊM: Khởi tạo pending partner requests rỗng
        entry.setValue(user -> user
                .withItems(map)
                .withSpace(defaultSpace)
                .withPendingPartnerRequests(Collections.emptyMap()),
                false);
    }

    @Override
    public void onUpdate(DataEntry<UUID, UserImpl> entry, UserImpl oldValue, UserImpl newValue) {
        saveMapRef.get().put(entry.getKey(), newValue);
    }

    public void load(UUID uuid) {
        DataEntry<UUID, UserImpl> entry = getOrCreateEntry(uuid);
        if (entry.getValue().texture.isEmpty()) {
            AsyncScheduler.get(instance).run(() -> {
                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.getKey());
                String name = player.getName();
                if (name == null || name.isEmpty())
                    return;
                String textureUrl = TextureFetcher.getTextureUrl(name);
                if (textureUrl == null || textureUrl.isEmpty())
                    return;
                byte[] texture = ("{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}").getBytes();
                String textureString = new String(Base64.getEncoder().encode(texture));
                entry.setValue(user -> user.withTexture(textureString));
            });
        }
    }

    public Collection<User> getUsers() {
        return getEntryMap().values().stream().map(StubUser::new).collect(Collectors.toSet());
    }

    public User getUser(UUID uuid) {
        return userCache.computeIfAbsent(uuid, k -> new StubUser(getOrCreateEntry(k)));
    }

    public User getUser(OfflinePlayer player) {
        if (player == null) {
            throw new IllegalArgumentException("Player cannot be null");
        }
        return getUser(player.getUniqueId());
    }

    public void cleanup() {
        // Save all cached data
        Map<UUID, UserImpl> saveMap = saveMapRef.get();
        if (!saveMap.isEmpty()) {
            try {
                instance.getLogger().info("Đang lưu dữ liệu cho " + saveMap.size() + " người chơi...");

                // Save all entries using add/update
                for (Map.Entry<UUID, UserImpl> entry : saveMap.entrySet()) {
                    try {
                        DataEntry<UUID, UserImpl> dataEntry = getOrCreateEntry(entry.getKey());
                        dataEntry.setValue(entry.getValue());
                    } catch (Exception e) {
                        instance.getLogger().log(Level.WARNING, "Lỗi khi lưu dữ liệu cho người chơi " + entry.getKey(),
                                e);
                    }
                }
                saveMap.clear();

                instance.getLogger().info("Đã lưu tất cả dữ liệu người chơi thành công");
            } catch (Exception e) {
                instance.getLogger().log(Level.SEVERE, "Lỗi nghiêm trọng khi lưu dữ liệu người chơi", e);
            }
        }

        // Clear user cache to free memory
        userCache.clear();
        lastSaveTime.clear();
    }

    /**
     * Lưu toàn bộ dữ liệu đồng bộ - sử dụng khi server sắp tắt
     * để đảm bảo tất cả dữ liệu được lưu
     */
    public void forceSaveAll() {
        try {
            instance.getLogger().info("Đang lưu toàn bộ dữ liệu người chơi...");

            // Lưu tất cả dữ liệu trong saveMapRef
            save();

            // Đảm bảo tất cả người chơi online được lưu
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (getEntryMap().containsKey(uuid)) {
                    save(uuid);
                }
            }

            instance.getLogger().info("Đã lưu toàn bộ dữ liệu người chơi thành công");
        } catch (Exception e) {
            instance.getLogger().log(Level.SEVERE, "Lỗi nghiêm trọng khi lưu dữ liệu người chơi", e);
        }
    }

    public void stop() {
        autoSaveTask.cancel();
        forceSaveAll(); // Lưu tất cả dữ liệu trước khi dừng
        cleanup(); // Dọn dẹp các cache
    }

}