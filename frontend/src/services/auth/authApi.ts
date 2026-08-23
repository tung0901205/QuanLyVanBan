import { apiGet, apiPost } from "../core/apiClient";

export type UserPermission = {
  maChucNang: string;
  isView: boolean;
  isCreate: boolean;
  isEdit: boolean;
  isDelete: boolean;
  isApprove: boolean;
};

export type AuthUser = {
  id: number;
  username: string;
  hoTen: string;
  email: string;
  donViId?: number;
  nhomQuyenId?: number;
  roles?: string[];
  permissions?: UserPermission[];
};

export type AuthTokens = {
  accessToken: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
};

export const getCurrentUser = () =>
  apiGet<AuthUser>("/api/auth/me");

export const refreshToken = (refreshTokenValue: string) =>
  apiPost<AuthTokens>("/api/auth/refresh-token", {
    refreshToken: refreshTokenValue,
  });

export const loginAzure = (authorizationCode: string, redirectUri: string, codeVerifier?: string) =>
  apiPost<AuthTokens & { user: AuthUser }>("/api/auth/login/azure", {
    authorizationCode,
    redirectUri,
    code_verifier: codeVerifier || "",
  }, { auth: false });
