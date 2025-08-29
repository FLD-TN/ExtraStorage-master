## Tối ưu hệ thống lọc vật phẩm

### Vấn đề
- Hệ thống lọc vật phẩm hiện tại tạo nhiều HashSet tạm thời gây tốn CPU
- Không có tối ưu cho các loại vật phẩm phổ biến (như đá, đất, gỗ)
- Cache không được dọn dẹp khi người chơi thoát game
- Có thể kiểm tra filter không cần thiết cho người chơi không có bộ lọc nào

### Giải pháp
1. Thêm cache hai lớp:
   - Cache cho người chơi cụ thể (`playerFilterCache`)
   - Cache toàn cục cho các loại vật phẩm phổ biến (`commonItemTypeCache`)
   - Cache trạng thái filter của người chơi (`hasFilterCache`)

2. Thêm phương thức `hasPlayerFilter` để nhanh chóng xác định người chơi có bộ lọc nào không

3. Thực hiện dọn dẹp cache định kỳ trong hệ thống giám sát

4. Xóa cache khi người chơi thoát game để giảm sử dụng bộ nhớ

### Lợi ích
1. **Giảm CPU Usage**: Giảm đáng kể số lượng phép tính và chuyển đổi hashset
2. **Giảm Memory Usage**: Cache được dọn dẹp định kỳ và khi người chơi thoát game
3. **Tối ưu cho vật phẩm phổ biến**: Cache toàn cục giúp xử lý nhanh hơn các vật phẩm phổ biến
4. **Kiểm tra nhanh**: Phương thức hasPlayerFilter giúp bỏ qua kiểm tra filter cho người chơi không có bộ lọc

### Cách hoạt động
1. Khi player nhặt vật phẩm:
   - Trước tiên kiểm tra nhanh xem người chơi có bất kỳ bộ lọc nào không
   - Nếu không có bộ lọc, bỏ qua kiểm tra phức tạp hơn
   - Nếu có bộ lọc, kiểm tra trong cache toàn cục cho vật phẩm phổ biến
   - Nếu không tìm thấy trong cache toàn cục, kiểm tra cache người dùng
   - Nếu cache quá hạn, cập nhật từ storage

2. Dọn dẹp cache:
   - Xóa cache của người chơi cụ thể khi họ thoát game
   - Làm sạch các cache quá hạn định kỳ trong hệ thống giám sát
   - Xóa tất cả cache khi plugin tắt

### Cách kiểm tra
1. Theo dõi thông số "Filter Cache Size" trong log giám sát
2. Số lượng cache nên giảm sau khi người chơi thoát game
3. CPU và memory usage nên giảm sau khi tối ưu
