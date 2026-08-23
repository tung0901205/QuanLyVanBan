import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../../services/core/apiClient";
import { fetchWorkflows, type WorkflowItem } from "../../services/workflows/workflowsApi";
import { fetchSlaList, updateStepSla, type SlaItem } from "../../services/sla/slaApi";
import { fetchWorkflowProgress, type WorkflowProgressTask } from "../../services/reports/reportsWorkflowApi";

const DON_VI_OPTIONS = ["PHUT", "GIO", "NGAY"];
const DON_VI_LABEL: Record<string, string> = { PHUT: "Phút", GIO: "Giờ", NGAY: "Ngày" };

type EditState = { thoiGianXuLy: string; donViThoiGian: string; ghiChu: string };
type DeadlineRow = WorkflowProgressTask & { documentId: number; deadline: Date; deltaMs: number };

function uniqueCurrentTasks(items: WorkflowProgressTask[]): WorkflowProgressTask[] {
  const active = items.filter((item) => item.documentId && item.trangThaiXuLy === 1 && item.hanXuLy);
  const byDocument = new Map<number, WorkflowProgressTask>();
  for (const item of active) {
    const id = Number(item.documentId);
    const previous = byDocument.get(id);
    if (!previous || new Date(item.hanXuLy!).getTime() < new Date(previous.hanXuLy!).getTime()) {
      byDocument.set(id, item);
    }
  }
  return Array.from(byDocument.values());
}

function toDeadlineRows(items: WorkflowProgressTask[], mode: "overdue" | "warning"): DeadlineRow[] {
  const now = Date.now();
  const warningEnd = now + 2 * 24 * 60 * 60 * 1000;
  return uniqueCurrentTasks(items)
    .map((item) => {
      const deadline = new Date(item.hanXuLy!);
      return { ...item, documentId: Number(item.documentId), deadline, deltaMs: deadline.getTime() - now };
    })
    .filter((item) => Number.isFinite(item.deadline.getTime()))
    .filter((item) => mode === "overdue" ? item.deltaMs < 0 : item.deltaMs >= 0 && item.deadline.getTime() <= warningEnd)
    .sort((a, b) => a.deadline.getTime() - b.deadline.getTime());
}

function formatDuration(ms: number, overdue: boolean) {
  const absolute = Math.abs(ms);
  const hours = Math.max(1, Math.ceil(absolute / 3_600_000));
  if (hours < 24) return overdue ? `Trễ ${hours} giờ` : `Còn ${hours} giờ`;
  const days = Math.ceil(hours / 24);
  return overdue ? `Trễ ${days} ngày` : `Còn ${days} ngày`;
}

