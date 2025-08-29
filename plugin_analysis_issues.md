# Phân tích và giải pháp khắc phục các vấn đề tiềm ẩn trong ExtraStorage

## 1. Vấn đề với hệ thống nhặt vật phẩm vào kho (Pickup System)

### Phát hiện vấn đề:
1. **Không nhất quán giữa các listener**:
   - `PickupListener` và `PlayerDropListener` xử lý logic tương tự nhưng có phần khác nhau
   - `EntityDeathListener` sử dụng cách tiếp cận khác

2. **Xung đột metadata**:
   - Vật phẩm được đánh dấu `processed_by_storage` có thể gây xung đột giữa các plugin
   - Metadata không bền vững khi server restart

3. **Vấn đề hiệu suất**:
   - Mỗi lần nhặt đều gọi `getFilteredItems()` tạo bản sao dữ liệu lớn
   - Truy vấn quá nhiều vào Storage khi nhặt item (đặc biệt khi farm tự động)

### Giải pháp:
1. **Thống nhất cách xử lý nhặt vật phẩm**:
   ```java
   // Trong ItemFilterService
   public static boolean shouldPickupToStorage(Player player, ItemStack item, Storage storage) {
       // Cache kết quả để giảm lượng truy vấn tới storage
       UUID playerId = player.getUniqueId();
       String itemKey = ItemUtil.toMaterialKey(item);

       // Nếu global filter tắt, luôn cho phép nhặt vào kho
       if (!ExtraStorage.isFilterEnabled()) {
           return true;
       }

       // Tối ưu: Trước hết kiểm tra xem người chơi có bất kỳ bộ lọc nào không
       if (!hasPlayerFilter(playerId)) {
           return false;
       }

       // Đảm bảo item có thể được lưu vào kho
       if (!storage.canStore(item)) {
           return false;
       }

       // Sử dụng cache để kiểm tra nhanh
       return isItemAllowed(playerId, itemKey);
   }
   ```

2. **Sử dụng PersistentDataContainer thay cho metadata**:
   ```java
   // Đánh dấu item đã được xử lý
   ItemMeta meta = item.getItemMeta();
   if (meta != null) {
       meta.getPersistentDataContainer().set(
           new NamespacedKey(plugin, "processed_by_storage"),
           PersistentDataType.INTEGER, 1
       );
       item.setItemMeta(meta);
   }
   ```

## 2. Vấn đề với tính nhất quán dữ liệu khi server bị crash

### Phát hiện vấn đề:
1. **Quá trình lưu dữ liệu không đồng bộ**:
   - Nhiều người chơi cùng thoát game gây quá tải cho hệ thống lưu trữ

2. **Dữ liệu có thể bị mất khi server crash**:
   - Vấn đề với việc lưu dữ liệu khi server shutdown đột ngột

3. **Phục hồi dữ liệu không đầy đủ**:
   - Cơ chế backup chỉ sao lưu database, không bao gồm trạng thái hiện tại

### Giải pháp:
1. **Thêm cơ chế journaling**:
   ```java
   // Ghi transaction log trước khi thực hiện thay đổi dữ liệu
   public boolean addTransactionLog(UUID playerId, String itemKey, long amount, TransactionType type) {
       // Ghi thông tin giao dịch vào file .journal
       try (FileWriter writer = new FileWriter(journalFile, true)) {
           String transaction = System.currentTimeMillis() + "," + 
               playerId + "," + itemKey + "," + amount + "," + type + "\n";
           writer.write(transaction);
           return true;
       } catch (Exception e) {
           plugin.getLogger().warning("Failed to write transaction log: " + e.getMessage());
           return false;
       }
   }
   
   // Khi khôi phục, đọc các transaction từ journal và áp dụng lại
   public void recoverFromJournal() {
       // Đọc và áp dụng lại các giao dịch chưa được lưu
   }
   ```

2. **Cải thiện quá trình lưu dữ liệu**:
   - Sử dụng SaveStrategy để quyết định khi nào lưu dữ liệu
   - Phân bổ tác vụ lưu trữ để tránh tắc nghẽn

## 3. Vấn đề với bộ nhớ cache và memory leak

### Phát hiện vấn đề:
1. **Cache không được dọn dẹp đúng cách**:
   - `playerFilterCache` và `commonItemTypeCache` tăng không kiểm soát
   - `lastSaveTime` giữ tham chiếu đến UUID không còn sử dụng

2. **Memory leak do giữ tham chiếu cũ**:
   - User objects được giữ trong cache kể cả khi người chơi offline

### Giải pháp:
1. **Sử dụng ExpiringCache cho tất cả cache**:
   ```java
   // Trong ItemFilterService
   private static final ExpiringCache<UUID, Set<String>> playerFilterCache = 
       new ExpiringCache<>(30000); // 30 giây
   
   // Trong NotificationManager
   private final ExpiringCache<UUID, NotificationSettings> playerSettings =
       new ExpiringCache<>(TimeUnit.MINUTES.toMillis(10)); // 10 phút
   ```

