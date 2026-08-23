import type { AuthUser, UserPermission } from "./authApi";

export const BUSINESS_PERMISSION_CODES = {
  DASHBOARD: "VIEW_DASHBOARD",
  DOCUMENTS: "MANAGE_DOCUMENTS",
  APPROVALS: "APPROVE_DOCUMENT",
  WORKFLOWS: "MANAGE_WORKFLOWS",
  TEMPLATES: "MANAGE_TEMPLATES",
  REPORTS: "VIEW_REPORTS",
  UPLOAD: "UPLOAD_DOCUMENTS",
  TRANSFER: "TRANSFER_DOCUMENTS",
  CASE_FILES: "MANAGE_CASE_FILES",
  DELEGATION: "DELEGATION",
  AI: "USE_AI",
} as const;

export function getPermission(user: AuthUser | null | undefined, code: string): UserPermission | undefined {
  return user?.permissions?.find((item) => item.maChucNang?.toUpperCase() === code.toUpperCase());
}

export function hasAnyPermissionFlag(permission?: UserPermission): boolean {
  if (!permission) return false;
  return Boolean(permission.isView || permission.isCreate || permission.isEdit || permission.isDelete || permission.isApprove);
}

export function hasBusinessPermission(user: AuthUser | null | undefined, code: string): boolean {
  return hasAnyPermissionFlag(getPermission(user, code));
}
