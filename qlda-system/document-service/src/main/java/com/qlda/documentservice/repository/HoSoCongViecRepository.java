package com.qlda.documentservice.repository;

import com.qlda.documentservice.entity.HoSoCongViec;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface HoSoCongViecRepository extends JpaRepository<HoSoCongViec, Long>, JpaSpecificationExecutor<HoSoCongViec> {
    Optional<HoSoCongViec> findByMaHoSo(String maHoSo);

    boolean existsByMaHoSo(String maHoSo);

    Page<HoSoCongViec> findByNhomHoSoIgnoreCase(String nhomHoSo, Pageable pageable);
}
