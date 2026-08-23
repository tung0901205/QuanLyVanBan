import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { fetchIncomingDocuments } from "../../services/documents/documentsApi";
import { fetchDashboardStats } from "../../services/reports/reportsApi";
import { fetchPendingApprovals, type PendingApprovalItem } from "../../services/workflows/approvalsApi";
import { fetchOverdueDocuments } from "../../services/reports/reportsWorkflowApi";
import type { DashboardStats } from "../../services/reports/reportsApi";
import { getCurrentRoles, getStoredUser, isManager } from "../../services/auth/roleUtils";
import { Icon } from "../../shared/Icon";

const TRANG_THAI_MAP: Record<number, { label: string; className: string }> = {
  0: { label: "Nháp", className: "badge badge--ghost" },
  1: { label: "Đang xử lý", className: "badge badge--info" },
  2: { label: "Đã chuyển xử lý", className: "badge badge--warning" },
  3: { label: "Trình ký", className: "badge badge--primary" },
  4: { label: "Đã ký", className: "badge badge--success" },
  5: { label: "Đã ban hành", className: "badge badge--success" },
};

export default function Dashboard() {
  const managerView = isManager(getCurrentRoles(getStoredUser()));
  const [stats, setStats] = useState<DashboardStats | null>(null);
  const [recentDocs, setRecentDocs] = useState<Array<{
    id: number; soKyHieu: string; trichYeu: string; tenLoaiVanBan?: string;
    donViBanHanh: string; ngayTiepNhan: string; trangThai: number;
  }>>([]);
  const [pendingApprovals, setPendingApprovals] = useState<PendingApprovalItem[]>([]);
  const [overdueDocs, setOverdueDocs] = useState<Array<{
    documentId: number; soKyHieu: string; trichYeu: string; soNgayTre: number;
  }>>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadDashboard = async () => {
      const [dashboardResult, incomingResult, approvalsResult, overdueResult] = await Promise.allSettled([
        fetchDashboardStats(),
        fetchIncomingDocuments({ page: 0, size: 5 }),
        managerView ? fetchPendingApprovals({ page: 0, size: 5 }) : Promise.resolve({ content: [] } as { content: PendingApprovalItem[] }),
        managerView ? fetchOverdueDocuments({ page: 0, size: 5 }) : Promise.resolve({ content: [] } as { content: Array<{ documentId: number; soKyHieu: string; trichYeu: string; soNgayTre: number }> }),
      ]);

      const failures: string[] = [];
      if (dashboardResult.status === "fulfilled") setStats(dashboardResult.value);
      else failures.push("thống kê");

      if (incomingResult.status === "fulfilled") {
        setRecentDocs((incomingResult.value.content || []).map((doc) => ({
          id: doc.id,
          soKyHieu: doc.soKyHieu || "-",
          trichYeu: doc.trichYeu,
          tenLoaiVanBan: doc.tenLoaiVanBan,
          donViBanHanh: doc.donViBanHanh || "-",
          ngayTiepNhan: doc.ngayTiepNhan || "",
          trangThai: doc.trangThai ?? -1,
        })));
      } else failures.push("văn bản đến");

      if (approvalsResult.status === "fulfilled") setPendingApprovals(approvalsResult.value.content || []);
      else if (managerView) failures.push("phê duyệt");

      if (overdueResult.status === "fulfilled") setOverdueDocs(overdueResult.value.content || []);
      else if (managerView) failures.push("văn bản quá hạn");

      setError(failures.length ? `Không tải được: ${failures.join(", ")}` : null);
    };

    loadDashboard();
  }, [managerView]);

  return (
    <section>
      <div className="topbar">
        <div className="topbar__title">
          <h1>{managerView ? "Bảng điều hành Trưởng đơn vị" : "Bàn làm việc Chuyên viên"}</h1>
          <p>{managerView ? "Theo dõi phê duyệt, ký duyệt và tiến độ văn bản." : "Tiếp nhận, tạo và trình duyệt văn bản."}</p>
        </div>
        <div className="topbar__actions">
          <Link to={managerView ? "/approvals" : "/upload"} className="button">
            {managerView ? "Phê duyệt" : "Tải lên văn bản"}
          </Link>
          <Link to="/search" className="button secondary">
            Tìm kiếm
          </Link>
        </div>
      </div>

      {error && (
        <div className="card" style={{ marginBottom: 16, color: "#ef4444" }}>
          {error}
        </div>
      )}

      <div className="grid-4">
        <div className="stat-card-user stat-card-user--info">
          <div className="stat-card-user__label"><Icon name="inbox" />Văn bản đến</div>
          <div className="stat-card-user__value">{stats?.incomingDocuments ?? 0}</div>
          <span className="badge badge--info">Cần xử lý</span>
        </div>
        <div className="stat-card-user stat-card-user--warning">
          <div className="stat-card-user__label"><Icon name="workflow" />Đang xử lý</div>
          <div className="stat-card-user__value">{stats?.processingDocuments ?? 0}</div>
          <span className="badge badge--warning">Đang tiến hành</span>
        </div>
        <div className="stat-card-user stat-card-user--primary">
          <div className="stat-card-user__label"><Icon name={managerView ? "check" : "send"} />{managerView ? "Cần phê duyệt" : "Đã chuyển xử lý"}</div>
          <div className="stat-card-user__value">{managerView ? pendingApprovals.length : (stats?.processingDocuments ?? 0)}</div>
          <span className="badge badge--primary">{managerView ? "Chờ duyệt" : "Đã trình lãnh đạo"}</span>
        </div>
        <div className="stat-card-user stat-card-user--success">
          <div className="stat-card-user__label"><Icon name="check" />Đã hoàn tất</div>
          <div className="stat-card-user__value">{stats?.completedDocuments ?? 0}</div>
          <span className="badge badge--success">Hoàn thành</span>
        </div>
      </div>

      <div className="grid-2" style={{ marginTop: 24 }}>
        <div className="card">
          <h3>Văn bản đến gần đây</h3>
          {recentDocs.length === 0 ? (
            <p style={{ color: "var(--text-muted)", fontSize: 13 }}>Chưa có văn bản đến.</p>
          ) : (
            <table className="table" style={{ tableLayout: "fixed" }}>
              <colgroup>
                <col style={{ width: "22%" }} />
                <col style={{ width: "13%" }} />
                <col style={{ width: "33%" }} />
                <col style={{ width: "14%" }} />
                <col style={{ width: "18%" }} />
              </colgroup>
              <thead>
                <tr>
                  <th style={{ whiteSpace: "nowrap" }}>Mã</th>
                  <th style={{ whiteSpace: "nowrap" }}>Loại</th>
                  <th>Nội dung</th>
                  <th style={{ whiteSpace: "nowrap" }}>Ngày</th>
                  <th style={{ whiteSpace: "nowrap" }}>Trạng thái</th>
                </tr>
              </thead>
              <tbody>
                {recentDocs.map((doc) => {
                  const stt = TRANG_THAI_MAP[doc.trangThai] ?? { label: "Không xác định", className: "badge badge--ghost" };
                  return (
                    <tr key={doc.id}>
                      <td style={{ fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{doc.soKyHieu}</td>
                      <td style={{ fontSize: 13, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{doc.tenLoaiVanBan || "-"}</td>
                      <td>
                        <Link to={`/documents/${doc.id}`}>{doc.trichYeu}</Link>
                        <div style={{ fontSize: 12, color: "var(--text-muted)" }}>{doc.donViBanHanh}</div>
                      </td>
                      <td style={{ whiteSpace: "nowrap", fontSize: 13 }}>
                        {doc.ngayTiepNhan ? new Date(doc.ngayTiepNhan).toLocaleDateString("vi-VN") : "-"}
                      </td>
                      <td><span className={stt.className}>{stt.label}</span></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {managerView ? (
        <div className="card">
            <h3>Phê duyệt chờ xử lý</h3>
          {pendingApprovals.length === 0 ? (
            <p style={{ color: "var(--text-muted)", fontSize: 13 }}>Không có phê duyệt nào đang chờ.</p>
          ) : (
            <table className="table" style={{ tableLayout: "fixed" }}>
              <colgroup>
                <col style={{ width: "27%" }} />
                <col style={{ width: "41%" }} />
                <col style={{ width: "18%" }} />
                <col style={{ width: "14%" }} />
              </colgroup>
              <thead>
                <tr>
                  <th style={{ whiteSpace: "nowrap" }}>Mã</th>
                  <th>Nội dung</th>
                  <th style={{ whiteSpace: "nowrap" }}>Người gửi</th>
                  <th style={{ whiteSpace: "nowrap" }}>Hạn xử lý</th>
                </tr>
              </thead>
              <tbody>
                {pendingApprovals.map((item) => (
                  <tr key={item.processingId}>
                    <td style={{ fontWeight: 600, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{item.soKyHieu || "-"}</td>
                    <td>
                      <Link to={`/documents/${item.documentId}`}>{item.trichYeu}</Link>
                    </td>
                    <td style={{ fontSize: 13 }}>{item.nguoiGuiTen || `#${item.nguoiGuiId}`}</td>
                    <td style={{ whiteSpace: "nowrap", fontSize: 13 }}>
                      {item.hanXuLy ? new Date(item.hanXuLy).toLocaleDateString("vi-VN") : "-"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <hr style={{ margin: "16px 0", border: "none", borderTop: "1px solid var(--border)" }} />

          <h3>Văn bản quá hạn</h3>
          {overdueDocs.length === 0 ? (
            <p style={{ color: "var(--text-muted)", fontSize: 13 }}>Không có văn bản quá hạn.</p>
          ) : (
            <table className="table" style={{ width: "100%" }}>
              <thead>
                <tr>
                  <th style={{ whiteSpace: "nowrap" }}>Mã</th>
                  <th>Nội dung</th>
                  <th style={{ whiteSpace: "nowrap" }}>Số ngày trễ</th>
                </tr>
              </thead>
              <tbody>
                {overdueDocs.map((item) => (
                  <tr key={item.documentId}>
                    <td style={{ fontWeight: 600, whiteSpace: "nowrap" }}>{item.soKyHieu}</td>
                    <td>
                      <Link to={`/documents/${item.documentId}`}>{item.trichYeu}</Link>
                    </td>
                    <td style={{ whiteSpace: "nowrap", fontSize: 13, color: "#ef4444" }}>
                      {item.soNgayTre} ngày
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        ) : (
          <div className="card">
            <h3>Quy trình xử lý của Chuyên viên</h3>
            <div style={{ display: "grid", gap: 12, marginTop: 12 }}>
              <div className="modal-doc-ref"><strong>1. Tiếp nhận</strong><br />Mở mục Tải lên, khai báo thông tin và đính kèm tệp.</div>
              <div className="modal-doc-ref"><strong>2. Trình duyệt</strong><br />Chọn Trưởng đơn vị rồi gửi văn bản vào luồng.</div>
              <div className="modal-doc-ref"><strong>3. Theo dõi</strong><br />Mở Văn bản đến hoặc Thông báo để xem kết quả phê duyệt.</div>
            </div>
            <div style={{ display: "flex", gap: 10, marginTop: 18, flexWrap: "wrap" }}>
              <Link className="button" to="/upload">Tải lên văn bản</Link>
              <Link className="button secondary" to="/inbox">Văn bản đến</Link>
              <Link className="button secondary" to="/outgoing">Văn bản đi</Link>
            </div>
          </div>
        )}
      </div>
    </section>
  );
}
