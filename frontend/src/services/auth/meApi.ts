import { apiPut } from "../core/apiClient";
import type { AuthUser } from "./authApi";

export const updateMyProfile = (payload: {
  hoTen: string;
  email: string;
}) => apiPut<AuthUser>("/api/auth/me", payload);
