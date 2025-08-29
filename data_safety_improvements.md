# Các cải tiến để đảm bảo an toàn dữ liệu và khắc phục sự cố mất dữ liệu khi server restart/crash

## 1. Thêm StorageBackupManager
- **Tự động sao lưu** dữ liệu mỗi 30 phút
- **Sao lưu khi tắt plugin** để đảm bảo dữ liệu được an toàn
- **Cơ chế phát hiện crash** với file marker để khôi phục dữ liệu khi server bị crash
- **Giới hạn số lượng backup** để tránh tràn ổ đĩa

## 2. Cải tiến quá trình lưu dữ liệu
- **Giảm chu kỳ auto-save** từ 1 phút xuống 30 giây
- **Thêm forceSaveAll** để đảm bảo toàn bộ dữ liệu được lưu trước khi tắt plugin
- **Cải thiện unload** để đảm bảo tất cả dữ liệu được lưu khi người chơi thoát game
- **Log chi tiết quá trình lưu** để dễ dàng phát hiện sự cố

## 3. Nâng cao cơ chế khóa (lock) cho giao dịch
- **Phát hiện deadlock** bằng cách theo dõi thời gian khóa
- **Tự động giải phóng khóa treo** sau một khoảng thời gian
- **Kiểm soát số lượng giao dịch đồng thời** để giảm tải CPU

## 4. Quản lý cache thông minh
- **Xóa cache kịp thời** khi người chơi thoát game
- **Dọn dẹp cache định kỳ** để tránh rò rỉ bộ nhớ
- **Ghi log số lượng cache** để theo dõi hiệu suất

## Quy trình làm việc khi server khởi động
1. Plugin kiểm tra marker crash để phát hiện các sự cố trước đó
2. Kiểm tra và khôi phục dữ liệu từ backup nếu cần
3. Tải dữ liệu người chơi từ cơ sở dữ liệu
4. Kích hoạt hệ thống sao lưu tự động

## Quy trình làm việc khi server tắt
1. Lưu tất cả dữ liệu người chơi (forceSaveAll)
2. Tạo bản sao lưu trước khi tắt
3. Dọn dẹp các cache và tài nguyên

## Quy trình khi người chơi thoát game
1. Lưu dữ liệu người chơi ngay lập tức
2. Xóa tất cả cache liên quan
3. Ghi log quá trình

## Cách kiểm tra
1. Theo dõi logs để đảm bảo quy trình lưu dữ liệu hoạt động đúng
2. Kiểm tra thư mục backup để đảm bảo sao lưu được tạo đều đặn
3. Thử nghiệm restart server và kiểm tra dữ liệu được khôi phục đúng
4. Giám sát hiệu suất plugin trong quá trình sử dụng
