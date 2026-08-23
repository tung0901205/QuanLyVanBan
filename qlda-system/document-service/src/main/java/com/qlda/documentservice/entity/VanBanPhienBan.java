package com.qlda.documentservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "VanBanPhienBan")
@Getter
@Setter
@NoArgsConstructor
public class VanBanPhienBan {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "VanBanID", nullable = false)
    private VanBan vanBan;

    @Column(name = "TenPhienBan", nullable = false, length = 100)
    private String tenPhienBan;

    @Column(name = "FileUrl", length = 1000)
    private String fileUrl;

    @Column(name = "NoiDungThayDoi", columnDefinition = "TEXT")
    private String noiDungThayDoi;

    @Column(name = "NoiDungSnapshot", columnDefinition = "TEXT")
    private String noiDungSnapshot;

    @Column(name = "NguoiTaoID")
    private Long nguoiTaoId;

    @Column(name = "NgayTao", nullable = false)
    private LocalDateTime ngayTao;
}
