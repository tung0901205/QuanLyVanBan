import { apiGet, getAccessToken, getApiBaseUrl } from "../core/apiClient";

export const exportReport = (params: {
  reportType: string;
  format: string;
  fromDate?: string;
  toDate?: string;
  donViId?: number;
}) => apiGet<{ fileName: string; fileUrl: string }>("/api/reports/export", { params });

export const downloadReportFile = async (fileUrl: string): Promise<Blob> => {
  const response = await fetch(new URL(fileUrl, getApiBaseUrl()).toString(), {
    headers: { Authorization: `Bearer ${getAccessToken()}` },
  });
  if (!response.ok) throw new Error("Không thể tải báo cáo");
  return response.blob();
};