export default function SlaManagement() {
  const [workflows, setWorkflows] = useState<WorkflowItem[]>([]);
  const [selectedWorkflowId, setSelectedWorkflowId] = useState<number | null>(null);
  const [slaItems, setSlaItems] = useState<SlaItem[]>([]);
  const [loadingWf, setLoadingWf] = useState(true);
  const [loadingSla, setLoadingSla] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [editRow, setEditRow] = useState<number | null>(null);
  const [editState, setEditState] = useState<EditState>({ thoiGianXuLy: "", donViThoiGian: "NGAY", ghiChu: "" });
  const [saving, setSaving] = useState(false);
  const [saveResult, setSaveResult] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"config" | "violations" | "warning">("config");
  const [progressItems, setProgressItems] = useState<WorkflowProgressTask[]>([]);
  const [loadingProgress, setLoadingProgress] = useState(false);
  const [progressError, setProgressError] = useState<string | null>(null);

  useEffect(() => {
    fetchWorkflows({ page: 0, size: 100, suDung: true })
      .then((res) => setWorkflows(res.content || []))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không thể tải quy trình"))
      .finally(() => setLoadingWf(false));
  }, []);

  const overdueRows = useMemo(() => toDeadlineRows(progressItems, "overdue"), [progressItems]);
  const warningRows = useMemo(() => toDeadlineRows(progressItems, "warning"), [progressItems]);

  const loadProgress = () => {
    setLoadingProgress(true);
    setProgressError(null);
    fetchWorkflowProgress()
      .then((res) => setProgressItems(res.items || []))
      .catch((err) => {
        setProgressItems([]);
        setProgressError(err instanceof ApiError ? err.message : "Không thể tải dữ liệu tiến độ xử lý");
      })
      .finally(() => setLoadingProgress(false));
  };

  const loadSla = (workflowId: number) => {
    setLoadingSla(true); setError(null); setEditRow(null); setSaveResult(null);
    fetchSlaList(workflowId)
      .then((items) => setSlaItems(Array.isArray(items) ? items : []))
      .catch((err) => setError(err instanceof ApiError ? err.message : "Không thể tải cấu hình SLA"))
      .finally(() => setLoadingSla(false));
  };

  const handleSelectWorkflow = (id: number) => { setSelectedWorkflowId(id); loadSla(id); };
  const openEdit = (item: SlaItem) => {
    setEditRow(item.stepId);
    setEditState({ thoiGianXuLy: String(item.thoiGianXuLy ?? ""), donViThoiGian: item.donViThoiGian ?? "NGAY", ghiChu: "" });
    setSaveResult(null);
  };

  const handleSave = async (item: SlaItem) => {
    const value = parseInt(editState.thoiGianXuLy, 10);
    if (!Number.isFinite(value) || value <= 0) { setSaveResult("Thời gian xử lý phải là số dương"); return; }
    setSaving(true); setSaveResult(null);
    try {
      await updateStepSla(item.workflowId, item.stepId, {
        thoiGianXuLy: value,
        donViThoiGian: editState.donViThoiGian,
        ghiChu: editState.ghiChu.trim() || undefined,
      });
      setSlaItems((prev) => prev.map((s) => s.stepId === item.stepId ? { ...s, thoiGianXuLy: value, donViThoiGian: editState.donViThoiGian } : s));
      setEditRow(null);
    } catch (err) {
      setSaveResult(err instanceof ApiError ? err.message : "Lưu thất bại");
    } finally { setSaving(false); }
  };

  const openProgressTab = (tab: "violations" | "warning") => {
    setActiveTab(tab);
    loadProgress();
  };

  const renderDeadlineTable = (rows: DeadlineRow[], overdue: boolean) => (
    <div className="card">
      <div style={{ padding: "16px 18px 6px" }}>
        <strong>{overdue ? "Văn bản đang xử lý nhưng đã quá hạn" : "Văn bản đang xử lý sẽ hết hạn trong 48 giờ"}</strong>
        <p style={{ margin: "6px 0 0", color: "var(--text-muted)", fontSize: 13 }}>
          Dữ liệu được tính trực tiếp từ hạn xử lý của bước workflow đang hoạt động; mỗi văn bản chỉ hiển thị bước hiện tại gần nhất.
        </p>
      </div>
      {progressError && <div className="admin-error" style={{ margin: 16 }}>⚠️ {progressError}</div>}
      {loadingProgress && <p style={{ padding: 20, textAlign: "center", color: "var(--text-muted)" }}>Đang tải tiến độ...</p>}
      {!loadingProgress && !progressError && rows.length === 0 && (
        <div className="empty-state" style={{ padding: "30px 0" }}>
          <div className="empty-state__icon">✅</div>
          <h3>{overdue ? "Không có văn bản vi phạm SLA" : "Không có văn bản sắp hết hạn"}</h3>
          <p>{overdue ? "Các bước đang xử lý hiện vẫn nằm trong hạn." : "Không có bước xử lý nào hết hạn trong 48 giờ tới."}</p>
        </div>
      )}
      {!loadingProgress && rows.length > 0 && (
        <div style={{ overflowX: "auto" }}>
          <table className="table">
            <thead><tr><th>Văn bản</th><th>Người xử lý</th><th>Hạn xử lý</th><th>Tình trạng</th><th>Tiến độ</th><th></th></tr></thead>
            <tbody>
              {rows.map((item) => (
                <tr key={item.documentId}>
                  <td>
                    <div style={{ fontWeight: 650 }}>{item.soKyHieu || `#${item.documentId}`}</div>
                    <div style={{ fontSize: 12, color: "var(--text-muted)", maxWidth: 360 }}>{item.trichYeu || "Chưa có trích yếu"}</div>
                  </td>
                  <td>{item.nguoiXuLy || (item.nguoiXuLyId ? `#${item.nguoiXuLyId}` : "-")}</td>
                  <td style={{ whiteSpace: "nowrap" }}>{item.deadline.toLocaleString("vi-VN")}</td>
                  <td><span className={overdue ? "badge badge--danger" : "badge badge--warning"}>{formatDuration(item.deltaMs, overdue)}</span></td>
                  <td>{item.tyLeHoanThanh ?? 0}%</td>
                  <td><Link className="btn-xs btn-xs--ghost" to={`/documents/${item.documentId}`}>Mở văn bản</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <div style={{ padding: "12px 16px", display: "flex", justifyContent: "flex-end" }}>
        <button className="button secondary" type="button" onClick={loadProgress}>🔄 Làm mới</button>
      </div>
    </div>
  );

  return (
    <section>
      <div className="topbar">
        <div className="topbar__title">
          <h1>Quản lý SLA</h1>
          <p>Cấu hình thời hạn và theo dõi vi phạm theo bước xử lý thực tế.</p>
        </div>
      </div>

      <div className="filter-bar" style={{ marginBottom: 16 }}>
        <div className="filter-tabs">
          <button className={`filter-tab${activeTab === "config" ? " active" : ""}`} onClick={() => setActiveTab("config")}>Cấu hình SLA</button>
          <button className={`filter-tab${activeTab === "violations" ? " active" : ""}`} onClick={() => openProgressTab("violations")}>Vi phạm SLA {overdueRows.length > 0 && `(${overdueRows.length})`}</button>
          <button className={`filter-tab${activeTab === "warning" ? " active" : ""}`} onClick={() => openProgressTab("warning")}>Sắp hết hạn {warningRows.length > 0 && `(${warningRows.length})`}</button>
        </div>
      </div>

      {activeTab === "violations" && renderDeadlineTable(overdueRows, true)}
      {activeTab === "warning" && renderDeadlineTable(warningRows, false)}

      {activeTab === "config" && (
        <>
          <div className="card" style={{ marginBottom: 16, padding: 16 }}>
            <label style={{ display: "flex", flexDirection: "column", gap: 6, fontSize: 14, maxWidth: 440 }}>
              <strong>Chọn quy trình</strong>
              {loadingWf ? <span style={{ color: "var(--text-muted)" }}>Đang tải quy trình...</span> : (
                <select value={selectedWorkflowId ?? ""} onChange={(e) => { const id = Number(e.target.value); if (id) handleSelectWorkflow(id); }}>
                  <option value="">-- Chọn quy trình --</option>
                  {workflows.map((wf) => <option key={wf.id} value={wf.id}>{wf.tenQuyTrinh} ({wf.maQuyTrinh})</option>)}
                </select>
              )}
            </label>
          </div>

          {error && <div className="admin-error">⚠️ {error}</div>}
          {selectedWorkflowId && (
            <div className="card">
              {loadingSla && <p style={{ padding: 18, textAlign: "center", color: "var(--text-muted)" }}>Đang tải...</p>}
              {!loadingSla && slaItems.length === 0 && <p style={{ padding: 20, textAlign: "center", color: "var(--text-muted)" }}>Quy trình chưa có bước để cấu hình SLA.</p>}
              {!loadingSla && slaItems.length > 0 && (
                <table className="table">
                  <thead><tr><th>Tên bước</th><th>Thời gian xử lý</th><th>Đơn vị</th><th>Ghi chú</th><th></th></tr></thead>
                  <tbody>
                    {slaItems.map((item) => {
                      const editing = editRow === item.stepId;
                      return (
                        <tr key={item.stepId}>
                          <td style={{ fontWeight: 600 }}>{item.tenBuoc}</td>
                          <td>{editing ? <input type="number" min={1} value={editState.thoiGianXuLy} onChange={(e) => setEditState({ ...editState, thoiGianXuLy: e.target.value })} style={{ width: 90 }} /> : (item.thoiGianXuLy ?? "-")}</td>
                          <td>{editing ? (
                            <select value={editState.donViThoiGian} onChange={(e) => setEditState({ ...editState, donViThoiGian: e.target.value })}>
                              {DON_VI_OPTIONS.map((unit) => <option key={unit} value={unit}>{DON_VI_LABEL[unit]}</option>)}
                            </select>
                          ) : DON_VI_LABEL[item.donViThoiGian || "NGAY"]}</td>
                          <td>{editing ? <input value={editState.ghiChu} onChange={(e) => setEditState({ ...editState, ghiChu: e.target.value })} placeholder="Ghi chú thay đổi..." /> : "-"}</td>
                          <td style={{ whiteSpace: "nowrap" }}>
                            {editing ? (
                              <><button className="btn-xs btn-xs--primary" disabled={saving} onClick={() => handleSave(item)}>{saving ? "..." : "Lưu"}</button><button className="btn-xs btn-xs--ghost" style={{ marginLeft: 6 }} disabled={saving} onClick={() => { setEditRow(null); setSaveResult(null); }}>Hủy</button></>
                            ) : <button className="btn-xs btn-xs--ghost" onClick={() => openEdit(item)}>Chỉnh sửa</button>}
                            {editing && saveResult && <div style={{ color: "var(--danger)", fontSize: 11, marginTop: 5 }}>{saveResult}</div>}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
          )}
          {!selectedWorkflowId && !loadingWf && <div className="card"><p style={{ padding: 24, textAlign: "center", color: "var(--text-muted)" }}>Chọn một quy trình để xem và chỉnh sửa thời hạn xử lý của từng bước.</p></div>}
        </>
      )}
    </section>
  );
}
