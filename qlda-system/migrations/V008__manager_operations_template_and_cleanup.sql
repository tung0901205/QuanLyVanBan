-- Align operational features with the designed role split:
-- ADMIN manages the system; LANH_DAO manages operational workflow/template/report screens.
-- This migration is intentionally idempotent because db-migrations executes every SQL file on each startup.

-- 1) Make the permission table wording explicit for the manager report function.
UPDATE chucnang
SET tenchucnang = 'Xem báo cáo thống kê',
    mota = 'Xem thống kê và báo cáo tình hình xử lý văn bản'
WHERE upper(machucnang) = 'VIEW_REPORTS';

UPDATE nhomquyen
SET mota = 'Phê duyệt, ký duyệt, quản lý quy trình, template văn bản và xem báo cáo thống kê'
WHERE upper(manhomquyen) = 'LANH_DAO';

-- 2) Ensure LANH_DAO can manage workflow/template and view reports.
INSERT INTO phanquyen (nhomquyenid, chucnangid, isview, iscreate, isedit, isdelete, isapprove)
SELECT r.id, f.id, TRUE, TRUE, TRUE, TRUE, FALSE
FROM nhomquyen r
JOIN chucnang f ON upper(f.machucnang) = 'MANAGE_WORKFLOWS'
WHERE upper(r.manhomquyen) = 'LANH_DAO'
  AND NOT EXISTS (
      SELECT 1 FROM phanquyen p
      WHERE p.nhomquyenid = r.id AND p.chucnangid = f.id
  );

UPDATE phanquyen p
SET isview = TRUE, iscreate = TRUE, isedit = TRUE, isdelete = TRUE
FROM nhomquyen r, chucnang f
WHERE p.nhomquyenid = r.id
  AND p.chucnangid = f.id
  AND upper(r.manhomquyen) = 'LANH_DAO'
  AND upper(f.machucnang) = 'MANAGE_WORKFLOWS';

INSERT INTO phanquyen (nhomquyenid, chucnangid, isview, iscreate, isedit, isdelete, isapprove)
SELECT r.id, f.id, TRUE, TRUE, TRUE, TRUE, FALSE
FROM nhomquyen r
JOIN chucnang f ON upper(f.machucnang) = 'MANAGE_TEMPLATES'
WHERE upper(r.manhomquyen) = 'LANH_DAO'
  AND NOT EXISTS (
      SELECT 1 FROM phanquyen p
      WHERE p.nhomquyenid = r.id AND p.chucnangid = f.id
  );

UPDATE phanquyen p
SET isview = TRUE, iscreate = TRUE, isedit = TRUE, isdelete = TRUE
FROM nhomquyen r, chucnang f
WHERE p.nhomquyenid = r.id
  AND p.chucnangid = f.id
  AND upper(r.manhomquyen) = 'LANH_DAO'
  AND upper(f.machucnang) = 'MANAGE_TEMPLATES';

INSERT INTO phanquyen (nhomquyenid, chucnangid, isview, iscreate, isedit, isdelete, isapprove)
SELECT r.id, f.id, TRUE, FALSE, FALSE, FALSE, FALSE
FROM nhomquyen r
JOIN chucnang f ON upper(f.machucnang) = 'VIEW_REPORTS'
WHERE upper(r.manhomquyen) = 'LANH_DAO'
  AND NOT EXISTS (
      SELECT 1 FROM phanquyen p
      WHERE p.nhomquyenid = r.id AND p.chucnangid = f.id
  );

UPDATE phanquyen p
SET isview = TRUE
FROM nhomquyen r, chucnang f
WHERE p.nhomquyenid = r.id
  AND p.chucnangid = f.id
  AND upper(r.manhomquyen) = 'LANH_DAO'
  AND upper(f.machucnang) = 'VIEW_REPORTS';

