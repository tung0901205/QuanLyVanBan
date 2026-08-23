import { apiPost } from "../core/apiClient";

export const summarizeDocument = (payload: {
  documentId: number;
  userId?: number;
  text?: string;
  summaryType?: string;
  language?: string;
}) => apiPost<{
  documentId: number;
  summary: string;
  confidence?: number;
  modelUsed?: string;
  source?: "REQUEST" | "OCR_SAVED" | "ATTACHMENT" | "CONTENT" | "TITLE" | string;
  attachmentName?: string;
}>(
  "/api/ai/summarize",
  payload
);
