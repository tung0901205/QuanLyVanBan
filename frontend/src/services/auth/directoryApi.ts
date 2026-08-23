import { apiGet } from "../core/apiClient";

export type DirectoryUser = {
  id: number;
  username: string;
  hoTen: string;
  email: string;
  donViId?: number | null;
  tenDonVi?: string | null;
  chucVu?: string | null;
  maNhomQuyen?: string | null;
  tenNhomQuyen?: string | null;
};

export const fetchDirectoryUsers = (params?: {
  role?: string;
  donViId?: number;
  keyword?: string;
}) => apiGet<DirectoryUser[]>("/api/auth/directory/users", { params });
