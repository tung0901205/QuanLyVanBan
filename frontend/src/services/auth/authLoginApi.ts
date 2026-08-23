import { apiPost } from "../core/apiClient";

export type LoginUser = {
  id: number;
  username: string;
  hoTen: string;
  email: string;
  roles: string[];
};

export type LoginResponse = {
  accessToken: string;
  refreshToken?: string;
  tokenType?: string;
  expiresIn?: number;
  user: LoginUser;
};

export const login = (payload: {
  username: string;
  password: string;
}) =>
  apiPost<LoginResponse>("/api/auth/login", payload, {
    auth: false,
  });
