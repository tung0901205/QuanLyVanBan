import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../services/core/apiClient";
import { getCurrentUser, type AuthUser } from "../../services/auth/authApi";
import { getPermission, BUSINESS_PERMISSION_CODES } from "../../services/auth/permissionUtils";
import { fetchDocumentTypes } from "../../services/documents/documentTypesApi";
import { fetchUnits, type UnitItem } from "../../services/units/unitsApi";
import {
  createTemplate,
  deleteTemplate,
  fetchTemplates,
  updateTemplate,
  createDocumentFromTemplate,
  uploadTemplateFile,
  downloadTemplateFile,
  type TemplateItem,
} from "../../services/documents/templatesApi";

type DocumentTypeOption = {
  id: number;
  tenLoaiVanBan: string;
};

type CreatedDocument = {
  id: number;
  templateName: string;
};

type DynamicField = {
  key: string;
  label: string;
  type?: "text" | "textarea" | "date" | "time";
  required?: boolean;
  placeholder?: string;
};

type CreateForm = {
  soKyHieu: string;
  trichYeu: string;
  ngayVanBan: string;
  nguoiKy: string;
  chucVuNguoiKy: string;
  donViChuTriId: string;
  diaDanh: string;
  noiNhan: string;
  doMat: string;
  doKhan: string;
  values: Record<string, string>;
};

const today = () => new Date().toISOString().slice(0, 10);

const TEMPLATE_FIELDS: Record<string, DynamicField[]> = {
  "TPL-CV-01": [
    { key: "NOI_DUNG", label: "Nội dung công văn", type: "textarea", required: true, placeholder: "Nhập nội dung chính của công văn..." },
  ],
  "TPL-CV-02": [
    { key: "SO_VAN_BAN_DEN", label: "Số văn bản cần trả lời", required: true },
    { key: "NGAY_VAN_BAN_DEN", label: "Ngày văn bản cần trả lời", type: "date", required: true },
    { key: "DON_VI_GUI", label: "Đơn vị gửi văn bản", required: true },
    { key: "NOI_DUNG_TRA_LOI", label: "Nội dung trả lời", type: "textarea", required: true },
  ],
  "TPL-BB-01": [
    { key: "NGAY_HOP", label: "Ngày họp", type: "date", required: true },
    { key: "THOI_GIAN_HOP", label: "Thời gian họp", type: "time", required: true },
    { key: "DIA_DIEM_HOP", label: "Địa điểm họp", required: true },
    { key: "CHU_TRI", label: "Người chủ trì", required: true },
    { key: "THU_KY", label: "Thư ký", required: true },
    { key: "THANH_PHAN_THAM_DU", label: "Thành phần tham dự", type: "textarea", required: true },
    { key: "NOI_DUNG_HOP", label: "Nội dung cuộc họp", type: "textarea", required: true },
    { key: "KET_LUAN", label: "Kết luận cuộc họp", type: "textarea", required: true },
  ],
  "TPL-BB-02": [
    { key: "HANG_MUC", label: "Hạng mục nghiệm thu", required: true },
    { key: "THOI_GIAN_NGHIEM_THU", label: "Thời gian nghiệm thu", required: true, placeholder: "Ví dụ: 09:00 ngày 19/08/2026" },
    { key: "DIA_DIEM_NGHIEM_THU", label: "Địa điểm nghiệm thu", required: true },
    { key: "DON_VI_THUC_HIEN", label: "Đơn vị thực hiện", required: true },
    { key: "DON_VI_NGHIEM_THU", label: "Đơn vị nghiệm thu", required: true },
    { key: "NOI_DUNG_NGHIEM_THU", label: "Nội dung nghiệm thu", type: "textarea", required: true },
    { key: "KET_QUA_NGHIEM_THU", label: "Kết quả nghiệm thu", type: "textarea", required: true },
    { key: "KIEN_NGHI", label: "Kiến nghị", type: "textarea" },
    { key: "DAI_DIEN_THUC_HIEN", label: "Đại diện đơn vị thực hiện", required: true },
    { key: "DAI_DIEN_NGHIEM_THU", label: "Đại diện đơn vị nghiệm thu", required: true },
  ],
  "TPL-DX-01": [
    { key: "DON_VI_DE_XUAT", label: "Đơn vị đề xuất", required: true },
    { key: "LY_DO_DE_XUAT", label: "Lý do / nhu cầu", type: "textarea", required: true },
    { key: "DANH_MUC_MUA_SAM", label: "Danh mục mua sắm", type: "textarea", required: true, placeholder: "Ví dụ: 10 máy tính xách tay, cấu hình..." },
    { key: "KINH_PHI_DU_KIEN", label: "Kinh phí dự kiến", required: true },
    { key: "NGUON_KINH_PHI", label: "Nguồn kinh phí", required: true },
    { key: "NGUOI_LAP", label: "Người lập đề xuất", required: true },
  ],
  "TPL-QD-01": [
    { key: "CHUC_VU_BAN_HANH", label: "Chức vụ người ban hành", required: true, placeholder: "Ví dụ: GIÁM ĐỐC" },
    { key: "CAN_CU_1", label: "Căn cứ thứ nhất", required: true },
    { key: "CAN_CU_2", label: "Căn cứ thứ hai", required: true },
    { key: "DE_NGHI_CUA", label: "Theo đề nghị của", required: true },
    { key: "DIEU_1", label: "Điều 1", type: "textarea", required: true },
    { key: "DIEU_2", label: "Điều 2", type: "textarea", required: true },
    { key: "DIEU_3", label: "Điều 3", type: "textarea", required: true },
  ],
};

