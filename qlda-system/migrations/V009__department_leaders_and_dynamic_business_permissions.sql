SET client_encoding = 'UTF8';

-- Ensure every configured unit has a LANH_DAO demo account so upload/transfer can
-- select the leader of the exact chosen department. Password for demo accounts: 123456.

INSERT INTO nguoidung (
    username, password, hoten, email, dienthoai, donviid, chucvu,
    nhomquyenid, azuread_id, trangthai, ngaytao, ngaycapnhat
)
SELECT x.username,
       '$2y$10$hhhBmL7l5Iklx76Gse5l3OUWQWu2i78B4FWXK9K0zkwHToN6nBMVi',
       x.hoten, x.email, x.dienthoai, d.id, x.chucvu,
       r.id, NULL, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (VALUES
    ('lanhdao',      'Nguyễn Văn Tùng', 'lanhdao@qlda.local',      '0900001003', 'PCNTT', 'Trưởng phòng CNTT'),
    ('lanhdao_ktc',  'Nguyễn Minh Khoa','lanhdao.ktc@qlda.local',  '0900001004', 'PKTC',  'Trưởng phòng KH&TC'),
    ('lanhdao_hc',   'Trần Thu Hà',     'lanhdao.hc@qlda.local',   '0900001005', 'PHC',   'Trưởng phòng Hành chính'),
    ('lanhdao_qlda', 'Lê Hoàng Nam',    'lanhdao.qlda@qlda.local', '0900001006', 'BQLDA', 'Trưởng Ban QLDA'),
    ('giamdoc',      'Phạm Quốc Anh',   'giamdoc@qlda.local',      '0900001007', 'BGD',   'Giám đốc')
) AS x(username, hoten, email, dienthoai, madonvi, chucvu)
JOIN donvi d ON upper(d.madonvi) = upper(x.madonvi)
JOIN nhomquyen r ON upper(r.manhomquyen) = 'LANH_DAO'
WHERE NOT EXISTS (
    SELECT 1 FROM nguoidung u WHERE lower(u.username) = lower(x.username)
);

-- Correct existing demo leader records if the project was upgraded from an older ZIP.
UPDATE nguoidung u
SET donviid = d.id,
    nhomquyenid = r.id,
    trangthai = 1,
    ngaycapnhat = CURRENT_TIMESTAMP
FROM donvi d, nhomquyen r
WHERE upper(r.manhomquyen) = 'LANH_DAO'
  AND (
      (lower(u.username) = 'lanhdao'      AND upper(d.madonvi) = 'PCNTT') OR
      (lower(u.username) = 'lanhdao_ktc'  AND upper(d.madonvi) = 'PKTC') OR
      (lower(u.username) = 'lanhdao_hc'   AND upper(d.madonvi) = 'PHC') OR
      (lower(u.username) = 'lanhdao_qlda' AND upper(d.madonvi) = 'BQLDA') OR
      (lower(u.username) = 'giamdoc'       AND upper(d.madonvi) = 'BGD')
  );

SELECT setval(
    pg_get_serial_sequence('nguoidung', 'id'),
    GREATEST(COALESCE((SELECT MAX(id) FROM nguoidung), 1), 1),
    true
);

-- Re-route unfinished work items to the leader of THE EXACT processing unit.
-- V008 moved old Admin tasks to the first demo leader; this pass corrects upgraded
-- databases so each department's pending item appears on its own leader's screen.
WITH leader_per_unit AS (
    SELECT u.donviid, MIN(u.id) AS leader_id
    FROM nguoidung u
    JOIN nhomquyen r ON r.id = u.nhomquyenid
    WHERE upper(r.manhomquyen) = 'LANH_DAO'
      AND u.trangthai = 1
      AND u.donviid IS NOT NULL
    GROUP BY u.donviid
)
UPDATE xulyvanban xv
SET nguoinhanid = l.leader_id
FROM leader_per_unit l
WHERE xv.donvixulyid = l.donviid
  AND xv.trangthaixuly IN (0, 1)
  AND EXISTS (
      SELECT 1
      FROM buocquytrinh b
      WHERE b.id = xv.buocquytrinhid
        AND b.batbuocpheduyet = TRUE
  )
  AND NOT EXISTS (
      SELECT 1
      FROM nguoidung current_recipient
      JOIN nhomquyen recipient_role ON recipient_role.id = current_recipient.nhomquyenid
      WHERE current_recipient.id = xv.nguoinhanid
        AND current_recipient.trangthai = 1
        AND current_recipient.donviid = xv.donvixulyid
        AND upper(recipient_role.manhomquyen) = 'LANH_DAO'
  );

-- Keep unread approval notifications aligned with the department leader as well.
-- Prefer the document's owning/processing unit; this is only for unfinished notices.
WITH leader_per_unit AS (
    SELECT u.donviid, MIN(u.id) AS leader_id
    FROM nguoidung u
    JOIN nhomquyen r ON r.id = u.nhomquyenid
    WHERE upper(r.manhomquyen) = 'LANH_DAO'
      AND u.trangthai = 1
      AND u.donviid IS NOT NULL
    GROUP BY u.donviid
), target_notice AS (
    SELECT tb.id AS notification_id,
           COALESCE(
               (SELECT xv.donvixulyid
                FROM xulyvanban xv
                WHERE xv.vanbanid = tb.vanbanid
                  AND xv.trangthaixuly IN (0, 1)
                ORDER BY xv.id DESC
                LIMIT 1),
               vb.donvichutriid
           ) AS unit_id
    FROM thongbao tb
    LEFT JOIN vanban vb ON vb.id = tb.vanbanid
    WHERE tb.dadoc = FALSE
      AND upper(COALESCE(tb.loaithongbao, '')) = 'PHE_DUYET'
)
UPDATE thongbao tb
SET nguoinhanid = l.leader_id,
    tieude = replace(replace(tb.tieude, 'Admin', 'Trưởng đơn vị'), 'ADMIN', 'Trưởng đơn vị'),
    noidung = replace(replace(tb.noidung, 'Admin', 'Trưởng đơn vị'), 'ADMIN', 'Trưởng đơn vị')
FROM target_notice tn
JOIN leader_per_unit l ON l.donviid = tn.unit_id
WHERE tb.id = tn.notification_id
  AND NOT EXISTS (
      SELECT 1
      FROM nguoidung current_recipient
      JOIN nhomquyen recipient_role ON recipient_role.id = current_recipient.nhomquyenid
      WHERE current_recipient.id = tb.nguoinhanid
        AND current_recipient.trangthai = 1
        AND current_recipient.donviid = tn.unit_id
        AND upper(recipient_role.manhomquyen) = 'LANH_DAO'
  );
