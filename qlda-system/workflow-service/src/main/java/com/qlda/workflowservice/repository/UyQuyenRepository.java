package com.qlda.workflowservice.repository;

import com.qlda.workflowservice.entity.UyQuyen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface UyQuyenRepository extends JpaRepository<UyQuyen, Long> {
    List<UyQuyen> findByNguoiUyQuyenIdOrNguoiDuocUyQuyenId(Long nguoiUyQuyenId, Long nguoiDuocUyQuyenId);

    @Query("""
            SELECT u FROM UyQuyen u
            WHERE u.nguoiDuocUyQuyenId = :delegateId
              AND u.active = true
              AND u.tuNgay <= :today
              AND u.denNgay >= :today
            """)
    List<UyQuyen> findActiveByDelegate(@Param("delegateId") Long delegateId, @Param("today") LocalDate today);

    @Query("""
            SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM UyQuyen u
            WHERE u.nguoiUyQuyenId = :delegatorId
              AND u.nguoiDuocUyQuyenId = :delegateId
              AND u.active = true
              AND u.tuNgay <= :today
              AND u.denNgay >= :today
            """)
    boolean existsActiveDelegation(
            @Param("delegatorId") Long delegatorId,
            @Param("delegateId") Long delegateId,
            @Param("today") LocalDate today
    );

    @Modifying
    @Query("UPDATE UyQuyen u SET u.active = false WHERE u.active = true AND u.denNgay < :today")
    int deactivateExpired(LocalDate today);
}