-- 3) Business approval steps must be handled by LANH_DAO instead of ADMIN.
UPDATE buocquytrinh
SET vaitroxuly = 'LANH_DAO',
    tenbuoc = CASE
        WHEN lower(tenbuoc) LIKE '%quản trị viên%' OR lower(tenbuoc) LIKE '%admin%'
            THEN 'Trưởng đơn vị phê duyệt'
        ELSE tenbuoc
    END
WHERE upper(vaitroxuly) = 'ADMIN'
  AND batbuocpheduyet = TRUE;

UPDATE quytrinh
SET mota = replace(replace(mota, 'Admin phê duyệt', 'Trưởng đơn vị phê duyệt'), 'ADMIN phê duyệt', 'Trưởng đơn vị phê duyệt')
WHERE mota IS NOT NULL
  AND (mota LIKE '%Admin phê duyệt%' OR mota LIKE '%ADMIN phê duyệt%');

-- 4) Existing unfinished operational tasks that were assigned to ADMIN are moved to the demo/active LANH_DAO.
UPDATE xulyvanban xv
SET nguoinhanid = mgr.id,
    donvixulyid = mgr.donviid
FROM nguoidung mgr
JOIN nhomquyen mgr_role ON mgr.nhomquyenid = mgr_role.id
WHERE upper(mgr_role.manhomquyen) = 'LANH_DAO'
  AND lower(mgr.username) = 'lanhdao'
  AND xv.trangthaixuly IN (0, 1)
  AND xv.nguoinhanid IN (
      SELECT au.id
      FROM nguoidung au
      JOIN nhomquyen ar ON au.nhomquyenid = ar.id
      WHERE upper(ar.manhomquyen) = 'ADMIN'
  );

-- Move unread approval notifications from ADMIN to LANH_DAO so the leader UI receives them.
UPDATE thongbao tb
SET nguoinhanid = mgr.id,
    tieude = replace(replace(tb.tieude, 'Admin', 'Trưởng đơn vị'), 'ADMIN', 'Trưởng đơn vị'),
    noidung = replace(replace(tb.noidung, 'Admin', 'Trưởng đơn vị'), 'ADMIN', 'Trưởng đơn vị')
FROM nguoidung mgr
JOIN nhomquyen mgr_role ON mgr.nhomquyenid = mgr_role.id
WHERE upper(mgr_role.manhomquyen) = 'LANH_DAO'
  AND lower(mgr.username) = 'lanhdao'
  AND tb.dadoc = FALSE
  AND upper(COALESCE(tb.loaithongbao, '')) IN ('PHE_DUYET', 'VAN_BAN')
  AND tb.nguoinhanid IN (
      SELECT au.id
      FROM nguoidung au
      JOIN nhomquyen ar ON au.nhomquyenid = ar.id
      WHERE upper(ar.manhomquyen) = 'ADMIN'
  );

-- Clear legacy URL placeholders that never pointed to an uploaded local file.
-- A real template file is now uploaded through /api/documents/templates/{id}/file
-- and stored as /uploads/<generated-name>.
UPDATE templatevanban
SET tepmau = NULL
WHERE tepmau IS NOT NULL
  AND (lower(tepmau) LIKE '/templates/%' OR lower(tepmau) LIKE 'templates/%');

-- 5) Clean only obsolete demo records tied to the removed Azure SSO sample flow.
-- Keep normal document/workflow history intact.
DELETE FROM thongbao
WHERE upper(COALESCE(loaithongbao, '')) = 'HE_THONG'
  AND (lower(tieude) LIKE '%azure%' OR lower(noidung) LIKE '%azure sso%');

DELETE FROM lichsuhethong
WHERE upper(COALESCE(hanhdong, '')) = 'DANG_NHAP'
  AND lower(COALESCE(noidungchitiet, '')) LIKE '%azure sso%';

-- Keep serial sequences safe after explicit seed IDs / permission inserts.
SELECT setval(
    pg_get_serial_sequence('phanquyen', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM phanquyen), 1), 1),
    true
);