const COMMON_PLACEHOLDERS = new Set([
  "DON_VI", "SO_KY_HIEU", "TRICH_YEU", "NGUOI_KY", "CHUC_VU_NGUOI_KY",
  "DIA_DANH", "NOI_NHAN", "NGAY", "THANG", "NAM", "NGAY_VAN_BAN", "TEN_TEMPLATE",
]);

function extractFallbackFields(template: TemplateItem): DynamicField[] {
  const raw = template.noiDungMau ?? "";
  const matches = [...raw.matchAll(/\{\{([A-Z0-9_]+)\}\}/g)].map((match) => match[1]);
  return Array.from(new Set(matches))
    .filter((key) => !COMMON_PLACEHOLDERS.has(key))
    .map((key) => ({ key, label: key.replaceAll("_", " "), type: "textarea" as const }));
}

function initialCreateForm(user: AuthUser | null, unit?: UnitItem): CreateForm {
  return {
    soKyHieu: "",
    trichYeu: "",
    ngayVanBan: today(),
    nguoiKy: "",
    chucVuNguoiKy: "TRƯỞNG ĐƠN VỊ",
    donViChuTriId: user?.donViId ? String(user.donViId) : "",
    diaDanh: "Hà Nội",
    noiNhan: "",
    doMat: "CONG_KHAI",
    doKhan: "BINH_THUONG",
    values: unit ? { DON_VI_DE_XUAT: unit.tenDonVi, DON_VI_NGHIEM_THU: unit.tenDonVi } : {},
  };
}

