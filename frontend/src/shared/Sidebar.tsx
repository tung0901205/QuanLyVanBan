import { useEffect, useMemo, useState } from "react";
import { NavLink } from "react-router-dom";
import { Icon, type IconName } from "./Icon";
import { getCurrentUser, type AuthUser } from "../services/auth/authApi";
import { hasBusinessPermission, BUSINESS_PERMISSION_CODES } from "../services/auth/permissionUtils";
import { fetchNotifications } from "../services/notifications/notificationsApi";
import { fetchDelegations } from "../services/workflows/delegationApi";
import {
  getCurrentRoles,
  getStoredUser,
  hasAnyRole,
  ROLE_ADMIN,
  ROLE_MANAGER,
  ROLE_STAFF,
  roleLabel,
} from "../services/auth/roleUtils";

type NavItem = {
  to: string;
  label: string;
  icon: IconName;
  permissionCode?: string;
  permissionCodes?: string[];
  alwaysVisible?: boolean;
  delegationAlsoGrants?: boolean;
};

// Chỉ ánh xạ các màn hình NGHIỆP VỤ. Các mã quyền quản trị như
// MANAGE_USERS / MANAGE_UNITS / MANAGE_PERMISSIONS / VIEW_AUDIT_LOGS
// cố ý không có ở đây nên sẽ không bao giờ bị kéo sang menu Chuyên viên/Lãnh đạo.
const navItems: NavItem[] = [
  { to: "/dashboard", label: "Tổng quan", icon: "home", permissionCode: BUSINESS_PERMISSION_CODES.DASHBOARD },
  {
    to: "/inbox",
    label: "Văn bản đến",
    icon: "inbox",
    // Quyền chuyển xử lý không có màn hình độc lập; khi Admin cấp riêng quyền này
    // người dùng vẫn cần nhìn thấy danh sách Văn bản đến để thực hiện thao tác.
    permissionCodes: [BUSINESS_PERMISSION_CODES.DOCUMENTS, BUSINESS_PERMISSION_CODES.TRANSFER],
  },
  { to: "/outgoing", label: "Văn bản đi", icon: "send", permissionCode: BUSINESS_PERMISSION_CODES.DOCUMENTS },
  { to: "/approvals", label: "Phê duyệt", icon: "check", permissionCode: BUSINESS_PERMISSION_CODES.APPROVALS, delegationAlsoGrants: true },
  { to: "/workflows", label: "Quy trình xử lý", icon: "workflow", permissionCode: BUSINESS_PERMISSION_CODES.WORKFLOWS },
  { to: "/templates", label: "Template văn bản", icon: "template", permissionCode: BUSINESS_PERMISSION_CODES.TEMPLATES },
  { to: "/reports", label: "Báo cáo & Thống kê", icon: "report", permissionCode: BUSINESS_PERMISSION_CODES.REPORTS },
  { to: "/case-files", label: "Hồ sơ công việc", icon: "folder", permissionCode: BUSINESS_PERMISSION_CODES.CASE_FILES },
  { to: "/internal-documents", label: "Văn bản nội bộ", icon: "building", permissionCode: BUSINESS_PERMISSION_CODES.DOCUMENTS },
  { to: "/delegation", label: "Ủy quyền xử lý", icon: "people", permissionCode: BUSINESS_PERMISSION_CODES.DELEGATION },
  { to: "/upload", label: "Tải lên", icon: "upload", permissionCode: BUSINESS_PERMISSION_CODES.UPLOAD },
  { to: "/search", label: "Tìm kiếm", icon: "search", permissionCode: BUSINESS_PERMISSION_CODES.DOCUMENTS },
  { to: "/profile", label: "Tài khoản", icon: "person", alwaysVisible: true },
];

