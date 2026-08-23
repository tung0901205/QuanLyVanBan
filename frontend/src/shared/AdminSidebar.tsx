import { useState } from "react";
import { NavLink } from "react-router-dom";
import { Icon, type IconName } from "./Icon";

const adminNavItems: Array<{to: string; label: string; icon: IconName}> = [
  { to: "/admin/dashboard", label: "Tổng quan hệ thống", icon: "dashboard" },
  { to: "/admin/users", label: "Quản lý người dùng", icon: "people" },
  { to: "/admin/permissions", label: "Phân quyền", icon: "shield" },
  { to: "/admin/units", label: "Quản lý đơn vị", icon: "building" },
  { to: "/admin/document-types", label: "Loại văn bản", icon: "file" },
  { to: "/admin/sla", label: "Quản lý SLA", icon: "clock" },
  { to: "/admin/audit-logs", label: "Nhật ký hệ thống", icon: "audit" },
];

export default function AdminSidebar() {
  const [collapsed, setCollapsed] = useState(false);

  return (
    <aside className={`admin-sidebar${collapsed ? " admin-sidebar--collapsed" : ""}`}>
      <div className="admin-sidebar__brand">
        <div className="admin-sidebar__brand-icon"><Icon name="settings" size={20} /></div>
        {!collapsed && (
          <div className="admin-sidebar__brand-text">
            <h2>Admin Panel</h2>
            <span>Quản trị hệ thống</span>
          </div>
        )}
        <button
          className="admin-sidebar__toggle"
          onClick={() => setCollapsed((c) => !c)}
          title={collapsed ? "Mở rộng menu" : "Thu gọn menu"}
        >
          <Icon name={collapsed ? "chevron-right" : "chevron-left"} size={16} />
        </button>
      </div>

      <nav className="admin-sidebar__nav">
        {!collapsed && <span className="admin-nav-section">Quản trị</span>}
        {adminNavItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            title={collapsed ? item.label : undefined}
            className={({ isActive }) => isActive ? "admin-nav-link active" : "admin-nav-link"}
          >
            <span className="icon"><Icon name={item.icon} /></span>
            <span>{item.label}</span>
          </NavLink>
        ))}
      </nav>

      {!collapsed && (
        <div className="admin-sidebar__footer">
          <p>eOIS Admin v1.0</p>
        </div>
      )}
    </aside>
  );
}
