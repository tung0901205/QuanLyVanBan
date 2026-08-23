import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { getAccessToken } from "../services/core/apiClient";
import { defaultRouteForRoles, getCurrentRoles, getStoredUser, hasAnyRole } from "../services/auth/roleUtils";

type Props = {
  allowed: string[];
  children: ReactNode;
};

export default function RequireRole({ allowed, children }: Props) {
  const location = useLocation();
  const token = getAccessToken();
  if (!token) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  const roles = getCurrentRoles(getStoredUser());
  if (!hasAnyRole(roles, allowed)) {
    return <Navigate to={defaultRouteForRoles(roles)} replace />;
  }

  return <>{children}</>;
}