export default function TemplateManagement() {
  const [templates, setTemplates] = useState<TemplateItem[]>([]);
  const [documentTypes, setDocumentTypes] = useState<DocumentTypeOption[]>([]);
  const [units, setUnits] = useState<UnitItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState<TemplateItem | null>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [searchTerm, setSearchTerm] = useState("");
  const [applyingTemplateId, setApplyingTemplateId] = useState<number | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [createTarget, setCreateTarget] = useState<TemplateItem | null>(null);
  const [createdDocument, setCreatedDocument] = useState<CreatedDocument | null>(null);
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null);
  const [createForm, setCreateForm] = useState<CreateForm>(() => initialCreateForm(null));

  const [formData, setFormData] = useState({
    maTemplate: "",
    tenTemplate: "",
    loaiVanBanId: undefined as number | undefined,
    noiDungMau: "",
    tepMau: "",
    suDung: true,
  });

  const templatePermission = useMemo(
    () => getPermission(currentUser, BUSINESS_PERMISSION_CODES.TEMPLATES),
    [currentUser]
  );
  const canCreateTemplate = Boolean(templatePermission?.isCreate);
  const canEditTemplate = Boolean(templatePermission?.isEdit);
  const canDeleteTemplate = Boolean(templatePermission?.isDelete);

  const reloadTemplates = async () => {
    const response = await fetchTemplates({ page: 0, size: 100, keyword: searchTerm || undefined });
    setTemplates(response.content || []);
  };

  useEffect(() => {
    const loadTemplates = async () => {
      setLoading(true);
      setError(null);
      try {
        const [templatesResponse, typesResponse, user, unitResponse] = await Promise.all([
          fetchTemplates({ page: 0, size: 100 }),
          fetchDocumentTypes({}),
          getCurrentUser(),
          fetchUnits({ page: 0, size: 200, suDung: true }),
        ]);
        setTemplates(templatesResponse.content || []);
        setDocumentTypes((typesResponse || []).map((item) => ({ id: item.id, tenLoaiVanBan: item.tenLoaiVanBan })));
        setCurrentUser(user);
        setUnits(unitResponse.content || []);
        const ownUnit = (unitResponse.content || []).find((u) => u.id === user.donViId);
        setCreateForm(initialCreateForm(user, ownUnit));
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Không thể tải template");
      } finally {
        setLoading(false);
      }
    };
    loadTemplates();
  }, []);

  const handleAddTemplate = () => {
    setEditingTemplate(null);
    setSelectedFile(null);
    setFormData({ maTemplate: "", tenTemplate: "", loaiVanBanId: undefined, noiDungMau: "", tepMau: "", suDung: true });
    setShowForm(true);
  };

  const handleEditTemplate = (template: TemplateItem) => {
    setEditingTemplate(template);
    setSelectedFile(null);
    setFormData({
      maTemplate: template.maTemplate,
      tenTemplate: template.tenTemplate,
      loaiVanBanId: template.loaiVanBanId,
      noiDungMau: template.noiDungMau || "",
      tepMau: template.tepMau || "",
      suDung: template.suDung,
    });
    setShowForm(true);
  };

  const handleSaveTemplate = async () => {
    try {
      if (!formData.maTemplate.trim() || !formData.tenTemplate.trim() || !formData.loaiVanBanId) {
        setActionMsg("Vui lòng điền đầy đủ mã, tên và loại văn bản.");
        return;
      }
      let templateId: number;
      if (editingTemplate) {
        const saved = await updateTemplate(editingTemplate.id, {
          tenTemplate: formData.tenTemplate.trim(),
          loaiVanBanId: formData.loaiVanBanId,
          noiDungMau: formData.noiDungMau,
          tepMau: formData.tepMau || undefined,
          suDung: formData.suDung,
        });
        templateId = saved.id;
      } else {
        const saved = await createTemplate({
          maTemplate: formData.maTemplate.trim(),
          tenTemplate: formData.tenTemplate.trim(),
          loaiVanBanId: formData.loaiVanBanId,
          noiDungMau: formData.noiDungMau,
          suDung: formData.suDung,
        });
        templateId = saved.id;
      }
      if (selectedFile) await uploadTemplateFile(templateId, selectedFile);
      setShowForm(false);
      setSelectedFile(null);
      setActionMsg(editingTemplate ? "Đã cập nhật template thành công." : "Đã tạo template thành công.");
      await reloadTemplates();
    } catch (err) {
      setActionMsg(err instanceof ApiError ? err.message : "Không thể lưu template.");
    }
  };

  const handleDeleteTemplate = async (id: number) => {
    if (!confirm("Bạn chắc chắn muốn ngừng sử dụng template này?")) return;
    try {
      await deleteTemplate(id);
      setActionMsg("Đã ngừng sử dụng template.");
      await reloadTemplates();
    } catch (err) {
      setActionMsg(err instanceof ApiError ? err.message : "Không thể cập nhật template.");
    }
  };

  const openCreateFromTemplate = (template: TemplateItem) => {
    const ownUnit = units.find((u) => u.id === currentUser?.donViId);
    const base = initialCreateForm(currentUser, ownUnit);
    const defaults: Record<string, string> = {};
    if (template.maTemplate === "TPL-BB-01") {
      defaults.NGAY_HOP = today();
      defaults.CHU_TRI = currentUser?.hoTen ?? "";
    }
    if (template.maTemplate === "TPL-DX-01") defaults.NGUOI_LAP = currentUser?.hoTen ?? "";
    setCreateForm({ ...base, trichYeu: template.tenTemplate, values: { ...base.values, ...defaults } });
    setCreateTarget(template);
    setCreatedDocument(null);
    setActionMsg(null);
  };

  const dynamicFields = useMemo(() => {
    if (!createTarget) return [];
    return TEMPLATE_FIELDS[createTarget.maTemplate] ?? extractFallbackFields(createTarget);
  }, [createTarget]);

  const handleCreateFromTemplate = async () => {
    if (!createTarget) return;
    const requiredCommon = [
      [createForm.soKyHieu, "Số ký hiệu"],
      [createForm.trichYeu, "Trích yếu"],
      [createForm.ngayVanBan, "Ngày văn bản"],
      [createForm.nguoiKy, "Người ký"],
      [createForm.donViChuTriId, "Đơn vị chủ trì"],
    ];
    const missingCommon = requiredCommon.find(([value]) => !String(value).trim());
    if (missingCommon) {
      setActionMsg(`Vui lòng nhập ${missingCommon[1]}.`);
      return;
    }
    const missingDynamic = dynamicFields.find((field) => field.required && !(createForm.values[field.key] ?? "").trim());
    if (missingDynamic) {
      setActionMsg(`Vui lòng nhập ${missingDynamic.label}.`);
      return;
    }

    const selectedUnit = units.find((u) => String(u.id) === createForm.donViChuTriId);
    const replaceData: Record<string, string> = {
      ...createForm.values,
      DON_VI: selectedUnit?.tenDonVi ?? "",
      SO_KY_HIEU: createForm.soKyHieu.trim(),
      TRICH_YEU: createForm.trichYeu.trim(),
      NGUOI_KY: createForm.nguoiKy.trim(),
      CHUC_VU_NGUOI_KY: createForm.chucVuNguoiKy.trim(),
      DIA_DANH: createForm.diaDanh.trim(),
      NOI_NHAN: createForm.noiNhan.trim(),
    };

    try {
      setApplyingTemplateId(createTarget.id);
      const res = await createDocumentFromTemplate({
        templateId: createTarget.id,
        soKyHieu: createForm.soKyHieu.trim(),
        trichYeu: createForm.trichYeu.trim(),
        loaiVanBanId: createTarget.loaiVanBanId,
        donViChuTriId: Number(createForm.donViChuTriId),
        nguoiKy: createForm.nguoiKy.trim(),
        ngayVanBan: createForm.ngayVanBan,
        doMat: createForm.doMat,
        doKhan: createForm.doKhan,
        replaceData,
      });
      setCreatedDocument({ id: res.documentId, templateName: createTarget.tenTemplate });
      setActionMsg(`Đã tạo văn bản đi #${res.documentId} hoàn chỉnh từ mẫu “${createTarget.tenTemplate}”. Văn bản đang ở trạng thái Đang xử lý và có thể trình duyệt ngay.`);
      setCreateTarget(null);
    } catch (err) {
      setActionMsg(err instanceof ApiError ? err.message : "Tạo văn bản từ mẫu thất bại.");
    } finally {
      setApplyingTemplateId(null);
    }
  };

  const handleDownloadTemplate = async (template: TemplateItem) => {
    if (!template.tepMau) {
      setActionMsg("Template chưa có tệp mẫu. Hãy bấm Sửa và chọn tệp từ máy tính.");
      return;
    }
    try {
      const blob = await downloadTemplateFile(template.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      const extension = template.tepMau.includes(".") ? template.tepMau.substring(template.tepMau.lastIndexOf(".")) : ".docx";
      a.href = url;
      a.download = `${template.maTemplate}${extension}`;
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      setActionMsg("Không thể tải tệp template.");
    }
  };

  const filteredTemplates = useMemo(() => {
    const key = searchTerm.trim().toLowerCase();
    if (!key) return templates;
    return templates.filter((template) =>
      template.tenTemplate.toLowerCase().includes(key) || template.maTemplate.toLowerCase().includes(key)
    );
  }, [templates, searchTerm]);

  const typeNameById = useMemo(
    () => new Map(documentTypes.map((type) => [type.id, type.tenLoaiVanBan])),
    [documentTypes]
  );

  if (loading) return <div className="admin-loading"><div className="admin-spinner" /><span>Đang tải template...</span></div>;
  if (error) return <div className="admin-loading">{error}</div>;

  return (
    <div className="admin-section">
      <div className="section-header">
        <div>
          <h2>Template văn bản</h2>
          <p style={{ margin: "4px 0 0", color: "var(--text-muted)", fontSize: 13 }}>
            Chọn mẫu, nhập đầy đủ thông tin nghiệp vụ và tạo ngay văn bản đi hoàn chỉnh.
          </p>
        </div>
        {canCreateTemplate && <button className="button primary" onClick={handleAddTemplate}>+ Thêm template</button>}
      </div>

      <div className="alert alert--info" style={{ marginBottom: 16, display: "block" }}>
        <strong>Cách dùng:</strong> bấm <strong>Tải mẫu</strong> để tải file Word mẫu chuẩn tiếng Việt. Bấm <strong>Tạo văn bản</strong> để nhập số ký hiệu, ngày văn bản, người ký và các trường riêng của từng mẫu. Hệ thống sẽ điền dữ liệu vào file Word, tạo <strong>Văn bản đi</strong> ở trạng thái <strong>Đang xử lý</strong>, không tạo bản nháp.
      </div>

      <div className="admin-filters">
        <input
          type="text"
          placeholder="Tìm kiếm theo tên hoặc mã template..."
          value={searchTerm}
          onChange={(e) => setSearchTerm(e.target.value)}
          className="search-input"
        />
      </div>

      {actionMsg && (
        <div className="alert alert--success" style={{ marginBottom: 16, display: "block" }}>
          <span>{actionMsg}</span>
          {createdDocument && (
            <span style={{ marginLeft: 10 }}>
              <Link to={`/documents/${createdDocument.id}`} style={{ fontWeight: 700 }}>Mở văn bản #{createdDocument.id}</Link>
              <span> · </span>
              <Link to="/outgoing" style={{ fontWeight: 700 }}>Xem trong Văn bản đi</Link>
            </span>
          )}
          <button onClick={() => { setActionMsg(null); setCreatedDocument(null); }} style={{ marginLeft: 12, background: "none", border: "none", cursor: "pointer" }}>✕</button>
        </div>
      )}

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(310px, 1fr))", gap: 16 }}>
        {filteredTemplates.map((template) => (
          <div className="card" key={template.id} style={{ padding: 0, overflow: "hidden" }}>
            <div style={{ padding: "16px 16px 12px", borderBottom: "1px solid var(--border)" }}>
              <div>
                <h3 style={{ margin: 0 }}>{template.tenTemplate}</h3>
                <span className={template.suDung ? "badge badge--success" : "badge badge--ghost"}>{template.suDung ? "SỬ DỤNG" : "NGỪNG"}</span>
              </div>
            </div>
            <div style={{ padding: "14px 16px", minHeight: 160 }}>
              <p><strong>Mã:</strong> <code>{template.maTemplate}</code></p>
              <p><strong>Loại:</strong> {template.tenLoaiVanBan || typeNameById.get(template.loaiVanBanId) || "-"}</p>
              <p><strong>Tệp mẫu:</strong> {template.tepMau ? "✅ Có file Word/PDF sẵn" : "⚠ Chưa có tệp"}</p>
              <div style={{ color: "var(--text-muted)", fontSize: 12, maxHeight: 70, overflow: "hidden", whiteSpace: "pre-line", lineHeight: 1.45, fontFamily: "Arial, sans-serif" }}>
                {template.noiDungMau || "Mẫu soạn thảo văn bản"}
              </div>
            </div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap", padding: "12px 16px 16px", borderTop: "1px solid var(--border)" }}>
              <button className="button secondary" type="button" disabled={!template.tepMau} onClick={() => handleDownloadTemplate(template)}>⬇ Tải mẫu</button>
              <button className="button" type="button" disabled={!template.suDung || applyingTemplateId === template.id} onClick={() => openCreateFromTemplate(template)}>📄 Tạo văn bản</button>
              {canEditTemplate && <button className="button secondary" type="button" onClick={() => handleEditTemplate(template)}>Sửa</button>}
              {canDeleteTemplate && template.suDung && <button className="button danger" type="button" onClick={() => handleDeleteTemplate(template.id)}>Ngừng dùng</button>}
            </div>
          </div>
        ))}
      </div>

      {filteredTemplates.length === 0 && <div className="empty-state"><h3>Không có template phù hợp</h3></div>}

      {showForm && (
        <div className="admin-form-modal" onClick={() => setShowForm(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <h3>{editingTemplate ? "Chỉnh sửa template" : "Thêm template mới"}</h3>
            <div className="form-row">
              <div className="form-group">
                <label>Mã template *</label>
                <input type="text" value={formData.maTemplate} disabled={Boolean(editingTemplate)} onChange={(e) => setFormData({ ...formData, maTemplate: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Tên template *</label>
                <input type="text" value={formData.tenTemplate} onChange={(e) => setFormData({ ...formData, tenTemplate: e.target.value })} />
              </div>
            </div>
            <div className="form-group">
              <label>Loại văn bản *</label>
              <select value={formData.loaiVanBanId ?? ""} onChange={(e) => setFormData({ ...formData, loaiVanBanId: e.target.value ? Number(e.target.value) : undefined })}>
                <option value="">-- Chọn loại văn bản --</option>
                {documentTypes.map((type) => <option key={type.id} value={type.id}>{type.tenLoaiVanBan}</option>)}
              </select>
            </div>
            <div className="form-group">
              <label>Nội dung mẫu / placeholder</label>
              <textarea rows={7} value={formData.noiDungMau} onChange={(e) => setFormData({ ...formData, noiDungMau: e.target.value })} placeholder="Ví dụ: {{SO_KY_HIEU}}, {{TRICH_YEU}}, {{NOI_DUNG}}..." />
            </div>
            <div className="form-group">
              <label>Tệp mẫu từ máy tính</label>
              <input type="file" accept=".doc,.docx,.pdf,.odt,.rtf" onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)} />
              <small style={{ color: "var(--text-muted)" }}>
                {selectedFile ? `Đã chọn: ${selectedFile.name}` : formData.tepMau ? "Đang có tệp mẫu; chỉ chọn file mới nếu muốn thay thế." : "Nên dùng DOCX có placeholder trùng với nội dung mẫu."}
              </small>
            </div>
            <label style={{ display: "flex", alignItems: "center", gap: 8, marginTop: 12 }}>
              <input type="checkbox" checked={formData.suDung} onChange={(e) => setFormData({ ...formData, suDung: e.target.checked })} /> Sử dụng
            </label>
            <div className="modal-actions">
              <button className="button secondary" type="button" onClick={() => setShowForm(false)}>Hủy</button>
              <button className="button primary" type="button" onClick={handleSaveTemplate}>Lưu</button>
            </div>
          </div>
        </div>
      )}

      {createTarget && (
        <div className="modal-overlay" onClick={() => setCreateTarget(null)}>
          <div className="modal-card" style={{ maxWidth: 760, maxHeight: "92vh", overflowY: "auto" }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3>Tạo văn bản từ mẫu</h3>
                <small style={{ color: "var(--text-muted)" }}>{createTarget.maTemplate} — {createTarget.tenTemplate}</small>
              </div>
              <button className="modal-close" onClick={() => setCreateTarget(null)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-row">
                <div className="form-field">
                  <label className="form-label">Số ký hiệu <span>*</span></label>
                  <input className="form-control" value={createForm.soKyHieu} onChange={(e) => setCreateForm({ ...createForm, soKyHieu: e.target.value })} placeholder="VD: 15/2026/CV-CNTT" />
                </div>
                <div className="form-field">
                  <label className="form-label">Ngày văn bản <span>*</span></label>
                  <input type="date" className="form-control" value={createForm.ngayVanBan} onChange={(e) => setCreateForm({ ...createForm, ngayVanBan: e.target.value })} />
                </div>
              </div>

              <div className="form-field">
                <label className="form-label">Trích yếu <span>*</span></label>
                <textarea className="form-control" rows={2} value={createForm.trichYeu} onChange={(e) => setCreateForm({ ...createForm, trichYeu: e.target.value })} />
              </div>

              <div className="form-row">
                <div className="form-field">
                  <label className="form-label">Đơn vị chủ trì <span>*</span></label>
                  <select className="form-control" value={createForm.donViChuTriId} onChange={(e) => setCreateForm({ ...createForm, donViChuTriId: e.target.value })}>
                    <option value="">-- Chọn đơn vị --</option>
                    {units.filter((unit) => !currentUser?.donViId || unit.id === currentUser.donViId).map((unit) => <option key={unit.id} value={unit.id}>{unit.tenDonVi}</option>)}
                  </select>
                </div>
                <div className="form-field">
                  <label className="form-label">Địa danh</label>
                  <input className="form-control" value={createForm.diaDanh} onChange={(e) => setCreateForm({ ...createForm, diaDanh: e.target.value })} />
                </div>
              </div>

              <div className="form-row">
                <div className="form-field">
                  <label className="form-label">Người ký <span>*</span></label>
                  <input className="form-control" value={createForm.nguoiKy} onChange={(e) => setCreateForm({ ...createForm, nguoiKy: e.target.value })} placeholder="Họ và tên người ký" />
                </div>
                <div className="form-field">
                  <label className="form-label">Chức vụ người ký</label>
                  <input className="form-control" value={createForm.chucVuNguoiKy} onChange={(e) => setCreateForm({ ...createForm, chucVuNguoiKy: e.target.value })} />
                </div>
              </div>

              <div className="form-field">
                <label className="form-label">Nơi nhận</label>
                <textarea className="form-control" rows={2} value={createForm.noiNhan} onChange={(e) => setCreateForm({ ...createForm, noiNhan: e.target.value })} placeholder="Ví dụ: Phòng Hành chính; Ban Giám đốc" />
              </div>

              {dynamicFields.length > 0 && (
                <>
                  <h4 style={{ margin: "18px 0 10px" }}>Thông tin riêng của mẫu</h4>
                  {dynamicFields.map((field) => (
                    <div className="form-field" key={field.key}>
                      <label className="form-label">{field.label} {field.required && <span>*</span>}</label>
                      {field.type === "textarea" ? (
                        <textarea className="form-control" rows={3} placeholder={field.placeholder} value={createForm.values[field.key] ?? ""} onChange={(e) => setCreateForm({ ...createForm, values: { ...createForm.values, [field.key]: e.target.value } })} />
                      ) : (
                        <input type={field.type ?? "text"} className="form-control" placeholder={field.placeholder} value={createForm.values[field.key] ?? ""} onChange={(e) => setCreateForm({ ...createForm, values: { ...createForm.values, [field.key]: e.target.value } })} />
                      )}
                    </div>
                  ))}
                </>
              )}

              <div className="form-row">
                <div className="form-field">
                  <label className="form-label">Độ mật</label>
                  <select className="form-control" value={createForm.doMat} onChange={(e) => setCreateForm({ ...createForm, doMat: e.target.value })}>
                    <option value="CONG_KHAI">Công khai</option>
                    <option value="NOI_BO">Nội bộ</option>
                    <option value="MAT">Mật</option>
                    <option value="TOI_MAT">Tối mật</option>
                    <option value="TUYET_MAT">Tuyệt mật</option>
                  </select>
                </div>
                <div className="form-field">
                  <label className="form-label">Độ khẩn</label>
                  <select className="form-control" value={createForm.doKhan} onChange={(e) => setCreateForm({ ...createForm, doKhan: e.target.value })}>
                    <option value="BINH_THUONG">Bình thường</option>
                    <option value="KHAN">Khẩn</option>
                    <option value="THUONG_KHAN">Thượng khẩn</option>
                    <option value="HOA_TOC">Hỏa tốc</option>
                  </select>
                </div>
              </div>

              <div className="alert alert--info" style={{ display: "block" }}>
                Sau khi bấm <strong>Tạo văn bản</strong>, hệ thống sẽ điền các trường trên vào file Word mẫu, tạo một bản Word riêng và lưu vào <strong>Văn bản đi</strong> ở trạng thái <strong>Đang xử lý</strong>. Bạn có thể mở văn bản để tải file hoàn chỉnh hoặc trình duyệt cho Trưởng đơn vị.
              </div>
            </div>
            <div className="modal-footer">
              <button className="button secondary" type="button" onClick={() => setCreateTarget(null)}>Hủy</button>
              <button className="button" type="button" disabled={applyingTemplateId === createTarget.id} onClick={handleCreateFromTemplate}>
                {applyingTemplateId === createTarget.id ? "Đang tạo..." : "Tạo văn bản hoàn chỉnh"}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
