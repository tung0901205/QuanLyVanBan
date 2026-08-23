import { getAccessToken } from "../core/apiClient";
import type { AuthUser } from "./authApi";

export const ROLE_ADMIN = "ADMIN";
export const ROLE_MANAGER = "LANH_DAO";
export const ROLE_STAFF = "CHUYEN_VIEN";

export function normalizeRole(role?: string | null): string {
  if (!role) return "";
  const normalized = role.trim().toUpperCase().replace(/^ROLE_/, "");
  if (["MANAGER", "LEADER", "TRUONG_DON_VI", "TRUONG_PHONG", "GIAM_DOC", "LANHDAO"].includes(normalized)) {
    return ROLE_MANAGER;
  }
  if (["STAFF", "SPECIALIST", "CHUYENVIEN"].includes(normalized)) {
    return ROLE_STAFF;
  }
  return normalized;
}

export function normalizeRoles(roles?: Array<string | null> | null): string[] {
  return Array.from(new Set((roles ?? []).map(normalizeRole).filter(Boolean)));
}

function decodeTokenRoles(): string[] {
  const token = getAccessToken();
  if (!token) return [];
  try {
    const payload = token.split(".")[1];
    const normalized = payload.replace(/-/g, "+").replace(/_/g, "/");
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "=");
    const decoded = JSON.parse(atob(padded)) as { roles?: string[]; authorities?: string[] };
    return normalizeRoles([...(decoded.roles ?? []), ...(decoded.authorities ?? [])]);
  } catch {
    return [];
  }
}

export function getStoredUser(): AuthUser | null {
  try {
    const raw = sessionStorage.getItem("user");
    return raw ? (JSON.parse(raw) as AuthUser) : null;
  } catch {
    return null;
  }
}

export function getCurrentRoles(user?: AuthUser | null): string[] {
  return normalizeRoles([...(user?.roles ?? []), ...decodeTokenRoles()]);
}

export function hasAnyRole(roles: string[], allowed: string[]): boolean {
  const normalized = normalizeRoles(roles);
  return allowed.map(normalizeRole).some((role) => normalized.includes(role));
}

export function isAdmin(roles: string[]): boolean {
  return hasAnyRole(roles, [ROLE_ADMIN]);
}

export function isManager(roles: string[]): boolean {
  return hasAnyRole(roles, [ROLE_MANAGER, ROLE_ADMIN]);
}

export function isStaff(roles: string[]): boolean {
  return hasAnyRole(roles, [ROLE_STAFF, ROLE_ADMIN]);
}

export function roleLabel(roles: string[]): string {
  if (hasAnyRole(roles, [ROLE_ADMIN])) return "Quản trị viên";
  if (hasAnyRole(roles, [ROLE_MANAGER])) return "Trưởng đơn vị";
  if (hasAnyRole(roles, [ROLE_STAFF])) return "Chuyên viên";
  return "Người dùng";
}

export function defaultRouteForRoles(roles: string[]): string {
  return isAdmin(roles) ? "/admin/dashboard" : "/dashboard";
}
