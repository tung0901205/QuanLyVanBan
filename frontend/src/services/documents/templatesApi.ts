import { apiDelete, apiGet, apiPost, apiPut, getAccessToken, getApiBaseUrl } from "../core/apiClient";
import type { PagedResponse } from "../auth/usersApi";

export type TemplateItem = {
  id: number;
  maTemplate: string;
  tenTemplate: string;
  loaiVanBanId: number;
  tenLoaiVanBan?: string;
  noiDungMau?: string;
  tepMau?: string;
  suDung: boolean;
};

export const fetchTemplates = (params?: {
  page?: number;
  size?: number;
  keyword?: string;
  loaiVanBanId?: number;
  suDung?: boolean;
}) => apiGet<PagedResponse<TemplateItem>>("/api/documents/templates", { params });

export const fetchTemplateDetail = (id: number) =>
  apiGet<TemplateItem>(`/api/documents/templates/${id}`);

export const createTemplate = (payload: {
  maTemplate: string;
  tenTemplate: string;
  loaiVanBanId: number;
  noiDungMau?: string;
  tepMau?: string;
  suDung?: boolean;
}) => apiPost<{ id: number; maTemplate: string }>("/api/documents/templates", payload);

export const updateTemplate = (id: number, payload: {
  tenTemplate?: string;
  loaiVanBanId?: number;
  noiDungMau?: string;
  tepMau?: string;
  suDung?: boolean;
}) => apiPut<{ id: number }>(`/api/documents/templates/${id}`, payload);

export const deleteTemplate = (id: number) =>
  apiDelete<{ id: number }>(`/api/documents/templates/${id}`);

export const uploadTemplateFile = (id: number, file: File) => {
  const formData = new FormData();
  formData.append("file", file);
  return apiPost<TemplateItem>(`/api/documents/templates/${id}/file`, formData);
};

export const downloadTemplateFile = async (id: number): Promise<Blob> => {
  const response = await fetch(new URL(`/api/documents/templates/${id}/file`, getApiBaseUrl()).toString(), {
    headers: {
      Authorization: `Bearer ${getAccessToken()}`,
    },
  });
  if (!response.ok) {
    throw new Error("Không thể tải tệp template");
  }
  return response.blob();
};

export const applyTemplate = (templateId: number, payload: {
  documentId: number;
  replaceData?: Record<string, string>;
}) => apiPost<{ documentId: number; templateId: number; content: string }>(
  `/api/documents/templates/${templateId}/apply`,
  payload
);

export const createDocumentFromTemplate = (payload: {
  templateId: number;
  soKyHieu?: string;
  trichYeu?: string;
  loaiVanBanId?: number;
  donViChuTriId?: number;
  nguoiKy?: string;
  ngayVanBan?: string;
  doMat?: string;
  doKhan?: string;
  replaceData?: Record<string, string>;
}) => apiPost<{ documentId: number; templateId: number; trangThai: number }>(
  "/api/documents/from-template",
  payload
);
