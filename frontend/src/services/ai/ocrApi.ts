import { apiPost } from "../core/apiClient";

/**
 * OCR trực tiếp qua AI service. Không cần upload tạm qua document-service,
 * tránh lỗi service-to-service và hoạt động tốt cho DOCX/PDF/ảnh người dùng chọn.
 */
export const ocrFileDirect = (documentId: number, userId: number, file: File) => {
  const formData = new FormData();
  formData.append("documentId", String(documentId));
  formData.append("userId", String(userId));
  formData.append("language", "vi");
  formData.append("file", file);
  return apiPost<{
    documentId: number;
    ocrText: string;
    confidence: number;
    modelUsed?: string;
    fileName?: string;
  }>("/api/ai/ocr/file", formData);
};

// Giữ API cũ để các màn hình/luồng khác không bị ảnh hưởng.
export const uploadOcrFile = (documentId: number, file: File) => {
  const formData = new FormData();
  formData.append("file", file);
  return apiPost<{ documentId: number; fileName: string; fileUrl: string }>(
    `/api/documents/${documentId}/ocr/upload`,
    formData
  );
};

export const processOcr = (documentId: number, payload: { fileUrl: string; language?: string }) =>
  apiPost<{ documentId: number; ocrText: string; confidence: number }>(
    `/api/documents/${documentId}/ocr/process`,
    payload
  );

export const saveOcrResult = (documentId: number, payload: { ocrText: string; confidence?: number }) =>
  apiPost<{ documentId: number; daOCR: boolean }>(`/api/documents/${documentId}/ocr/save`, payload);
