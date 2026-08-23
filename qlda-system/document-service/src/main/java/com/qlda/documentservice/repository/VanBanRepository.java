package com.qlda.documentservice.repository;

import com.qlda.documentservice.entity.VanBan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VanBanRepository extends JpaRepository<VanBan, Long>, JpaSpecificationExecutor<VanBan> {
    boolean existsBySoKyHieuAndDaXoaFalse(String soKyHieu);

    Optional<VanBan> findByIdAndDaXoaFalse(Long id);

    Page<VanBan> findByPhanLoaiVanBanAndDaXoaFalse(Integer phanLoaiVanBan, Pageable pageable);

    List<VanBan> findBySoKyHieuAndDaXoaFalse(String soKyHieu);

    List<VanBan> findByIdInAndDaXoaFalseAndNguoiTaoId(List<Long> ids, Long nguoiTaoId);

    List<VanBan> findByIdInAndDaXoaFalse(List<Long> ids);

    long countByDaXoaFalse();

    long countByDaXoaFalseAndNguoiTaoId(Long nguoiTaoId);

    @Query(value = """
        SELECT DISTINCT v.id
        FROM vanban v
        JOIN nguoidung u ON u.id = :userId
        LEFT JOIN nhomquyen nq ON nq.id = u.nhomquyenid
        WHERE v.id IN (:documentIds)
          AND COALESCE(v.daxoa, false) = false
          AND COALESCE(u.trangthai, 1) = 1
          AND (
              UPPER(COALESCE(nq.manhomquyen, '')) IN ('ADMIN', 'QUAN_TRI_HE_THONG')
              OR v.nguoitaoid = :userId
              OR (u.donviid IS NOT NULL AND v.donvichutriid = u.donviid)
              OR EXISTS (
                  SELECT 1 FROM xulyvanban x
                  WHERE x.vanbanid = v.id
                    AND (x.nguoinhanid = :userId OR x.nguoiguiid = :userId)
              )
          )
        """, nativeQuery = true)
    List<Long> findAccessibleDocumentIds(
        @Param("userId") Long userId,
        @Param("documentIds") List<Long> documentIds
    );
}