2. **Dọn dẹp tham chiếu không sử dụng**:
   ```java
   // Trong onDisable
   public void onDisable() {
       // Đảm bảo lưu tất cả dữ liệu
       if (userManager != null) {
           userManager.forceSaveAll();
       }
       
       // Xóa tất cả cache và tham chiếu
       userManager.clearAllReferences();
       ItemFilterService.clearAllCache();
       
       // Các bước khác...
   }
   ```

## 4. Vấn đề với hiệu suất CPU khi xử lý storage

### Phát hiện vấn đề:
1. **Tính toán lặp đi lặp lại**:
   - `getFilteredItems()` tạo map mới mỗi khi được gọi
   - `getUsedSpace()` tính toán nhiều lần không cần thiết

2. **Xử lý đồng bộ cho các hoạt động nặng**:
   - Add/remove items trên main thread

### Giải pháp:
1. **Cache kết quả tính toán phức tạp**:
   ```java
   // Trong StubStorage
   private Set<String> cachedFilteredItems = null;
   private long lastFilterCacheTime = 0;
   
   @Override
   public Map<String, Item> getFilteredItems() {
       long currentTime = System.currentTimeMillis();
       
       // Sử dụng cache nếu còn hạn
       if (cachedFilteredItems != null && currentTime - lastFilterCacheTime < FILTER_CACHE_DURATION) {
           return cachedFilteredItems.stream()
               .collect(Collectors.toMap(key -> key, key -> new StubItem(this, key)));
       }
       
       // Tính toán và cache kết quả
       cachedFilteredItems = user.entry.getValue().items.entrySet()
           .stream()
           .filter(entry -> entry.getValue().filtered)
           .map(Map.Entry::getKey)
           .collect(Collectors.toSet());
       
       lastFilterCacheTime = currentTime;
       
       return cachedFilteredItems.stream()
           .collect(Collectors.toMap(key -> key, key -> new StubItem(this, key)));
   }
   ```

2. **Sử dụng AsyncScheduler cho các hoạt động nặng**:
   ```java
   // Trong StubStorage.add()
   @Override
   public void add(Object key, long quantity) {
       // Xử lý đồng bộ ngắn gọn
       String validKey = ItemUtil.toMaterialKey(key);
       
       // Cập nhật cache nhanh để UI hiển thị kịp thời
       updateLocalCache(validKey, quantity);
       
       // Cập nhật database bất đồng bộ
       AsyncScheduler.get(instance).run(() -> {
           user.entry.setValue(u -> u.withItemModifiedIfFound(validKey, i -> {
               if (i == null) {
                   return ItemImpl.EMPTY.withQuantity(quantity).withFiltered(true);
               }
               return i.withQuantity(i.quantity + quantity);
           }));
       });
   }
   ```

## 5. Đảm bảo nhặt item vào kho khi có filter

### Phát hiện vấn đề:
1. **Không nhất quán giữa các loại key**:
   - Vấn đề với `normalizeKey()` gây ra việc nhận dạng sai vật phẩm 
   - Item key khác nhau giữa các phiên bản Minecraft

2. **Vấn đề với chuỗi lọc**:
   - Người chơi cần thêm vật phẩm vào filter trước, nhưng có thể bị hủy do server restart

### Giải pháp:
1. **Sử dụng Material Registry để đồng bộ key**:
   ```java
   // Trong ItemUtil
   public static String toMaterialKey(Object item) {
       // Các xử lý hiện tại
       
       // Thêm kiểm tra với Material Registry
       try {
           Material material = Material.valueOf(itemType);
           // Đảm bảo sử dụng tên nhất quán từ Material Registry
           return material.name();
       } catch (Exception e) {
           // Không phải Material tiêu chuẩn, sử dụng xử lý hiện tại
       }
       
       // Các xử lý còn lại
   }
   ```

2. **Thêm whitelist mặc định từ config**:
   ```java
   // Trong UserManager.onCreate
   public void onCreate(DataEntry<UUID, UserImpl> entry) {
       // Tạo bộ lọc mặc định từ file cấu hình
       Map<String, ItemImpl> map = instance.getSetting().getDefaultFilters().stream()
           .map(ItemUtil::normalizeMaterialKey)
           .filter(Objects::nonNull)
           .filter(key -> !key.trim().isEmpty())
           .distinct()
           .collect(Collectors.toMap(
                   key -> key,
                   key -> ItemImpl.EMPTY.withFiltered(true).withQuantity(0)));
       
       // Thêm vào dữ liệu người dùng mới
       entry.setValue(user -> user
               .withItems(map)
               .withSpace(instance.getSetting().getMaxSpace()),
               false);
   }
   ```
