import { useEffect, useState } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { ApiError } from "../../services/core/apiClient";
import { fetchDocumentDetail, uploadAttachment } from "../../services/documents/documentsApi";
import { fetchWorkflowTimeline } from "../../services/workflows/workflowTrackingApi";
import { getCurrentUser, type AuthUser } from "../../services/auth/authApi";
import { fetchPendingApprovals, type PendingApprovalItem } from "../../services/workflows/approvalsApi";
import { approveProcessing, rejectProcessing } from "../../services/workflows/workflowApprovalsApi";
import { fetchAttachments, triggerDownload } from "../../services/documents/documentAttachmentsApi";
import {
  signDocument, publishDocument, getOneDriveEditUrl, getSignatureInfo, type SignatureInfo,
} from "../../services/documents/documentsPublishApi";
import type { DocumentDetail as DocDetail } from "../../services/documents/documentsApi";
import type { AttachmentItem } from "../../services/documents/documentAttachmentsApi";
import type { WorkflowTimelineItem } from "../../services/workflows/workflowTrackingApi";
import { summarizeDocument } from "../../services/ai/userSummaryApi";
import { fetchAiResultsByDocument, deleteAiResult, type AiResultItem } from "../../services/ai/aiResultsApi";
import { ocrFileDirect } from "../../services/ai/ocrApi";
import { getCurrentRoles, isManager } from "../../services/auth/roleUtils";

const TRANG_THAI_MAP: Record<number, { label: string; className: string }> = {
  0: { label: "Nháp", className: "badge badge--ghost" },
  1: { label: "Đang xử lý", className: "badge badge--info" },
  2: { label: "Đã chuyển xử lý", className: "badge badge--warning" },
  3: { label: "Trình ký", className: "badge badge--primary" },
  4: { label: "Đã ký", className: "badge badge--success" },
  5: { label: "Đã ban hành", className: "badge badge--success" },
};

