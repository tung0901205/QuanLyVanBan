-- Align workflow role codes with the roles actually issued in JWT tokens.
UPDATE buocquytrinh
SET vaitroxuly = 'LANH_DAO'
WHERE upper(vaitroxuly) IN ('TRUONG_PHONG', 'GIAM_DOC', 'MANAGER', 'LEADER');

-- Ensure the three designed roles exist on databases created before the latest seed.
INSERT INTO nhomquyen (manhomquyen, tennhomquyen, mota, sudung)
SELECT 'ADMIN', 'Quản trị hệ thống', 'Toàn quyền quản trị và cấu hình hệ thống', TRUE
WHERE NOT EXISTS (SELECT 1 FROM nhomquyen WHERE upper(manhomquyen) = 'ADMIN');

INSERT INTO nhomquyen (manhomquyen, tennhomquyen, mota, sudung)
SELECT 'LANH_DAO', 'Trưởng đơn vị', 'Phê duyệt, ký duyệt, ban hành và ủy quyền', TRUE
WHERE NOT EXISTS (SELECT 1 FROM nhomquyen WHERE upper(manhomquyen) = 'LANH_DAO');

INSERT INTO nhomquyen (manhomquyen, tennhomquyen, mota, sudung)
SELECT 'CHUYEN_VIEN', 'Chuyên viên', 'Tiếp nhận, tạo, cập nhật và trình duyệt văn bản', TRUE
WHERE NOT EXISTS (SELECT 1 FROM nhomquyen WHERE upper(manhomquyen) = 'CHUYEN_VIEN');

-- Move users from legacy role codes to the canonical roles used by JWT and the UI.
UPDATE nguoidung nd
SET nhomquyenid = (SELECT id FROM nhomquyen WHERE upper(manhomquyen) = 'LANH_DAO' LIMIT 1)
FROM nhomquyen old_role
WHERE nd.nhomquyenid = old_role.id
  AND upper(old_role.manhomquyen) IN ('MANAGER', 'LEADER', 'TRUONG_DON_VI', 'TRUONG_PHONG', 'GIAM_DOC', 'LANHDAO');

UPDATE nguoidung nd
SET nhomquyenid = (SELECT id FROM nhomquyen WHERE upper(manhomquyen) = 'CHUYEN_VIEN' LIMIT 1)
FROM nhomquyen old_role
WHERE nd.nhomquyenid = old_role.id
  AND upper(old_role.manhomquyen) IN ('STAFF', 'SPECIALIST', 'CHUYENVIEN');

-- Fix demo account role if it exists.
UPDATE nguoidung
SET nhomquyenid = (SELECT id FROM nhomquyen WHERE upper(manhomquyen) = 'CHUYEN_VIEN' LIMIT 1)
WHERE username = 'icetruong';

-- Keep the identity sequence above existing explicit seed IDs.
SELECT setval(
    pg_get_serial_sequence('nguoidung', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM nguoidung), 1), 1),
    true
);

-- Ensure a predictable manager demo account for role/workflow testing.
-- Username/password: lanhdao / 123456
INSERT INTO nguoidung (
    username, password, hoten, email, dienthoai, donviid, chucvu,
    nhomquyenid, azuread_id, trangthai, ngaytao, ngaycapnhat
)
SELECT
    'lanhdao',
    '$2y$10$hhhBmL7l5Iklx76Gse5l3OUWQWu2i78B4FWXK9K0zkwHToN6nBMVi',
    'Nguyễn Văn Tùng',
    'lanhdao@qlda.local',
    '0900001003',
    COALESCE((SELECT id FROM donvi WHERE madonvi = 'PCNTT' LIMIT 1), (SELECT MIN(id) FROM donvi)),
    'Trưởng đơn vị',
    (SELECT id FROM nhomquyen WHERE upper(manhomquyen) = 'LANH_DAO' LIMIT 1),
    NULL,
    1,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM nguoidung WHERE lower(username) = 'lanhdao');

UPDATE nguoidung
SET password = '$2y$10$hhhBmL7l5Iklx76Gse5l3OUWQWu2i78B4FWXK9K0zkwHToN6nBMVi',
    nhomquyenid = (SELECT id FROM nhomquyen WHERE upper(manhomquyen) = 'LANH_DAO' LIMIT 1),
    chucvu = 'Trưởng đơn vị',
    trangthai = 1,
    ngaycapnhat = CURRENT_TIMESTAMP
WHERE lower(username) = 'lanhdao';
