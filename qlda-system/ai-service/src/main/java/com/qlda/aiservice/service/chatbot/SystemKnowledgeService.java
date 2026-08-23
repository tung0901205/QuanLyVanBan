package com.qlda.aiservice.service.chatbot;

import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class SystemKnowledgeService {

    private record KnowledgeItem(List<String> keywords, String answer) {}

    private static final List<KnowledgeItem> ITEMS = List.of(
        new KnowledgeItem(List.of("tong quan", "dashboard tong quan", "trang tong quan"), """
            Mục "Tổng quan" là trang tóm tắt nhanh tình trạng làm việc của tài khoản đang đăng nhập. Tùy vai trò và quyền, trang có thể hiển thị các chỉ số, công việc/văn bản cần xử lý và lối tắt đến các chức năng nghiệp vụ.
            """),
        new KnowledgeItem(List.of("tai len van ban", "tai van ban len", "upload van ban", "nop van ban", "them van ban"), """
            Bước 1: Vào menu "Tải lên" ở thanh bên trái.
            Bước 2: Nhập Trích yếu, Số ký hiệu, Loại văn bản, Ngày văn bản và các thông tin bắt buộc.
            Bước 3: Chọn Đơn vị ban hành và Đơn vị chủ trì; hệ thống sẽ lọc Trưởng đơn vị nhận xử lý theo đúng đơn vị đã chọn.
            Bước 4: Chọn tệp đính kèm từ máy tính nếu có.
            Bước 5: Chọn "Lưu nháp" nếu chưa muốn gửi, hoặc "Gửi vào luồng" để chuyển văn bản cho Trưởng đơn vị xử lý.
            """),
        new KnowledgeItem(List.of("uy quyen", "uy quyen xu ly", "tao uy quyen"), """
            Bước 1: Đăng nhập bằng tài khoản Lãnh đạo/Trưởng đơn vị và mở menu "Ủy quyền xử lý".
            Bước 2: Nhấn "+ Tạo ủy quyền".
            Bước 3: Chọn người được ủy quyền, thời gian bắt đầu, thời gian kết thúc và phạm vi ủy quyền.
            Bước 4: Lưu ủy quyền. Trong thời gian còn hiệu lực, người được ủy quyền sẽ thấy chức năng nghiệp vụ tương ứng, ví dụ Phê duyệt nếu phạm vi cho phép.
            Bước 5: Khi không còn nhu cầu, Lãnh đạo có thể hủy ủy quyền tại chính màn hình này.
            """),
        new KnowledgeItem(List.of("van ban noi bo", "noi bo la gi"), """
            "Văn bản nội bộ" là nhóm văn bản được tạo và lưu hành trong phạm vi cơ quan/đơn vị, phục vụ trao đổi và điều hành nội bộ. Mục này dùng để xem, tạo và quản lý các văn bản không phải văn bản đến từ bên ngoài hoặc văn bản phát hành ra ngoài.
            """),
        new KnowledgeItem(List.of("van ban den", "muc van ban den"), """
            "Văn bản đến" dùng để quản lý các văn bản cơ quan tiếp nhận. Người dùng có thể xem danh sách, mở chi tiết, theo dõi trạng thái, tệp đính kèm và luồng xử lý; người có quyền phù hợp có thể luân chuyển hoặc xử lý văn bản.
            """),
        new KnowledgeItem(List.of("van ban di", "muc van ban di"), """
            "Văn bản đi" dùng để quản lý văn bản do cơ quan soạn thảo và phát hành. Văn bản có thể được tạo trực tiếp hoặc tạo từ Template, sau đó trình duyệt, ký duyệt và ban hành theo quy trình.
            """),
        new KnowledgeItem(List.of("ho so cong viec", "gan van ban vao ho so", "gan vb"), """
            Bước 1: Mở menu "Hồ sơ công việc".
            Bước 2: Tạo hồ sơ mới hoặc chọn một hồ sơ đang có.
            Bước 3: Chọn "Gắn VB" và chọn văn bản theo mã/trích yếu.
            Bước 4: Sau khi gắn, văn bản xuất hiện trong hồ sơ để người dùng mở lại và theo dõi cùng nhóm công việc liên quan.
            """),
        new KnowledgeItem(List.of("template", "mau van ban", "tao tu mau"), """
            Bước 1: Mở menu "Template văn bản".
            Bước 2: Chọn mẫu phù hợp và nhấn "Tạo từ mẫu".
            Bước 3: Điền các trường của mẫu như số ký hiệu, trích yếu, ngày văn bản, người ký và nội dung chuyên biệt của từng mẫu.
            Bước 4: Nhấn tạo văn bản. Hệ thống sinh file Word hoàn chỉnh và tạo một Văn bản đi.
            Bước 5: Mở Văn bản đi vừa tạo để tải file Word, kiểm tra nội dung và tiếp tục trình duyệt/ban hành theo quy trình.
            """),
        new KnowledgeItem(List.of("phe duyet", "duyet van ban", "tu choi van ban"), """
            Bước 1: Mở menu "Phê duyệt" hoặc mở thông báo yêu cầu phê duyệt.
            Bước 2: Chọn văn bản cần xử lý và đọc chi tiết, tệp đính kèm, lịch sử xử lý.
            Bước 3: Chọn "Phê duyệt" nếu đồng ý hoặc "Từ chối" nếu cần trả lại.
            Bước 4: Nhập ý kiến xử lý khi cần; lý do từ chối nên được ghi rõ.
            Bước 5: Xác nhận để hệ thống cập nhật trạng thái và thông báo cho bên liên quan.
            """),
        new KnowledgeItem(List.of("quy trinh xu ly", "luong xu ly", "workflow"), """
            "Quy trình xử lý" mô tả các bước mà một văn bản phải đi qua, ví dụ: Chuyên viên tạo/tiếp nhận → Trưởng đơn vị xem xét/phê duyệt → ký duyệt → ban hành. Mỗi bước có người/nhóm quyền phụ trách và có thể gắn thời hạn SLA để theo dõi tiến độ.
            """),
        new KnowledgeItem(List.of("bao cao thong ke", "thong ke", "dashboard", "bao cao"), """
            Mục "Báo cáo & Thống kê" dành cho người có quyền xem báo cáo. Màn hình tổng hợp các số liệu như tổng văn bản, văn bản đang xử lý, hoàn thành, quá hạn và biểu đồ theo thời gian/trạng thái. Người dùng có thể dùng bộ lọc để xem số liệu theo phạm vi cần theo dõi.
            """),
        new KnowledgeItem(List.of("thong bao", "muc thong bao"), """
            Mục "Thông báo" hiển thị các sự kiện liên quan đến người dùng như văn bản mới được chuyển đến, yêu cầu phê duyệt, kết quả xử lý và cảnh báo. Người dùng có thể mở thông báo để xem nội dung liên quan, đánh dấu đã đọc hoặc xóa thông báo.
            """),
        new KnowledgeItem(List.of("tim kiem", "tra cuu", "tim van ban"), """
            Bước 1: Mở menu "Tìm kiếm".
            Bước 2: Nhập mã văn bản, số ký hiệu, trích yếu hoặc từ khóa liên quan.
            Bước 3: Chọn bộ lọc nếu cần như loại văn bản, trạng thái hoặc thời gian.
            Bước 4: Thực hiện tìm kiếm và mở văn bản trong danh sách kết quả để xem chi tiết.
            Bạn cũng có thể hỏi Trợ lý AI theo dạng: "Tìm văn bản mã CV/2026/..." hoặc "Tìm văn bản có tên ...".
            """),
        new KnowledgeItem(List.of("ocr", "trich thong tin van ban", "doc noi dung van ban", "nhan dang van ban"), """
            OCR là chức năng đọc chữ từ file PDF scan hoặc ảnh. Tại trang Chi tiết văn bản, mở khối "Phân tích AI", chọn tệp PDF/ảnh rồi nhấn "Nhận dạng văn bản". Nội dung nhận dạng được lưu vào văn bản và có thể dùng làm nguồn cho chức năng "Tóm tắt".
            """),
        new KnowledgeItem(List.of("tom tat", "tom tat van ban", "phan tich ai"), """
            Để tóm tắt văn bản: mở trang "Chi tiết văn bản" → mở khối "Phân tích AI" → nhấn "Tóm tắt". Nếu văn bản đã có nội dung OCR, hệ thống tóm tắt trực tiếp nội dung đó. Nếu chưa có OCR nhưng có tệp PDF/ảnh đính kèm, hệ thống sẽ thử nhận dạng tệp trước rồi mới tạo bản tóm tắt.
            """),
        new KnowledgeItem(List.of("sla", "cau hinh sla", "vi pham sla", "sap het han"), """
            SLA là thời hạn xử lý gắn với quy trình/bước xử lý. Trong mục "Cấu hình SLA", người quản trị thiết lập thời gian cho từng bước. Tab "Vi phạm SLA" hiển thị các công việc đang xử lý nhưng đã quá hạn; tab "Sắp hết hạn" hiển thị các công việc gần đến hạn để chủ động xử lý.
            """),
        new KnowledgeItem(List.of("phan quyen", "quyen truy cap", "bang phan quyen"), """
            Mục "Phân quyền" của Admin dùng để cấu hình quyền Xem/Tạo/Sửa/Xóa/Phê duyệt theo nhóm quyền. Với các chức năng nghiệp vụ, menu của Chuyên viên/Lãnh đạo được hiển thị theo quyền đã cấp; các menu quản trị thuần Admin không được đưa sang giao diện nghiệp vụ.
            """),
        new KnowledgeItem(List.of("quan ly don vi", "don vi la gi", "phong ban"), """
            Mục "Quản lý đơn vị" của Admin dùng để quản lý cơ cấu đơn vị/phòng ban. Thông tin đơn vị được dùng để giới hạn phạm vi xử lý, lọc Lãnh đạo đúng phòng và phục vụ báo cáo theo đơn vị.
            """),
        new KnowledgeItem(List.of("loai van ban", "quan ly loai van ban"), """
            Mục "Loại văn bản" dùng để quản lý danh mục như Công văn, Quyết định, Biên bản, Đề xuất... Danh mục này được dùng khi tạo/tải văn bản, gắn Template và cấu hình quy trình phù hợp.
            """),
        new KnowledgeItem(List.of("quan ly nguoi dung", "nguoi dung he thong"), """
            Mục "Quản lý người dùng" của Admin dùng để tạo, cập nhật và quản lý tài khoản, vai trò và đơn vị của người sử dụng hệ thống. Quyền thao tác cụ thể được cấu hình thêm tại màn hình "Phân quyền".
            """),
        new KnowledgeItem(List.of("ky duyet", "ban hanh", "da ban hanh"), """
            Ký duyệt/ban hành là bước cuối của luồng xử lý đối với văn bản đủ điều kiện. Sau khi Lãnh đạo xác nhận, văn bản được chuyển sang trạng thái đã ký/đã ban hành theo quy trình. Văn bản đã ban hành không được tiếp tục luân chuyển hoặc chỉnh sửa nghiệp vụ như văn bản đang xử lý.
            """),
        new KnowledgeItem(List.of("tai khoan", "ho so ca nhan", "doi mat khau"), """
            Mục "Tài khoản" dùng để xem và cập nhật thông tin cá nhân của tài khoản đang đăng nhập. Các chức năng liên quan đến hồ sơ hoặc đổi mật khẩu chỉ hiển thị theo những gì hệ thống hiện hỗ trợ.
            """)
    );

    public Optional<String> findAnswer(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) return Optional.empty();

        KnowledgeItem best = null;
        int bestScore = 0;
        for (KnowledgeItem item : ITEMS) {
            int score = 0;
            for (String keyword : item.keywords()) {
                String key = normalize(keyword);
                if (normalized.contains(key)) {
                    score = Math.max(score, key.split("\\s+").length * 10 + key.length());
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = item;
            }
        }
        return best == null ? Optional.empty() : Optional.of(best.answer().trim());
    }

    public boolean isSystemRelated(String question) {
        String normalized = normalize(question);
        if (normalized.isBlank()) return false;
        if (findAnswer(question).isPresent()) return true;
        List<String> domainTerms = List.of(
            "van ban", "cong van", "quyet dinh", "bien ban", "ho so", "template", "mau van ban",
            "phe duyet", "uy quyen", "quy trinh", "luong xu ly", "thong bao", "bao cao", "thong ke",
            "sla", "ocr", "ai", "chatbot", "tai len", "tim kiem", "tra cuu", "phan quyen", "don vi",
            "nguoi dung", "lanh dao", "truong don vi", "chuyen vien", "ban hanh", "ky duyet", "ky so"
        );
        return domainTerms.stream().map(this::normalize).anyMatch(normalized::contains);
    }

    public Map<String, Object> buildGuideStructuredData(String answer) {
        List<String> steps = new ArrayList<>();
        for (String line : answer.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.matches("^(Bước|Buoc)\\s+\\d+[:.]?.*")) {
                steps.add(trimmed);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        if (!steps.isEmpty()) data.put("steps", steps);
        return data;
    }

    private String normalize(String input) {
        if (input == null) return "";
        String lowered = input.toLowerCase(Locale.ROOT).trim();
        String nfd = Normalizer.normalize(lowered, Normalizer.Form.NFD);
        return nfd
            .replaceAll("\\p{M}+", "")
            .replaceAll("đ", "d")
            .replaceAll("[^a-z0-9\\s/._-]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }
}
