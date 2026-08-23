import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../services/core/apiClient";
import { fetchOutgoingDocuments } from "../../services/documents/documentsOutgoingApi";
import { createOutgoingDocument } from "../../services/documents/documentsOutgoingCreateApi";
import { fetchDocumentTypes, type DocumentTypeItem } from "../../services/documents/documentTypesApi";
import { uploadAttachment } from "../../services/documents/documentsApi";
import type { DocumentListItem } from "../../services/documents/documentsApi";
import { submitOutgoingApproval } from "../../services/documents/documentsOutgoingActionsApi";
import { fetchDirectoryUsers, type DirectoryUser } from "../../services/auth/directoryApi";
import { getCurrentRoles, getStoredUser, hasAnyRole, ROLE_STAFF } from "../../services/auth/roleUtils";

const TRANG_THAI_MAP: Record<number, { label: string; className: string }> = {
  0: { label: "Nháp", className: "badge badge--ghost" },
  1: { label: "Đang xử lý", className: "badge badge--info" },
  2: { label: "Đang duyệt", className: "badge badge--info" },
  3: { label: "Trình ký", className: "badge badge--primary" },
  4: { label: "Đã ký", className: "badge badge--success" },
  5: { label: "Đã ban hành", className: "badge badge--success" },
};

const DO_KHAN_MAP: Record<string, { label: string; className: string }> = {
  BINH_THUONG: { label: "Bình thường", className: "badge badge--ghost" },
  KHAN: { label: "Khẩn", className: "badge badge--danger" },
  THUONG_KHAN: { label: "Thượng khẩn", className: "badge badge--danger" },
  HOA_TOC: { label: "Hỏa tốc", className: "badge badge--danger" },
};

function getTrangThaiBadge(trangThai?: number) {
  const key = trangThai ?? -1;
  return TRANG_THAI_MAP[key] ?? { label: "Không xác định", className: "badge badge--ghost" };
}

type CreateForm = {
  trichYeu: string; soKyHieu: string; loaiVanBanId: string;
  nguoiKy: string; ngayVanBan: string; doKhan: string;
};

const EMPTY_FORM: CreateForm = {
  trichYeu: "", soKyHieu: "", loaiVanBanId: "", nguoiKy: "", ngayVanBan: "", doKhan: "BINH_THUONG",
};

type UploadStatus = { file: File; status: "pending" | "uploading" | "done" | "error"; error?: string };

