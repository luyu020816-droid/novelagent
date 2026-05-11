import { apiJson } from "./client";

export type GenerationRoiJobRow = {
  jobId: string;
  chapterNo: number;
  status: string;
  totalTokens: number | null;
  retryWasteTokens: number | null;
  trimmedOptionalCount: number | null;
  criticRejectRounds: number | null;
  llmUsageSummary: Record<string, unknown> | null;
  tokenBudgetStatus: Record<string, unknown> | null;
  chapterVersionId: string | null;
  createdAt: string | null;
};

export async function fetchGenerationRoi(projectId: string): Promise<GenerationRoiJobRow[]> {
  return apiJson<GenerationRoiJobRow[]>(
    `/api/projects/${encodeURIComponent(projectId)}/generation-roi`
  );
}