const formatSize = (bytes?: number) => {
  if (!bytes) return "-";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const formatDate = (dateStr?: string) => dateStr ? new Date(dateStr).toLocaleDateString("vi-VN") : "-";
const formatDateTime = (dateStr?: string | null) => dateStr ? new Date(dateStr).toLocaleString("vi-VN") : "-";

export default function DocumentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();

  const [doc, setDoc] = useState<DocDetail | null>(null);
  const [attachments, setAttachments] = useState<AttachmentItem[]>([]);
  const [timeline, setTimeline] = useState<WorkflowTimelineItem[]>([]);
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(null);
  const [pendingApproval, setPendingApproval] = useState<PendingApprovalItem | null>(null);
  const [oneDriveUrl, setOneDriveUrl] = useState<string | null>(null);
  const [signatureInfo, setSignatureInfo] = useState<SignatureInfo | null>(null);
  const [showSignModal, setShowSignModal] = useState(false);
  const [showSigInfoModal, setShowSigInfoModal] = useState(false);
  const [showPublishModal, setShowPublishModal] = useState(false);
  const [signNote, setSignNote] = useState("");
  const [publishDate, setPublishDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [publishNote, setPublishNote] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionMsg, setActionMsg] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [showRejectInput, setShowRejectInput] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [replacementFile, setReplacementFile] = useState<File | null>(null);
  const [uploadingReplacement, setUploadingReplacement] = useState(false);

  // AI panel state
  const [aiResults, setAiResults] = useState<AiResultItem[]>([]);
  const [aiLoading, setAiLoading] = useState(false);
  const [aiMsg, setAiMsg] = useState<string | null>(null);
  const [showAiPanel, setShowAiPanel] = useState(false);
  // OCR state
  const [ocrFile, setOcrFile] = useState<File | null>(null);
  const [ocrText, setOcrText] = useState<string | null>(null);
  const [ocrProcessing, setOcrProcessing] = useState(false);

  useEffect(() => {
    if (!id) return;
    const documentId = Number(id);
    const loadDetail = async () => {
      setLoading(true);
      setError(null);
      try {
        const detail = await fetchDocumentDetail(documentId);
        setDoc(detail);

        const user = await getCurrentUser().catch(() => null);
        setCurrentUser(user);

        const optionalResults = await Promise.allSettled([
          fetchAttachments(documentId),
          fetchWorkflowTimeline(documentId),
          getOneDriveEditUrl(documentId),
        ]);
        if (optionalResults[0].status === "fulfilled") setAttachments(optionalResults[0].value ?? []);
        if (optionalResults[1].status === "fulfilled") setTimeline(optionalResults[1].value ?? []);
        if (optionalResults[2].status === "fulfilled") setOneDriveUrl(optionalResults[2].value ?? null);

        // Hỏi backend danh sách phê duyệt cho MỌI người dùng nghiệp vụ. Backend sẽ
        // tự lọc theo quyền trực tiếp hoặc ủy quyền đang còn hiệu lực. Vì vậy Chuyên
        // viên được Lãnh đạo ủy quyền cũng thấy khối Phê duyệt khi mở chi tiết VB.
        if (user) {
          const pendingResp = await fetchPendingApprovals({ page: 0, size: 100, nguoiDuyetId: user.id }).catch(() => null);
          const match = pendingResp?.content?.find((item) => item.documentId === documentId);
          setPendingApproval(match ?? null);
        } else {
          setPendingApproval(null);
        }

        if (detail.daKySo) {
          const sigResp = await getSignatureInfo(documentId).catch(() => null);
          setSignatureInfo(sigResp ?? null);
        } else {
          setSignatureInfo(null);
        }
      } catch (err) {
        setError(err instanceof ApiError ? err.message : "Không thể tải chi tiết văn bản");
      } finally {
        setLoading(false);
      }
    };
    loadDetail();
  }, [id]);

  const handleApprove = async () => {
    if (!pendingApproval) return;
    if (!window.confirm("Xác nhận phê duyệt văn bản này?")) return;
    setSubmitting(true); setActionMsg(null); setActionError(null);
    try {
      await approveProcessing(pendingApproval.processingId, { chuyenBuocTiepTheo: true });
      setActionMsg("Phê duyệt thành công. Văn bản đã sẵn sàng để ký duyệt.");
      setPendingApproval(null);
      setDoc((previous) => previous ? { ...previous, trangThai: 3 } : previous);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Phê duyệt thất bại");
    } finally { setSubmitting(false); }
  };

  const handleReject = async () => {
    if (!pendingApproval) return;
    if (!rejectReason.trim()) { setActionError("Vui lòng nhập lý do từ chối"); return; }
    if (!window.confirm("Xác nhận từ chối văn bản này?")) return;
    setSubmitting(true); setActionMsg(null); setActionError(null);
    try {
      await rejectProcessing(pendingApproval.processingId, { lyDoTuChoi: rejectReason });
      setActionMsg("Đã từ chối văn bản!");
      setPendingApproval(null);
      setTimeout(() => navigate("/dashboard"), 1500);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Từ chối thất bại");
    } finally { setSubmitting(false); }
  };

  const handleSign = async () => {
    if (!currentUser || !id) return;
    setSubmitting(true); setActionMsg(null); setActionError(null);
    try {
      await signDocument(Number(id), {
        signatureType: "LOCAL_HASH_SHA256",
        ghiChu: signNote.trim() || undefined,
      });
      setActionMsg("Ký số thành công!");
      setShowSignModal(false); setSignNote("");
      setDoc((prev) => prev ? { ...prev, daKySo: true, trangThai: 4 } : prev);
      getSignatureInfo(Number(id))
        .then((r) => setSignatureInfo(r ?? null))
        .catch(() => null);
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Ký số thất bại");
    } finally { setSubmitting(false); }
  };

  const handleUploadEditedFile = async () => {
    if (!doc || !replacementFile) return;
    if (doc.trangThai === 5) {
      setActionError("Văn bản đã ban hành nên không thể thêm hoặc thay đổi tệp.");
      return;
    }
    setUploadingReplacement(true);
    setActionError(null);
    setActionMsg(null);
    try {
      await uploadAttachment(doc.id, replacementFile);
      const refreshed = await fetchAttachments(doc.id);
      setAttachments(refreshed ?? []);
      setReplacementFile(null);
      setActionMsg("Đã tải bản soạn thảo đã chỉnh sửa lên văn bản.");
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Không thể tải tệp đã chỉnh sửa lên văn bản");
    } finally {
      setUploadingReplacement(false);
    }
  };

  const handlePublish = async () => {
    if (!id || !publishDate) return;
    setSubmitting(true);
    setActionMsg(null);
    setActionError(null);
    try {
      await publishDocument(Number(id), {
        ngayPhatHanh: publishDate,
        noiDungPhatHanh: publishNote.trim() || undefined,
      });
      setDoc((previous) => previous ? { ...previous, trangThai: 5 } : previous);
      setShowPublishModal(false);
      setPublishNote("");
      setActionMsg("Ban hành văn bản thành công!");
    } catch (err) {
      setActionError(err instanceof ApiError ? err.message : "Ban hành văn bản thất bại");
    } finally {
      setSubmitting(false);
    }
  };

  const isTemplateDocument = attachments.some((file) =>
    /^(Van-ban-tu-mau-|Ban-soan-thao-TPL-)/i.test(file.tenTep || "")
  );

  const currentRoles = getCurrentRoles(currentUser);
  const canSign = Boolean(
    currentUser &&
    isManager(currentRoles) &&
    !pendingApproval &&
    !doc?.daKySo &&
    doc?.trangThai === 3
  );
  const canPublish = Boolean(currentUser && isManager(currentRoles) && doc?.daKySo && doc?.trangThai === 4);

  const stt = doc ? TRANG_THAI_MAP[doc.trangThai ?? -1] : null;

  const loadAiResults = async (docId: number) => {
    try {
      const items = await fetchAiResultsByDocument(docId);
      setAiResults(items || []);
    } catch { /* ignore */ }
  };

  const handleAiSummarize = async () => {
    if (!doc || !id) return;
    if (!currentUser?.id) {
      setAiMsg("Không xác định được người dùng hiện tại. Vui lòng đăng nhập lại.");
      return;
    }
    setAiLoading(true);
    setAiMsg("Đang đọc nội dung văn bản và tạo bản tóm tắt...");
    try {
      // Nếu đã có OCR thì gửi trực tiếp. Nếu chưa có, backend AI sẽ tự tìm
      // tệp đính kèm (DOCX/PDF/ảnh), trích nội dung rồi tóm tắt.
      const res = await summarizeDocument({
        documentId: doc.id,
        userId: currentUser.id,
        text: doc.noiDungOCR?.trim() || "",
        summaryType: "SHORT",
        language: "vi",
      });
      const sourceLabel = res.source === "ATTACHMENT"
        ? `tệp ${res.attachmentName || "đính kèm"}`
        : res.source === "OCR_SAVED" || doc.noiDungOCR?.trim()
          ? "nội dung OCR đã lưu"
          : res.source === "TITLE"
            ? "trích yếu (không tìm thấy nội dung tệp)"
            : "nội dung văn bản";
      setAiMsg(`Tóm tắt từ ${sourceLabel}:\n${res.summary}`);
      loadAiResults(doc.id);
      // Tải lại chi tiết để nhận NoiDungOCR nếu backend vừa OCR tệp đính kèm.
      try {
        const refreshed = await fetchDocumentDetail(doc.id);
        setDoc(refreshed);
        if (refreshed.noiDungOCR?.trim()) setOcrText(refreshed.noiDungOCR);
      } catch { /* summary vẫn hợp lệ */ }
    } catch (err) {
      const message = err instanceof ApiError ? err.message : (err instanceof Error ? err.message : "Tóm tắt thất bại");
      setAiMsg(`Không thể tóm tắt văn bản: ${message}`);
    } finally {
      setAiLoading(false);
    }
  };

  const handleDeleteAiResult = async (resultId: number) => {
    if (!doc) return;
    try {
      await deleteAiResult(resultId);
      setAiResults((prev) => prev.filter((r) => r.id !== resultId));
    } catch { /* ignore */ }
  };

  const handleOcrProcess = async () => {
    if (!ocrFile || !doc || !currentUser?.id) return;
    setOcrProcessing(true);
    setOcrText(null);
    try {
      const processRes = await ocrFileDirect(doc.id, currentUser.id, ocrFile);
      setOcrText(processRes.ocrText);
      setDoc((prev) => prev ? { ...prev, daOCR: true, noiDungOCR: processRes.ocrText } : prev);
      setAiMsg(`Đã nhận dạng nội dung từ tệp ${ocrFile.name}. Bạn có thể bấm “Tóm tắt” để tóm tắt nội dung này.`);
      loadAiResults(doc.id);
    } catch (err) {
      setOcrText(err instanceof ApiError ? `Lỗi: ${err.message}` : "OCR thất bại");
    } finally {
      setOcrProcessing(false);
    }
  };

  return (
    <section>
      <div className="topbar">
        <div className="topbar__title">
          <h1>Chi tiết văn bản</h1>
          <p>Thông tin đầy đủ, luồng xử lý và tệp đính kèm.</p>
        </div>
      </div>

      {actionMsg && <div className="alert alert--success" style={{ marginBottom: 16 }}>{actionMsg}</div>}
      {actionError && <div className="alert alert--error" style={{ marginBottom: 16 }}>{actionError}</div>}

      {loading && (
        <div className="card">
          <div className="loading-state">
            <div className="loading-spinner" />
            <p>Đang tải chi tiết văn bản...</p>
          </div>
        </div>
      )}
      {error && <div className="alert alert--error">{error}</div>}

      {!loading && !error && doc && (
        <div className="grid-2">
          {/* LEFT COLUMN */}
          <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
            <div className="card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12, marginBottom: 16 }}>
                <div>
                  <h2 style={{ fontSize: 18, marginBottom: 6 }}>{doc.soKyHieu || `VB-${doc.id}`}</h2>
                  <p style={{ fontWeight: 500, color: "var(--text)", fontSize: 14 }}>{doc.trichYeu}</p>
                </div>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center", flexShrink: 0 }}>
                  {doc.daKySo && (
                    <button
                      className="badge badge--success"
                      style={{ cursor: "pointer", border: "none" }}
                      onClick={() => setShowSigInfoModal(true)}
                    >
                      ✍️ Đã ký số
                    </button>
                  )}
                  {doc.aiPhanLoai && (
                    <span
                      className="badge badge--info"
                      title={doc.aiConfidence != null ? `Độ tin cậy: ${Math.round(doc.aiConfidence * 100)}%` : "AI gợi ý phân loại"}
                    >
                      🤖 AI: {doc.aiPhanLoai}
                      {doc.aiConfidence != null && (
                        <span style={{ opacity: 0.75, fontSize: 11, marginLeft: 3 }}>
                          ({Math.round(doc.aiConfidence * 100)}%)
                        </span>
                      )}
                    </span>
                  )}
                  {oneDriveUrl && (
                    <a href={oneDriveUrl} target="_blank" rel="noopener noreferrer" className="btn-xs btn-xs--ghost">
                      📝 Word Online
                    </a>
                  )}
                  {canSign && (
                    <button className="button" style={{ fontSize: 13, padding: "6px 14px" }}
                      onClick={() => { setShowSignModal(true); setActionError(null); }}>
                      ✍️ Ký số
                    </button>
                  )}
                  {canPublish && (
                    <button className="button" style={{ fontSize: 13, padding: "6px 14px" }}
                      onClick={() => { setShowPublishModal(true); setActionError(null); }}>
                      📣 Ban hành
                    </button>
                  )}
                </div>
              </div>

              <table className="doc-meta-table">
                <tbody>
                  {[
                    { label: "Loại văn bản", value: doc.tenLoaiVanBan || "-" },
                    { label: "Nơi gửi", value: doc.donViBanHanh || "-" },
                    { label: "Người ký", value: doc.nguoiKy || "-" },
                    { label: "Ngày tiếp nhận", value: formatDate(doc.ngayTiepNhan) },
                    { label: "Ngày văn bản", value: formatDate(doc.ngayVanBan) },
                    { label: "Độ khẩn", value: doc.doKhan || "-" },
                    { label: "Độ mật", value: doc.doMat || "-" },
                    { label: "Hạn xử lý", value: formatDate(doc.hanXuLy) },
                    { label: "Trạng thái", value: stt ? <span className={stt.className}>{stt.label}</span> : "-" },
                  ].map(({ label, value }) => (
                    <tr key={label}>
                      <td>{label}</td>
                      <td>{value}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            {pendingApproval && (
              <div className="card">
                <h3>Phê duyệt</h3>
                <p style={{ fontSize: 13, color: "var(--text-muted)", marginBottom: 12 }}>
                  Văn bản này đang chờ phê duyệt của bạn.
                </p>
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <button className="button" onClick={handleApprove} disabled={submitting}>
                    {submitting ? "Đang xử lý..." : "✅ Phê duyệt"}
                  </button>
                  <button className="button secondary" onClick={() => { setShowRejectInput(true); setActionError(null); }} disabled={submitting}>
                    🚫 Từ chối
                  </button>
                </div>
                {showRejectInput && (
                  <div style={{ marginTop: 14 }}>
                    <textarea
                      className="form-control"
                      placeholder="Nhập lý do từ chối..."
                      value={rejectReason}
                      onChange={(e) => setRejectReason(e.target.value)}
                      rows={3}
                      style={{ marginBottom: 10 }}
                    />
                    <div style={{ display: "flex", gap: 8 }}>
                      <button className="button danger" onClick={handleReject} disabled={submitting}>Xác nhận từ chối</button>
                      <button className="button secondary" onClick={() => { setShowRejectInput(false); setRejectReason(""); setActionError(null); }}>Hủy</button>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* RIGHT COLUMN */}
          <div style={{ display: "flex", flexDirection: "column", gap: 20 }}>
            {/* AI PANEL */}
            <div className="card">
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
                <h3 style={{ margin: 0 }}>🤖 Phân tích AI</h3>
                <button
                  className="btn-xs btn-xs--ghost"
                  onClick={() => {
                    setShowAiPanel((p) => !p);
                    if (!showAiPanel && doc) loadAiResults(doc.id);
                  }}
                >
                  {showAiPanel ? "Thu gọn" : "Mở rộng"}
                </button>
              </div>
              {showAiPanel && (
                <>
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 12 }}>
                    <button className="btn-xs btn-xs--primary" onClick={handleAiSummarize} disabled={aiLoading}>
                      {aiLoading ? "..." : "📝 Tóm tắt"}
                    </button>
                  </div>
                  {aiMsg && (
                    <div className="alert alert--info" style={{ marginBottom: 12, whiteSpace: "pre-line", fontSize: 13 }}>
                      {aiMsg}
                    </div>
                  )}
                  {aiResults.length > 0 && (
                    <div>
                      <p style={{ fontSize: 12, fontWeight: 600, color: "var(--text-muted)", marginBottom: 6 }}>
                        Lịch sử AI ({aiResults.length})
                      </p>
                      <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
                        {aiResults.map((r) => (
                          <div key={r.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", padding: "6px 8px", background: "var(--surface-subtle, #f9fafb)", borderRadius: 6, fontSize: 12 }}>
                            <div>
                              <span className="badge badge--ghost" style={{ marginRight: 6 }}>{r.loaiXuLyAI}</span>
                              <span style={{ color: "var(--text-muted)" }}>
                                {r.doTinCay != null && `${Math.round(r.doTinCay * 100)}%`}
                              </span>
                            </div>
                            <button className="btn-xs" onClick={() => handleDeleteAiResult(r.id)} style={{ color: "#ef4444", fontSize: 11 }}>
                              Xóa
                            </button>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                  {/* OCR Section */}
                  <div style={{ marginTop: 14, paddingTop: 12, borderTop: "1px solid var(--border)" }}>
                    <p style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>📷 OCR văn bản</p>
                    <p style={{ fontSize: 11, color: "var(--text-muted)", marginBottom: 8 }}>
                      Tóm tắt sẽ tự dùng nội dung OCR đã lưu; nếu chưa có, hệ thống sẽ thử đọc tệp PDF/DOCX/ảnh đính kèm đầu tiên. Bạn vẫn có thể chọn một tệp khác ở đây để OCR thủ công.
                    </p>
                    <input
                      type="file"
                      accept=".pdf,.docx,.png,.jpg,.jpeg,.tif,.tiff"
                      onChange={(e) => setOcrFile(e.target.files?.[0] || null)}
                      style={{ fontSize: 12, marginBottom: 8, display: "block" }}
                    />
                    {ocrFile && (
                      <button
                        className="btn-xs btn-xs--primary"
                        onClick={handleOcrProcess}
                        disabled={ocrProcessing}
                        style={{ marginBottom: 8 }}
                      >
                        {ocrProcessing ? "Đang xử lý..." : "Nhận dạng văn bản"}
                      </button>
                    )}
                    {ocrText && (
                      <textarea
                        readOnly
                        value={ocrText}
                        rows={4}
                        style={{ width: "100%", fontSize: 12, resize: "vertical", padding: 6 }}
                      />
                    )}
                  </div>
                </>
              )}
            </div>

            <div className="card">
              <h3>Tệp đính kèm</h3>
              {doc.trangThai !== 5 && isTemplateDocument && (
                <div className="alert alert--info" style={{ marginBottom: 12, display: "block", lineHeight: 1.6 }}>
                  Văn bản này được tạo từ template. Hãy <strong>tải file Word đã được điền dữ liệu xuống</strong>; nếu cần chỉnh sửa thêm trên máy, chọn lại file đã sửa ở đây để lưu cùng văn bản.
                  <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginTop: 10 }}>
                    <input
                      type="file"
                      accept=".doc,.docx,.pdf,.odt,.rtf"
                      onChange={(e) => setReplacementFile(e.target.files?.[0] ?? null)}
                      style={{ maxWidth: 310 }}
                    />
                    <button
                      className="button secondary"
                      type="button"
                      disabled={!replacementFile || uploadingReplacement}
                      onClick={handleUploadEditedFile}
                    >
                      {uploadingReplacement ? "Đang tải..." : "Tải bản đã chỉnh sửa"}
                    </button>
                  </div>
                </div>
              )}
              {attachments.length === 0 ? (
                <div className="empty-state" style={{ padding: "24px 0" }}>
                  <div className="empty-state__icon">📂</div>
                  <p>Không có tệp đính kèm.</p>
                </div>
              ) : (
                <table className="table">
                  <thead>
                    <tr>
                      <th>Tên tệp</th><th>Kích thước</th><th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {attachments.map((f) => (
                      <tr key={f.id}>
                        <td style={{ fontSize: 13 }}>📄 {f.tenTep}</td>
                        <td style={{ whiteSpace: "nowrap", fontSize: 13 }}>{formatSize(f.kichThuoc)}</td>
                        <td>
                          <button className="btn-xs btn-xs--primary" onClick={() => triggerDownload(f.id, f.tenTep)}>
                            Tải xuống
                          </button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            <div className="card">
              <h3>Lịch sử xử lý</h3>
              {timeline.length === 0 ? (
                <div className="empty-state" style={{ padding: "24px 0" }}>
                  <div className="empty-state__icon">📋</div>
                  <p>Chưa có dữ liệu xử lý.</p>
                </div>
              ) : (
                <div className="timeline">
                  {timeline.map((item, idx) => (
                    <div key={item.processingId || idx} className="timeline-item">
                      <div className={`timeline-dot ${item.hanhDongXuLy ? "timeline-dot--done" : "timeline-dot--active"}`}>
                        {item.hanhDongXuLy ? "✓" : "●"}
                      </div>
                      <div className="timeline-content">
                        <h4>{item.tenBuoc}</h4>
                        <p>{item.hanhDongXuLy || "Đang xử lý"}</p>
                        {item.yKienXuLy && (
                          <p style={{ fontStyle: "italic", marginTop: 2, fontSize: 12 }}>"{item.yKienXuLy}"</p>
                        )}
                        <p style={{ marginTop: 4 }}>
                          {item.ngayNhan ? formatDate(item.ngayNhan) : ""}
                          {item.nguoiXuLy && ` · ${item.nguoiXuLy}`}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {showSignModal && (
        <div className="modal-overlay" onClick={() => { setShowSignModal(false); setSignNote(""); setActionError(null); }}>
          <div className="modal-card" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>✍️ Xác nhận ký số</h3>
              <button className="modal-close" onClick={() => { setShowSignModal(false); setSignNote(""); }}>✕</button>
            </div>
            <div className="modal-body">
              <div className="alert alert--info">
                Chữ ký số sẽ được ghi nhận kèm theo hash SHA-256 của tệp đính kèm đầu tiên.
              </div>
              <div className="form-field">
                <label className="form-label">Ghi chú (tùy chọn)</label>
                <textarea className="form-control" placeholder="Nhập ghi chú..." rows={3}
                  value={signNote} onChange={(e) => setSignNote(e.target.value)} />
              </div>
              {actionError && <div className="alert alert--error">{actionError}</div>}
            </div>
            <div className="modal-footer">
              <button className="button secondary" onClick={() => { setShowSignModal(false); setSignNote(""); setActionError(null); }} disabled={submitting}>
                Hủy
              </button>
              <button className="button" onClick={handleSign} disabled={submitting}>
                {submitting ? "Đang ký..." : "Xác nhận ký số"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showPublishModal && (
        <div className="modal-overlay" onClick={() => setShowPublishModal(false)}>
          <div className="modal-card" style={{ maxWidth: 440 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>📣 Ban hành văn bản</h3>
              <button className="modal-close" onClick={() => setShowPublishModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              <div className="form-field">
                <label className="form-label">Ngày ban hành <span>*</span></label>
                <input type="date" className="form-control" value={publishDate} onChange={(e) => setPublishDate(e.target.value)} />
              </div>
              <div className="form-field">
                <label className="form-label">Ghi chú ban hành</label>
                <textarea className="form-control" rows={3} value={publishNote} onChange={(e) => setPublishNote(e.target.value)} />
              </div>
              {actionError && <div className="alert alert--error">{actionError}</div>}
            </div>
            <div className="modal-footer">
              <button className="button secondary" onClick={() => setShowPublishModal(false)} disabled={submitting}>Hủy</button>
              <button className="button" onClick={handlePublish} disabled={submitting || !publishDate}>
                {submitting ? "Đang ban hành..." : "Xác nhận ban hành"}
              </button>
            </div>
          </div>
        </div>
      )}

      {showSigInfoModal && (
        <div className="modal-overlay" onClick={() => setShowSigInfoModal(false)}>
          <div className="modal-card" style={{ maxWidth: 500 }} onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3>✍️ Thông tin chữ ký số</h3>
              <button className="modal-close" onClick={() => setShowSigInfoModal(false)}>✕</button>
            </div>
            <div className="modal-body">
              {signatureInfo ? (
                <table className="doc-meta-table">
                  <tbody>
                    <tr><td>Người ký</td><td>{signatureInfo.nguoiKyId ?? "-"}</td></tr>
                    <tr><td>Thời gian ký</td><td>{formatDateTime(signatureInfo.ngayKy)}</td></tr>
                    <tr><td>Loại ký</td><td>{signatureInfo.loaiKy || "-"}</td></tr>
                    <tr><td>Ghi chú</td><td>{signatureInfo.ghiChu || "-"}</td></tr>
                    <tr>
                      <td>Hash file</td>
                      <td style={{ wordBreak: "break-all", fontFamily: "monospace", fontSize: 12 }}>
                        {signatureInfo.hashFile || "Không có tệp đính kèm"}
                      </td>
                    </tr>
                    <tr><td>Chứng chỉ</td><td>{signatureInfo.certInfo || "-"}</td></tr>
                  </tbody>
                </table>
              ) : (
                <p style={{ color: "var(--text-muted)" }}>Không có thông tin chữ ký.</p>
              )}
            </div>
            <div className="modal-footer">
              <button className="button secondary" onClick={() => setShowSigInfoModal(false)}>Đóng</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
