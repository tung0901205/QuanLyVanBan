import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../services/core/apiClient";
import { fetchDocumentTypes, type DocumentTypeItem } from "../../services/documents/documentTypesApi";
import { fetchUnits, type UnitItem } from "../../services/units/unitsApi";
import { fetchDirectoryUsers, type DirectoryUser } from "../../services/auth/directoryApi";
import { fetchWorkflows, fetchWorkflowDetail, type WorkflowStep } from "../../services/workflows/workflowsApi";
import { createIncomingDocument, transferIncomingDocument, uploadAttachment } from "../../services/documents/documentsApi";
import { indexDocument } from "../../services/ai/searchApi";
import { classifyDocument } from "../../services/ai/aiApi";
import { summarizeDocument } from "../../services/ai/userSummaryApi";
import { extractMetadata } from "../../services/ai/userMetadataApi";

const DO_MAT_OPTIONS = [
  { value: "CONG_KHAI", label: "Công khai" },
  { value: "NOI_BO", label: "Nội bộ" },
  { value: "MAT", label: "Mật" },
  { value: "TOI_MAT", label: "Tối mật" },
  { value: "TUYET_MAT", label: "Tuyệt mật" },
];

const DO_KHAN_OPTIONS = [
  { value: "BINH_THUONG", label: "Bình thường" },
  { value: "KHAN", label: "Khẩn" },
  { value: "THUONG_KHAN", label: "Thượng khẩn" },
  { value: "HOA_TOC", label: "Hỏa tốc" },
];

type AiAnalysisResult = {
  classification?: { phanLoai: string; confidence: number };
  summary?: string;
  metadata?: Record<string, string>;
  chunksCreated?: number;
};

type UploadForm = {
  soKyHieu: string;
  trichYeu: string;
  loaiVanBanId: string;
  ngayVanBan: string;
  ngayTiepNhan: string;
  donViBanHanhId: string;
  donViChuTriId: string;
  nguoiKy: string;
  doMat: string;
  doKhan: string;
  hanXuLy: string;
  nguoiNhanId: string;
  noiDungChuyen: string;
};

const today = () => new Date().toISOString().slice(0, 10);

const makeInitialForm = (): UploadForm => ({
  soKyHieu: "",
  trichYeu: "",
  loaiVanBanId: "",
  ngayVanBan: today(),
  ngayTiepNhan: today(),
  donViBanHanhId: "",
  donViChuTriId: "",
  nguoiKy: "",
  doMat: "CONG_KHAI",
  doKhan: "BINH_THUONG",
  hanXuLy: "",
  nguoiNhanId: "",
  noiDungChuyen: "Kính trình Trưởng đơn vị xem xét và phê duyệt.",
});

