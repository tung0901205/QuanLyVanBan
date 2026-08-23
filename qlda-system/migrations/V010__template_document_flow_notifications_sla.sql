SET client_encoding = 'UTF8';

-- Đồng bộ mô tả các template tích hợp sẵn với bộ DOCX placeholder mới.
UPDATE templatevanban SET noidungmau = E'{{DON_VI}}\nSố: {{SO_KY_HIEU}}\n{{DIA_DANH}}, ngày {{NGAY}} tháng {{THANG}} năm {{NAM}}\nV/v: {{TRICH_YEU}}\nKính gửi: {{NOI_NHAN}}\n\n{{NOI_DUNG}}\n\n{{CHUC_VU_NGUOI_KY}}\n{{NGUOI_KY}}', sudung = TRUE WHERE matemplate = 'TPL-CV-01';
UPDATE templatevanban SET noidungmau = E'{{DON_VI}}\nSố: {{SO_KY_HIEU}}\n{{DIA_DANH}}, ngày {{NGAY}} tháng {{THANG}} năm {{NAM}}\nV/v: {{TRICH_YEU}}\nTrả lời văn bản số {{SO_VAN_BAN_DEN}} ngày {{NGAY_VAN_BAN_DEN}} của {{DON_VI_GUI}}.\n\n{{NOI_DUNG_TRA_LOI}}\n\n{{CHUC_VU_NGUOI_KY}}\n{{NGUOI_KY}}', sudung = TRUE WHERE matemplate = 'TPL-CV-02';
UPDATE templatevanban SET noidungmau = E'BIÊN BẢN HỌP\nNgày họp: {{NGAY_HOP}} - Thời gian: {{THOI_GIAN_HOP}}\nĐịa điểm: {{DIA_DIEM_HOP}}\nChủ trì: {{CHU_TRI}} - Thư ký: {{THU_KY}}\nThành phần: {{THANH_PHAN_THAM_DU}}\n\nNội dung: {{NOI_DUNG_HOP}}\nKết luận: {{KET_LUAN}}', sudung = TRUE WHERE matemplate = 'TPL-BB-01';
UPDATE templatevanban SET noidungmau = E'BIÊN BẢN NGHIỆM THU\nHạng mục: {{HANG_MUC}}\nThời gian: {{THOI_GIAN_NGHIEM_THU}}\nĐịa điểm: {{DIA_DIEM_NGHIEM_THU}}\nĐơn vị thực hiện: {{DON_VI_THUC_HIEN}}\nĐơn vị nghiệm thu: {{DON_VI_NGHIEM_THU}}\nNội dung: {{NOI_DUNG_NGHIEM_THU}}\nKết quả: {{KET_QUA_NGHIEM_THU}}\nKiến nghị: {{KIEN_NGHI}}', sudung = TRUE WHERE matemplate = 'TPL-BB-02';
UPDATE templatevanban SET noidungmau = E'ĐỀ XUẤT MUA SẮM THIẾT BỊ\nĐơn vị đề xuất: {{DON_VI_DE_XUAT}}\nLý do: {{LY_DO_DE_XUAT}}\nDanh mục: {{DANH_MUC_MUA_SAM}}\nKinh phí dự kiến: {{KINH_PHI_DU_KIEN}}\nNguồn kinh phí: {{NGUON_KINH_PHI}}\nNgười lập: {{NGUOI_LAP}}', sudung = TRUE WHERE matemplate = 'TPL-DX-01';
UPDATE templatevanban SET noidungmau = E'QUYẾT ĐỊNH\nSố: {{SO_KY_HIEU}}\nV/v: {{TRICH_YEU}}\n{{CHUC_VU_BAN_HANH}}\nCăn cứ {{CAN_CU_1}}\nCăn cứ {{CAN_CU_2}}\nTheo đề nghị của {{DE_NGHI_CUA}}\nQUYẾT ĐỊNH:\nĐiều 1. {{DIEU_1}}\nĐiều 2. {{DIEU_2}}\nĐiều 3. {{DIEU_3}}\n{{CHUC_VU_NGUOI_KY}}\n{{NGUOI_KY}}', sudung = TRUE WHERE matemplate = 'TPL-QD-01';

-- Giữ nguyên dữ liệu nghiệp vụ; phần cuối chỉ soft-delete đúng các bản nháp lỗi do luồng template V2 sinh ra.

-- Ẩn các bản nháp lỗi được sinh bởi luồng template V2 cũ (tên tệp Ban-soan-thao-TPL-*).
-- Chỉ tác động đúng các văn bản đi ở trạng thái Nháp có dấu vết template cũ.
UPDATE vanban v
SET daxoa = TRUE,
    ngaycapnhat = CURRENT_TIMESTAMP
WHERE COALESCE(v.daxoa, FALSE) = FALSE
  AND v.phanloaivanban = 2
  AND v.trangthai = 0
  AND EXISTS (
      SELECT 1
      FROM tepdinhkem t
      WHERE t.vanbanid = v.id
        AND t.tentep LIKE 'Ban-soan-thao-TPL-%'
  );