export default function OutgoingDocuments() {
  const storedUser = getStoredUser();
  const roles = getCurrentRoles(storedUser);
  const canCreate = hasAnyRole(roles, [ROLE_STAFF]);
  const [documents, setDocuments] = useState<DocumentListItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [docTypes, setDocTypes] = useState<DocumentTypeItem[]>([]);

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState<CreateForm>(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [createSuccess, setCreateSuccess] = useState(false);
  const [attachFiles, setAttachFiles] = useState<UploadStatus[]>([]);
  const [managers, setManagers] = useState<DirectoryUser[]>([]);
  const [approvalDoc, setApprovalDoc] = useState<DocumentListItem | null>(null);
  const [approvalManagerId, setApprovalManagerId] = useState("");
  const [approvalNote, setApprovalNote] = useState("Kính trình Trưởng đơn vị xem xét và phê duyệt.");
  const [approvalSubmitting, setApprovalSubmitting] = useState(false);
  const [approvalError, setApprovalError] = useState<string | null>(null);

  const [filterStatus, setFilterStatus] = useState<string>("");
  const [filterFrom, setFilterFrom] = useState("");
  const [filterTo, setFilterTo] = useState("");

  const load = (status?: number, from?: string, to?: string) => {
    setLoading(true);
    setError(null);
    fetchOutgoingDocuments({ page: 0, size: 50, trangThai: status, fromDate: from, toDate: to })
      .then((res) => setDocuments(res.content || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không thể tải văn bản đi"))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    queueMicrotask(() => void load());
    fetchDocumentTypes({ suDung: true }).then(setDocTypes).catch(() => {});
    fetchDirectoryUsers({ role: "LANH_DAO", donViId: storedUser?.donViId }).then(setManagers).catch(() => {});
  }, [storedUser?.donViId]);

  const handleFilter = () => {
    load(filterStatus ? parseInt(filterStatus, 10) : undefined, filterFrom || undefined, filterTo || undefined);
  };

  const handleResetFilter = () => {
    setFilterStatus(""); setFilterFrom(""); setFilterTo(""); load();
  };

  const openCreate = () => {
    setForm(EMPTY_FORM); setCreateError(null); setCreateSuccess(false); setAttachFiles([]); setShowCreate(true);
  };

  const closeCreate = () => {
    setShowCreate(false); setCreateError(null); setCreateSuccess(false); setAttachFiles([]);
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []) as File[];
    setAttachFiles((prev) => [
      ...prev,
      ...files.map((f) => ({ file: f, status: "pending" as const })),
    ]);
    e.target.value = "";
  };

  const removeFile = (idx: number) => {
    setAttachFiles((prev) => prev.filter((_, i) => i !== idx));
  };

  const handleCreate = async () => {
    if (!form.trichYeu.trim()) { setCreateError("Vui lòng nhập trích yếu nội dung"); return; }
    setCreating(true); setCreateError(null);
    try {
      const created = await createOutgoingDocument({
        trichYeu: form.trichYeu.trim(),
        soKyHieu: form.soKyHieu.trim() || undefined,
        loaiVanBanId: form.loaiVanBanId ? parseInt(form.loaiVanBanId, 10) : undefined,
        nguoiKy: form.nguoiKy.trim() || undefined,
        ngayVanBan: form.ngayVanBan || undefined,
        doKhan: form.doKhan || undefined,
      });
      if (attachFiles.length > 0 && created?.id) {
        for (let i = 0; i < attachFiles.length; i++) {
          setAttachFiles((prev) => prev.map((f, idx) => idx === i ? { ...f, status: "uploading" } : f));
          try {
            await uploadAttachment(created.id, attachFiles[i].file);
            setAttachFiles((prev) => prev.map((f, idx) => idx === i ? { ...f, status: "done" } : f));
          } catch {
            setAttachFiles((prev) => prev.map((f, idx) => idx === i ? { ...f, status: "error", error: "Tải lên thất bại" } : f));
          }
        }
      }
      setCreateSuccess(true);
      setTimeout(() => { closeCreate(); load(); }, 900);
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.message : "Tạo văn bản thất bại");
    } finally {
      setCreating(false);
    }
  };

  const openApproval = (doc: DocumentListItem) => {
    setApprovalDoc(doc);
    setApprovalManagerId(managers.length === 1 ? String(managers[0].id) : "");
    setApprovalNote("Kính trình Trưởng đơn vị xem xét và phê duyệt.");
    setApprovalError(null);
  };

  const handleSubmitApproval = async () => {
    if (!approvalDoc) return;
    const managerId = Number(approvalManagerId);
    if (!Number.isFinite(managerId) || managerId <= 0) {
      setApprovalError("Vui lòng chọn Trưởng đơn vị phê duyệt");
      return;
    }
    setApprovalSubmitting(true);
    setApprovalError(null);
    try {
      await submitOutgoingApproval(approvalDoc.id, {
        nguoiPheDuyetId: managerId,
        noiDungTrinh: approvalNote.trim() || undefined,
      });
      setDocuments((items) => items.map((item) => item.id === approvalDoc.id ? { ...item, trangThai: 3 } : item));
      setApprovalDoc(null);
    } catch (error) {
      setApprovalError(error instanceof ApiError ? error.message : "Không thể trình duyệt văn bản");
    } finally {
      setApprovalSubmitting(false);
    }
  };

  const exportCsv = () => {
    const headers = ["Mã", "Loại", "Nội dung", "Ngày văn bản", "Trạng thái"];
    const rows = documents.map((d) => [
      d.soKyHieu || "-", d.tenLoaiVanBan || "-",
      `"${(d.trichYeu || "").replace(/"/g, '""')}"`,
      (d.ngayVanBan || d.ngayTiepNhan) ? new Date(d.ngayVanBan || d.ngayTiepNhan!).toLocaleDateString("vi-VN") : "-",
      getTrangThaiBadge(d.trangThai).label,
    ]);
    const csv = [headers.join(","), ...rows.map((r) => r.join(","))].join("\n");
    const blob = new Blob(["﻿" + csv], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = `van-ban-di_${new Date().toISOString().split("T")[0]}.csv`; a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <section>
      <div className="topbar">
        <div className="topbar__title">
          <h1>Văn bản đi</h1>
          <p>Quản lý và theo dõi các văn bản ban hành.</p>
        </div>
        <div className="topbar__actions">
          <button className="button secondary" type="button" onClick={exportCsv}>Xuất CSV</button>
          {canCreate && <button className="button" type="button" onClick={openCreate}>+ Tạo văn bản đi</button>}
        </div>
      </div>

      <div className="filter-bar">
        <select
          className="form-control"
          style={{ minWidth: 150 }}
          value={filterStatus}
          onChange={(e) => setFilterStatus(e.target.value)}
        >
          <option value="">Tất cả trạng thái</option>
          {Object.entries(TRANG_THAI_MAP).map(([k, v]) => (
            <option key={k} value={k}>{v.label}</option>
          ))}
        </select>
        <input type="date" className="form-control" value={filterFrom} onChange={(e) => setFilterFrom(e.target.value)} style={{ width: 150 }} />
        <input type="date" className="form-control" value={filterTo} onChange={(e) => setFilterTo(e.target.value)} style={{ width: 150 }} />
        <button className="button" type="button" onClick={handleFilter}>Lọc</button>
        <button className="button secondary" type="button" onClick={handleResetFilter}>Xóa lọc</button>
      </div>

      {error && <div className="alert alert--error" style={{ marginBottom: 16 }}>{error}</div>}

      <div className="card">
        {loading && (
          <div className="loading-state">
            <div className="loading-spinner" />
            <p>Đang tải văn bản đi...</p>
          </div>
        )}
        {!loading && !error && documents.length === 0 && (
          <div className="empty-state">
            <div className="empty-state__icon">📤</div>
            <h3>Chưa có văn bản đi</h3>
            <p>Tạo văn bản đi đầu tiên bằng nút phía trên.</p>
          </div>
        )}
        {documents.length > 0 && (
          <div style={{ overflowX: "auto" }}>
          <table className="table">
            <thead>
              <tr>
                <th>Mã</th><th>Loại</th><th>Nội dung</th><th>Ngày</th><th>Ưu tiên</th><th>Trạng thái</th><th>Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {documents.map((doc) => {
                const stt = getTrangThaiBadge(doc.trangThai);
                const khan = doc.doKhan ? (DO_KHAN_MAP[doc.doKhan] ?? null) : null;
                return (
                  <tr key={doc.id}>
                    <td style={{ fontWeight: 600, whiteSpace: "nowrap" }}>{doc.soKyHieu || "-"}</td>
                    <td style={{ fontSize: 13, whiteSpace: "nowrap" }}>{doc.tenLoaiVanBan || "-"}</td>
                    <td style={{ maxWidth: 260 }}>
                      <Link
                        to={`/documents/${doc.id}`}
                        title={doc.trichYeu}
                        style={{
                          color: "var(--accent-strong)",
                          fontWeight: 500,
                          display: "block",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {doc.trichYeu}
                      </Link>
                    </td>
                    <td style={{ fontSize: 13, whiteSpace: "nowrap" }}>
                      {(doc.ngayVanBan || doc.ngayTiepNhan) ? new Date(doc.ngayVanBan || doc.ngayTiepNhan!).toLocaleDateString("vi-VN") : "-"}
                    </td>
                    <td style={{ whiteSpace: "nowrap" }}>
                      {khan ? <span className={khan.className}>{khan.label}</span> : <span className="badge badge--ghost">—</span>}
                    </td>
                    <td style={{ whiteSpace: "nowrap" }}><span className={stt.className}>{stt.label}</span></td>
                    <td style={{ whiteSpace: "nowrap" }}>
                      <Link className="btn-xs btn-xs--ghost" to={`/documents/${doc.id}`}>Xem</Link>
                      {canCreate && (doc.trangThai ?? 0) <= 1 && (
                        <button className="btn-xs btn-xs--primary" type="button" onClick={() => openApproval(doc)} style={{ marginLeft: 6 }}>
                          Trình duyệt
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          </div>
        )}
      </div>

      {approvalDoc && (
        <div className="modal-overlay" onClick={() => setApprovalDoc(null)}>
          <div className="modal-card" style={{ maxWidth: 520 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>📨 Trình duyệt văn bản đi</h3>
              <button className="modal-close" onClick={() => setApprovalDoc(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="modal-doc-ref"><strong>{approvalDoc.soKyHieu || `VB-${approvalDoc.id}`}</strong> — {approvalDoc.trichYeu}</div>
              <div className="form-field">
                <label className="form-label">Trưởng đơn vị phê duyệt <span>*</span></label>
                <select className="form-control" value={approvalManagerId} onChange={(e) => setApprovalManagerId(e.target.value)}>
                  <option value="">-- Chọn Trưởng đơn vị --</option>
                  {managers.map((manager) => <option key={manager.id} value={manager.id}>{manager.hoTen} — {manager.tenDonVi || "Chưa gán đơn vị"}</option>)}
                </select>
                {managers.length === 0 && <small style={{ color: "var(--danger)" }}>Chưa có Trưởng đơn vị thuộc đúng đơn vị của người lập văn bản.</small>}
                {managers.length > 0 && <small style={{ color: "var(--text-muted)" }}>Chỉ hiển thị Lãnh đạo thuộc cùng đơn vị với người lập văn bản.</small>}
              </div>
              <div className="form-field">
                <label className="form-label">Ý kiến trình duyệt</label>
                <textarea className="form-control" rows={3} value={approvalNote} onChange={(e) => setApprovalNote(e.target.value)} />
              </div>
              {approvalError && <div className="alert alert--error">{approvalError}</div>}
            </div>
            <div className="modal-footer">
              <button className="button secondary" onClick={() => setApprovalDoc(null)} disabled={approvalSubmitting}>Hủy</button>
              <button className="button" onClick={handleSubmitApproval} disabled={approvalSubmitting}>
                {approvalSubmitting ? "Đang trình..." : "Xác nhận trình duyệt"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showCreate && (
        <div className="modal-overlay" onClick={closeCreate}>
          <div className="modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>📝 Tạo văn bản đi mới</h3>
              <button className="modal-close" onClick={closeCreate}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-section">
                <div className="form-field">
                  <label className="form-label">Trích yếu nội dung <span>*</span></label>
                  <textarea className="form-control" rows={3} placeholder="Nhập trích yếu nội dung văn bản..."
                    value={form.trichYeu} onChange={(e) => setForm({ ...form, trichYeu: e.target.value })} />
                </div>
                <div className="form-row">
                  <div className="form-field">
                    <label className="form-label">Số ký hiệu</label>
                    <input type="text" className="form-control" placeholder="VD: 01/2026/QĐ-ABC"
                      value={form.soKyHieu} onChange={(e) => setForm({ ...form, soKyHieu: e.target.value })} />
                  </div>
                  <div className="form-field">
                    <label className="form-label">Loại văn bản</label>
                    <select className="form-control" value={form.loaiVanBanId}
                      onChange={(e) => setForm({ ...form, loaiVanBanId: e.target.value })}>
                      <option value="">-- Chọn loại văn bản --</option>
                      {docTypes.map((t) => <option key={t.id} value={t.id}>{t.tenLoaiVanBan}</option>)}
                    </select>
                  </div>
                </div>
                <div className="form-row">
                  <div className="form-field">
                    <label className="form-label">Người ký</label>
                    <input type="text" className="form-control" placeholder="Họ tên người ký..."
                      value={form.nguoiKy} onChange={(e) => setForm({ ...form, nguoiKy: e.target.value })} />
                  </div>
                  <div className="form-field">
                    <label className="form-label">Ngày văn bản</label>
                    <input type="date" className="form-control"
                      value={form.ngayVanBan} onChange={(e) => setForm({ ...form, ngayVanBan: e.target.value })} />
                  </div>
                </div>
                <div className="form-field">
                  <label className="form-label">Độ khẩn</label>
                  <select className="form-control" value={form.doKhan}
                    onChange={(e) => setForm({ ...form, doKhan: e.target.value })}>
                    <option value="BINH_THUONG">Bình thường</option>
                    <option value="KHAN">Khẩn</option>
                    <option value="THUONG_KHAN">Thượng khẩn</option>
                    <option value="HOA_TOC">Hỏa tốc</option>
                  </select>
                </div>
              </div>
              <div className="form-field">
                <label className="form-label">Tệp đính kèm</label>
                <input type="file" multiple onChange={handleFileChange} style={{ fontSize: 13 }} />
                {attachFiles.length > 0 && (
                  <ul style={{ listStyle: "none", padding: 0, margin: "8px 0 0", display: "flex", flexDirection: "column", gap: 4 }}>
                    {attachFiles.map((f, i) => (
                      <li key={i} style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }}>
                        <span style={{ flex: 1, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{f.file.name}</span>
                        <span style={{ color: f.status === "done" ? "#22c55e" : f.status === "error" ? "#ef4444" : f.status === "uploading" ? "#f59e0b" : "var(--text-muted)" }}>
                          {f.status === "done" ? "✓" : f.status === "error" ? "✕" : f.status === "uploading" ? "..." : "chờ"}
                        </span>
                        {f.status === "pending" && (
                          <button type="button" onClick={() => removeFile(i)} style={{ color: "#ef4444", background: "none", border: "none", cursor: "pointer", padding: 0, fontSize: 12 }}>✕</button>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
              {createSuccess && <div className="alert alert--success">Tạo văn bản thành công!</div>}
              {createError && <div className="alert alert--error">{createError}</div>}
            </div>
            <div className="modal-footer">
              <button className="button secondary" type="button" onClick={closeCreate} disabled={creating}>Hủy</button>
              <button className="button" type="button" onClick={handleCreate} disabled={creating}>
                {creating ? "Đang tạo..." : "Tạo văn bản"}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
