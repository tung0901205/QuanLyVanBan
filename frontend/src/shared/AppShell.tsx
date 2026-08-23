import { Navigate, Outlet, useLocation } from "react-router-dom";
import { getAccessToken } from "../services/core/apiClient";
import Sidebar from "./Sidebar";

export default function AppShell() {
  const location = useLocation();
  if (!getAccessToken()) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return (
    <div className="app-shell">
      <Sidebar />
      <main className="main-content">
        <Outlet />
      </main>
    </div>
  );
}