export default function Sidebar() {
  const [currentUser, setCurrentUser] = useState<AuthUser | null>(() => getStoredUser());
  const [unreadCount, setUnreadCount] = useState(0);
  const [hasActiveIncomingDelegation, setHasActiveIncomingDelegation] = useState(false);
  const [collapsed, setCollapsed] = useState(false);

  const roles = useMemo(() => getCurrentRoles(currentUser), [currentUser]);
  const isBusinessUser = hasAnyRole(roles, [ROLE_STAFF, ROLE_MANAGER]);

  const visibleItems = useMemo(
    () => navItems.filter((item) => {
      if (!isBusinessUser) return false;
      if (item.alwaysVisible) return true;
      if (item.delegationAlsoGrants && hasActiveIncomingDelegation) return true;
      if (item.permissionCodes?.length) {
        return item.permissionCodes.some((code) => hasBusinessPermission(currentUser, code));
      }
      return item.permissionCode ? hasBusinessPermission(currentUser, item.permissionCode) : false;
    }),
    [currentUser, hasActiveIncomingDelegation, isBusinessUser]
  );

  useEffect(() => {
    getCurrentUser()
      .then((u) => {
        setCurrentUser(u);
        sessionStorage.setItem("user", JSON.stringify(u));
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    if (!currentUser?.id || !isBusinessUser) return;
    fetchDelegations({ nguoiDuocUyQuyenId: currentUser.id, active: true, page: 0, size: 1 })
      .then((res) => setHasActiveIncomingDelegation((res.totalElements ?? res.content?.length ?? 0) > 0))
      .catch(() => setHasActiveIncomingDelegation(false));
  }, [currentUser?.id, isBusinessUser]);

  useEffect(() => {
    if (!currentUser?.id) return;
    fetchNotifications({ nguoiNhanId: currentUser.id, daDoc: false, page: 0, size: 1 })
      .then((res) => setUnreadCount(res.totalElements ?? 0))
      .catch(() => {});
  }, [currentUser?.id]);

  return (
    <aside className={`sidebar${collapsed ? " sidebar--collapsed" : ""}`}>
      <div className="sidebar__brand">
        <div className="sidebar__brand-icon"><Icon name="archive" size={20} /></div>
        {!collapsed && (
          <div className="sidebar__brand-text">
            <h2>eOIS</h2>
            <span>Hệ thống xử lý văn bản</span>
          </div>
        )}
        <button
          className="sidebar__toggle"
          onClick={() => setCollapsed((c) => !c)}
          title={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
        >
          <Icon name={collapsed ? "chevron-right" : "chevron-left"} size={16} />
        </button>
      </div>

      {!collapsed && currentUser && (
        <div style={{ padding: "0 12px 14px" }}>
          <div style={{ fontSize: 13, fontWeight: 700, color: "var(--text-strong)" }}>
            {currentUser.hoTen}
          </div>
          <span className={hasAnyRole(roles, [ROLE_MANAGER]) ? "badge badge--primary" : "badge badge--info"}>
            {roleLabel(roles)}
          </span>
        </div>
      )}

      <nav className="sidebar__nav">
        {!collapsed && <span className="sidebar__nav-section">Nghiệp vụ</span>}

        {visibleItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            title={collapsed ? item.label : undefined}
            className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}
          >
            <span className="nav-link__icon"><Icon name={item.icon} /></span>
            <span className="nav-link__label">{item.label}</span>
          </NavLink>
        ))}

        {isBusinessUser && (
          <NavLink
            to="/notifications"
            title={collapsed ? "Thông báo" : undefined}
            className={({ isActive }) => isActive ? "nav-link active" : "nav-link"}
          >
            <span className="nav-link__icon"><Icon name="bell" /></span>
            <span className="nav-link__label">Thông báo</span>
            {unreadCount > 0 && (
              <span className="nav-link__badge">
                {unreadCount > 99 ? "99+" : unreadCount}
              </span>
            )}
          </NavLink>
        )}

        {hasAnyRole(roles, [ROLE_ADMIN]) && (
          <>
            <div className="nav-divider" />
            {!collapsed && <span className="sidebar__nav-section">Quản trị</span>}
            <NavLink
              to="/admin/dashboard"
              title={collapsed ? "Quản trị hệ thống" : undefined}
              className={({ isActive }) =>
                isActive ? "nav-link active admin-link" : "nav-link admin-link"
              }
            >
              <span className="nav-link__icon"><Icon name="settings" /></span>
              <span className="nav-link__label">Quản trị hệ thống</span>
            </NavLink>
          </>
        )}
      </nav>
    </aside>
  );
}