export default function Upload() {
  const [documentTypes, setDocumentTypes] = useState<DocumentTypeItem[]>([]);
  const [units, setUnits] = useState<UnitItem[]>([]);
  const [managers, setManagers] = useState<DirectoryUser[]>([]);
  const [workflowSuggestion, setWorkflowSuggestion] = useState<{
    id: number;
    tenQuyTrinh: string;
    steps: WorkflowStep[];
  } | null>(null);

  const [form, setForm] = useState<UploadForm>(() => makeInitialForm());
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);
  const [lookupLoading, setLookupLoading] = useState(true);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [message, setMessage] = useState<{ type: "success" | "error" | "info"; text: string } | null>(null);
  const [createdDocumentId, setCreatedDocumentId] = useState<number | null>(null);
  const [aiAnalyzing, setAiAnalyzing] = useState(false);
  const [aiResult, setAiResult] = useState<AiAnalysisResult | null>(null);

  const loadLookups = async () => {
    setLookupLoading(true);
    setLookupError(null);
    const [typesResult, unitsResult, managersResult] = await Promise.allSettled([
      fetchDocumentTypes({ suDung: true }),
      fetchUnits({ size: 200, suDung: true }),
      fetchDirectoryUsers({ role: "LANH_DAO" }),
    ]);

    if (typesResult.status === "fulfilled") setDocumentTypes(typesResult.value ?? []);
    if (unitsResult.status === "fulfilled") setUnits(unitsResult.value?.content ?? []);
    if (managersResult.status === "fulfilled") setManagers(managersResult.value ?? []);

    const failures: string[] = [];
    if (typesResult.status === "rejected") failures.push("loại văn bản");
    if (unitsResult.status === "rejected") failures.push("đơn vị");
    if (managersResult.status === "rejected") failures.push("Trưởng đơn vị");
    if (failures.length) {
      setLookupError(`Không tải được danh mục: ${failures.join(", ")}. Hãy kiểm tra Auth/Document Service rồi bấm Tải lại.`);
    }
    setLookupLoading(false);
  };

  useEffect(() => {
    queueMicrotask(() => void loadLookups());
  }, []);

  const selectedType = useMemo(
    () => documentTypes.find((item) => String(item.id) === form.loaiVanBanId),
    [documentTypes, form.loaiVanBanId]
  );

  const selectedManager = useMemo(
    () => managers.find((item) => String(item.id) === form.nguoiNhanId),
    [managers, form.nguoiNhanId]
  );

  const unitManagers = useMemo(
    () => form.donViChuTriId
      ? managers.filter((item) => String(item.donViId ?? "") === form.donViChuTriId)
      : [],
    [managers, form.donViChuTriId]
  );

  useEffect(() => {
    const loadWorkflow = async () => {
      const loaiVanBanId = Number(form.loaiVanBanId);
      if (!Number.isFinite(loaiVanBanId) || loaiVanBanId <= 0) {
        setWorkflowSuggestion(null);
        return;
      }
      try {
        const list = await fetchWorkflows({ page: 0, size: 20, loaiVanBanId, suDung: true });
        const first = list.content?.[0];
        if (!first) {
          setWorkflowSuggestion(null);
          return;
        }
        const detail = await fetchWorkflowDetail(first.id);
        const steps = [...(detail.steps ?? [])].sort((a, b) => a.thuTuBuoc - b.thuTuBuoc);
        setWorkflowSuggestion({ id: detail.id, tenQuyTrinh: detail.tenQuyTrinh, steps });
      } catch {
        setWorkflowSuggestion(null);
      }
    };
    loadWorkflow();
  }, [form.loaiVanBanId]);

  useEffect(() => {
    if (!form.donViChuTriId) {
      queueMicrotask(() => setForm((prev) => prev.nguoiNhanId ? { ...prev, nguoiNhanId: "" } : prev));
      return;
    }
    const matchingManagers = managers.filter((item) => String(item.donViId ?? "") === form.donViChuTriId);
    queueMicrotask(() => setForm((prev) => {
      const currentStillValid = matchingManagers.some((item) => String(item.id) === prev.nguoiNhanId);
      if (currentStillValid) return prev;
      return {
        ...prev,
        nguoiNhanId: matchingManagers.length === 1 ? String(matchingManagers[0].id) : "",
      };
    }));
  }, [form.donViChuTriId, managers]);

  const runAiAnalysis = async (documentId: number, text: string) => {
    setAiAnalyzing(true);
    setAiResult(null);
    const result: AiAnalysisResult = {};
    const [indexRes, classifyRes, summaryRes, metaRes] = await Promise.allSettled([
      indexDocument({ documentId, content: text }),
      classifyDocument({ documentId, text, language: "vi" }),
      summarizeDocument({ documentId, text, summaryType: "SHORT", language: "vi" }),
      extractMetadata({ documentId, text, language: "vi" }),
    ]);
    if (indexRes.status === "fulfilled") result.chunksCreated = indexRes.value.chunksCreated;
    if (classifyRes.status === "fulfilled") {
      result.classification = {
        phanLoai: classifyRes.value.phanLoai,
        confidence: classifyRes.value.confidence,
      };
    }
    if (summaryRes.status === "fulfilled") result.summary = summaryRes.value.summary;
    if (metaRes.status === "fulfilled") result.metadata = metaRes.value.metadata;
    setAiResult(result);
    setAiAnalyzing(false);
  };

  const validate = (mode: "draft" | "send") => {
    if (!form.trichYeu.trim()) return "Vui lòng nhập trích yếu nội dung";
    if (!form.loaiVanBanId) return "Vui lòng chọn loại văn bản";
    if (!form.donViChuTriId) return "Vui lòng chọn đơn vị chủ trì";
    if (mode === "send" && !form.nguoiNhanId) return "Vui lòng chọn Lãnh đạo của đúng đơn vị chủ trì";
    if (mode === "send" && (!selectedManager || String(selectedManager.donViId ?? "") !== form.donViChuTriId)) {
      return "Lãnh đạo nhận xử lý phải thuộc đúng đơn vị chủ trì đã chọn";
    }
    return null;
  };

  const handleSave = async (mode: "draft" | "send") => {
    const validation = validate(mode);
    if (validation) {
      setMessage({ type: "error", text: validation });
      return;
    }
    setLoading(true);
    setMessage(null);
    setCreatedDocumentId(null);
    let createdId: number | null = null;
    try {
      const unitIssuer = units.find((item) => String(item.id) === form.donViBanHanhId);
      const created = await createIncomingDocument({
        soKyHieu: form.soKyHieu.trim() || undefined,
        trichYeu: form.trichYeu.trim(),
        loaiVanBanId: Number(form.loaiVanBanId),
        donViBanHanh: unitIssuer?.tenDonVi,
        nguoiKy: form.nguoiKy.trim() || undefined,
        ngayVanBan: form.ngayVanBan || undefined,
        ngayTiepNhan: form.ngayTiepNhan || undefined,
        doMat: form.doMat,
        doKhan: form.doKhan,
        donViChuTriId: Number(form.donViChuTriId),
        hanXuLy: form.hanXuLy ? `${form.hanXuLy}T00:00:00` : undefined,
        trangThai: mode === "draft" ? 0 : 1,
      });

      createdId = created.id;
      setCreatedDocumentId(created.id);
      if (file) await uploadAttachment(created.id, file);

      if (mode === "send") {
        await transferIncomingDocument(created.id, {
          nguoiNhanId: Number(form.nguoiNhanId),
          donViXuLyId: Number(form.donViChuTriId),
          noiDungChuyen: form.noiDungChuyen.trim() || "Kính trình xem xét và phê duyệt.",
          hanXuLy: form.hanXuLy ? `${form.hanXuLy}T00:00:00` : undefined,
        });
      }

      setMessage({
        type: "success",
        text: mode === "send"
          ? "Đã đăng ký văn bản và chuyển cho Trưởng đơn vị xử lý."
          : "Đã lưu văn bản ở trạng thái Nháp.",
      });
      void runAiAnalysis(created.id, form.trichYeu.trim());
      setForm(makeInitialForm());
      setFile(null);
    } catch (error) {
      const detail = error instanceof ApiError ? error.message : "Không thể lưu văn bản";
      setMessage({
        type: "error",
        text: createdId
          ? `Văn bản #${createdId} đã được tạo nhưng chưa chuyển được vào luồng: ${detail}`
          : detail,
      });
    } finally {
      setLoading(false);
    }
  };

  return (
    <section>
      <div className="topbar">
        <div className="topbar__title">
          <h1>Tải lên văn bản</h1>
          <p>Đăng ký văn bản đến, đính kèm tệp và chuyển cho Trưởng đơn vị.</p>
        </div>
        <div className="topbar__actions">
          <button className="button secondary" type="button" disabled={loading || lookupLoading} onClick={() => handleSave("draft")}>
            {loading ? "Đang lưu..." : "Lưu nháp"}
          </button>
          <button className="button" type="button" disabled={loading || lookupLoading} onClick={() => handleSave("send")}>
            {loading ? "Đang gửi..." : "Gửi vào luồng"}
          </button>
        </div>
      </div>

      {lookupError && (
        <div className="alert alert--error" style={{ marginBottom: 16 }}>
          {lookupError}
          <button className="btn-xs btn-xs--ghost" type="button" onClick={loadLookups} style={{ marginLeft: 12 }}>
            Tải lại danh mục
          </button>
        </div>
      )}
      {message && (
        <div className={`alert alert--${message.type}`} style={{ marginBottom: 16 }}>
          {message.text}
          {createdDocumentId && (
            <span> <Link to={`/documents/${createdDocumentId}`}>Mở văn bản #{createdDocumentId}</Link></span>
          )}
        </div>
      )}

      <div className="grid-2">
        <div className="card">
          <div className="form-section">
            <div className="form-section__title">Thông tin cơ bản</div>
            <div className="form-field">
              <label className="form-label">Trích yếu nội dung <span>*</span></label>
              <input className="form-control" value={form.trichYeu} onChange={(e) => setForm({ ...form, trichYeu: e.target.value })} placeholder="Nhập trích yếu văn bản" />
            </div>
            <div className="form-row">
              <div className="form-field">
                <label className="form-label">Số ký hiệu</label>
                <input className="form-control" value={form.soKyHieu} onChange={(e) => setForm({ ...form, soKyHieu: e.target.value })} placeholder="VD: 123/UBND-VP" />
              </div>
              <div className="form-field">
                <label className="form-label">Loại văn bản <span>*</span></label>
                <select className="form-control" value={form.loaiVanBanId} onChange={(e) => setForm({ ...form, loaiVanBanId: e.target.value })}>
                  <option value="">-- Chọn loại văn bản --</option>
                  {documentTypes.map((item) => <option key={item.id} value={item.id}>{item.tenLoaiVanBan}</option>)}
                </select>
                {!lookupLoading && documentTypes.length === 0 && <small style={{ color: "var(--danger)" }}>Chưa có loại văn bản hoạt động.</small>}
              </div>
            </div>
            <div className="form-row">
              <div className="form-field">
                <label className="form-label">Ngày văn bản</label>
                <input type="date" className="form-control" value={form.ngayVanBan} onChange={(e) => setForm({ ...form, ngayVanBan: e.target.value })} />
              </div>
              <div className="form-field">
                <label className="form-label">Ngày tiếp nhận</label>
                <input type="date" className="form-control" value={form.ngayTiepNhan} onChange={(e) => setForm({ ...form, ngayTiepNhan: e.target.value })} />
              </div>
            </div>
          </div>

          <div className="form-section">
            <div className="form-section__title">Đơn vị &amp; Người ký</div>
            <div className="form-row">
              <div className="form-field">
                <label className="form-label">Đơn vị ban hành</label>
                <select className="form-control" value={form.donViBanHanhId} onChange={(e) => setForm({ ...form, donViBanHanhId: e.target.value })}>
                  <option value="">-- Chọn đơn vị --</option>
                  {units.map((item) => <option key={item.id} value={item.id}>{item.tenDonVi}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label className="form-label">Đơn vị chủ trì <span>*</span></label>
                <select className="form-control" value={form.donViChuTriId} onChange={(e) => setForm({ ...form, donViChuTriId: e.target.value })}>
                  <option value="">-- Chọn đơn vị --</option>
                  {units.map((item) => <option key={item.id} value={item.id}>{item.tenDonVi}</option>)}
                </select>
              </div>
            </div>
            <div className="form-field">
              <label className="form-label">Người ký trên văn bản</label>
              <input className="form-control" list="signer-suggestions" value={form.nguoiKy} onChange={(e) => setForm({ ...form, nguoiKy: e.target.value })} placeholder="Có thể nhập người ký bên ngoài cơ quan" />
              <datalist id="signer-suggestions">
                {managers.map((item) => <option key={item.id} value={item.hoTen}>{item.chucVu ?? item.tenNhomQuyen}</option>)}
              </datalist>
            </div>
          </div>

          <div className="form-section">
            <div className="form-section__title">Phân loại &amp; Hạn xử lý</div>
            <div className="form-row">
              <div className="form-field">
                <label className="form-label">Độ mật</label>
                <select className="form-control" value={form.doMat} onChange={(e) => setForm({ ...form, doMat: e.target.value })}>
                  {DO_MAT_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                </select>
              </div>
              <div className="form-field">
                <label className="form-label">Độ khẩn</label>
                <select className="form-control" value={form.doKhan} onChange={(e) => setForm({ ...form, doKhan: e.target.value })}>
                  {DO_KHAN_OPTIONS.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
                </select>
              </div>
            </div>
            <div className="form-field">
              <label className="form-label">Hạn xử lý</label>
              <input type="date" className="form-control" value={form.hanXuLy} onChange={(e) => setForm({ ...form, hanXuLy: e.target.value })} />
            </div>
          </div>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
          <div className="card">
            <div className="form-section__title">📎 Tập tin đính kèm</div>
            <input type="file" className="form-control" accept=".pdf,.doc,.docx,.xls,.xlsx,.png,.jpg,.jpeg" onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
            {file && <div className="alert alert--info" style={{ marginTop: 10 }}>📄 {file.name} ({(file.size / 1024).toFixed(1)} KB)</div>}
          </div>

          <div className="card soft">
            <div className="form-section__title">👤 Chuyển người xử lý</div>
            <div className="form-field">
              <label className="form-label">Trưởng đơn vị nhận xử lý <span>*</span></label>
              <select
                className="form-control"
                value={form.nguoiNhanId}
                disabled={!form.donViChuTriId || unitManagers.length === 0}
                onChange={(e) => setForm({ ...form, nguoiNhanId: e.target.value })}
              >
                <option value="">{form.donViChuTriId ? "-- Chọn Lãnh đạo đơn vị --" : "-- Chọn đơn vị chủ trì trước --"}</option>
                {unitManagers.map((item) => (
                  <option key={item.id} value={item.id}>{item.hoTen} — {item.tenDonVi ?? "Chưa gán đơn vị"}</option>
                ))}
              </select>
              {!lookupLoading && managers.length === 0 && <small style={{ color: "var(--danger)" }}>Chưa có tài khoản mang vai trò LANH_DAO. Admin cần tạo Lãnh đạo cho các đơn vị.</small>}
              {!lookupLoading && form.donViChuTriId && unitManagers.length === 0 && (
                <small style={{ color: "var(--danger)" }}>Đơn vị này chưa có Lãnh đạo. Không thể gửi vào luồng cho đến khi Admin gán một tài khoản LANH_DAO vào đúng đơn vị.</small>
              )}
              {!lookupLoading && unitManagers.length === 1 && (
                <small style={{ color: "var(--text-muted)" }}>Hệ thống đã tự chọn đúng Lãnh đạo của đơn vị chủ trì.</small>
              )}
            </div>
            <div className="form-field">
              <label className="form-label">Ý kiến trình duyệt</label>
              <textarea className="form-control" rows={3} value={form.noiDungChuyen} onChange={(e) => setForm({ ...form, noiDungChuyen: e.target.value })} />
            </div>
          </div>

          <div className="card soft">
            <div className="form-section__title">⚙️ Luồng xử lý đề xuất</div>
            {workflowSuggestion ? (
              <>
                <p style={{ fontWeight: 700, marginBottom: 10 }}>{workflowSuggestion.tenQuyTrinh}</p>
                <div className="timeline">
                  {workflowSuggestion.steps.map((step, index) => (
                    <div className="timeline-item" key={step.id}>
                      <div className="timeline-dot">{index + 1}</div>
                      <div className="timeline-content">
                        <h4>{step.tenBuoc}</h4>
                        <p>{step.vaiTroXuLy || "Chưa xác định vai trò"}{step.batBuocPheDuyet ? " • Bắt buộc phê duyệt" : ""}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </>
            ) : (
              <p style={{ color: "var(--text-muted)", fontSize: 13 }}>
                {selectedType ? "Không có quy trình hoạt động cho loại văn bản này; hệ thống vẫn cho phép luân chuyển thủ công." : "Chọn loại văn bản để xem luồng xử lý."}
              </p>
            )}
          </div>

          {(aiAnalyzing || aiResult) && (
            <div className="card soft">
              <div className="form-section__title">🤖 Phân tích AI</div>
              {aiAnalyzing && <p>Đang phân tích...</p>}
              {aiResult?.classification && <p><strong>Phân loại:</strong> {aiResult.classification.phanLoai}</p>}
              {aiResult?.summary && <p><strong>Tóm tắt:</strong> {aiResult.summary}</p>}
              {aiResult?.chunksCreated !== undefined && <p>Đã index {aiResult.chunksCreated} đoạn.</p>}
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
