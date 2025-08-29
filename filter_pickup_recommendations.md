# Khuyến nghị tối ưu hóa và sửa lỗi cho Plugin ExtraStorage

## I. Vấn đề về hệ thống bộ lọc và nhặt vật phẩm

### 1. Hệ thống bộ lọc item (Filter System)
- **Vấn đề**: Không nhất quán trong việc kiểm tra item có trong bộ lọc hay không, có thể dẫn đến việc người chơi không nhặt được item sau khi server restart
- **Giải pháp**:
  - Cải thiện hàm `toMaterialKey()` để đảm bảo nhận dạng nhất quán các loại vật phẩm
  - Sử dụng Material Registry để đảm bảo tên vật phẩm nhất quán giữa các phiên bản Minecraft
  - Lưu các bộ lọc người chơi vào một file riêng để phục hồi nếu cần
  - Lưu giữ một cách cẩn thận và nhất quán định dạng key của vật phẩm

### 2. Hệ thống nhặt vật phẩm (Pickup System)
- **Vấn đề**: Có thể xảy ra trường hợp vật phẩm không được nhặt vào kho ngay cả khi được lọc
- **Giải pháp**:
  - Thêm log chi tiết để theo dõi quá trình kiểm tra bộ lọc
  - Kiểm tra trạng thái `shouldPickupToStorage` bằng cách in log trước khi return false
  - Thêm option debug cho người chơi để xem item nào được lọc và không được lọc
  - Kiểm tra và xóa các điều kiện ngăn chặn không cần thiết trong PickupListener

### 3. Cải thiện cách xử lý metadata
- **Vấn đề**: Sử dụng metadata có thể gây xung đột giữa các plugin và không bền vững
- **Giải pháp**:
  - Sử dụng PersistentDataContainer thay cho metadata
  - Thêm tiền tố cho tất cả các key để tránh xung đột
  - Kiểm tra cẩn thận khi server restart để đảm bảo không xảy ra lỗi

## II. Vấn đề về hiệu suất và bộ nhớ

### 1. Rò rỉ bộ nhớ (Memory Leaks)
- **Vấn đề**: Cache không được dọn dẹp đúng cách, giữ tham chiếu đến người chơi offline
- **Giải pháp**:
  - Sử dụng ExpiringCache cho tất cả các cache
  - Chủ động dọn dẹp cache khi người chơi thoát game
  - Giám sát việc sử dụng bộ nhớ và tự động làm sạch khi cần

### 2. Sử dụng CPU cao
- **Vấn đề**: Tính toán lặp đi lặp lại và xử lý đồng bộ cho các hoạt động nặng
- **Giải pháp**:
  - Cache kết quả của các phép tính tốn kém (getFilteredItems, getUsedSpace)
  - Sử dụng AsyncScheduler cho các hoạt động nặng không cần đồng bộ
  - Tối ưu các vòng lặp và tránh tạo nhiều đối tượng tạm thời không cần thiết

### 3. Tối ưu hóa truy vấn cơ sở dữ liệu
- **Vấn đề**: Quá nhiều truy vấn đến cơ sở dữ liệu khi nhặt vật phẩm
- **Giải pháp**:
  - Gộp nhiều thay đổi nhỏ trước khi lưu vào database
  - Sử dụng batch processing khi cần cập nhật nhiều người chơi
  - Sử dụng lớp đệm giữa ứng dụng và cơ sở dữ liệu

## III. Vấn đề về tính toàn vẹn dữ liệu

### 1. Dữ liệu có thể bị mất khi server crash
- **Vấn đề**: Quá trình lưu dữ liệu không đồng bộ, dữ liệu có thể bị mất khi server crash
- **Giải pháp**:
  - Thêm cơ chế journaling để ghi lại các thay đổi trước khi áp dụng
  - Phát hiện server crash và khôi phục dữ liệu từ journal
  - Sao lưu định kỳ và trước khi cập nhật lớn

### 2. Khôi phục dữ liệu từ backup
- **Vấn đề**: Cơ chế backup chỉ sao lưu database, không bao gồm trạng thái hiện tại
- **Giải pháp**:
  - Sao lưu trạng thái đầy đủ của plugin, bao gồm cả cache
  - Thêm tùy chọn để admin có thể khôi phục dữ liệu cho từng người chơi
  - Lưu trữ metadata đầy đủ trong backup để đảm bảo khôi phục chính xác

## IV. Khuyến nghị cụ thể để đảm bảo người chơi luôn nhặt được vật phẩm vào kho

1. **Kiểm tra xem người chơi có thể nhặt item sau khi server restart**
   - Thêm log chi tiết khi khởi động lại về số lượng bộ lọc đã tải
   - Xác nhận danh sách bộ lọc được tải đúng cho mỗi người chơi

2. **Gỡ bỏ các điều kiện không cần thiết trong quá trình nhặt**
   - Đơn giản hóa logic kiểm tra trong PickupListener
   - Ưu tiên logic filter hơn các kiểm tra khác

3. **Thêm cơ chế tự động phục hồi bộ lọc**
   - Lưu danh sách vật phẩm đã lọc vào file riêng
   - Tự động khôi phục nếu phát hiện không nhất quán

4. **Cải thiện thông báo và phản hồi**
   - Thông báo rõ ràng cho người chơi khi vật phẩm vào kho
   - Thêm tùy chọn để hiển thị trạng thái bộ lọc

5. **Thêm chức năng phát hiện và tự động sửa lỗi**
   - Kiểm tra tính toàn vẹn dữ liệu khi người chơi đăng nhập
   - Tự động sửa các bộ lọc không nhất quán
