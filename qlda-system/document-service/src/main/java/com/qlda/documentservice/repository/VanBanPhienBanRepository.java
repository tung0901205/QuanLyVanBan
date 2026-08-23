package com.qlda.documentservice.repository;

import com.qlda.documentservice.entity.VanBanPhienBan;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VanBanPhienBanRepository extends JpaRepository<VanBanPhienBan, Long> {
    List<VanBanPhienBan> findByVanBan_IdOrderByNgayTaoAsc(Long vanBanId);
    Optional<VanBanPhienBan> findByVanBan_IdAndTenPhienBan(Long vanBanId, String tenPhienBan);
}
