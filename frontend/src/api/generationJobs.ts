import { apiJson } from "./client";

export type GenerationJobQueued = {
  jobId: string;
  status: string;
  message?: string;
};

export type GenerationJobStatus = {
  jobId: string;
  projectId: string;
  chapterNo: number;
  status: string;
  currentStage: string | null;
  progressPct: number;
  errorMessage: string | null;
  chapterVersionId: string | null;
  totalTokens: number | null;
  retryWasteTokens: number | null;
  trimmedOptionalCount: number | null;
  criticRejectRounds: number | null;
  llmUsageSummary: Record<string, unknown> | null;
  tokenBudgetStatus: Record<string, unknown> | null;
  createdAt: string | null;
  updatedAt: string | null;
};

export async function postChapterGenerateAsync(
  projectId: string,
  chapterNo: number,
  body: Record<string, unknown> = {}
): Promise<GenerationJobQueued> {
  return apiJson<GenerationJobQueued>(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/generate-async`,
    { method: "POST", body: JSON.stringify(body) }
  );
}

export async function fetchLatestGenerationJob(
  projectId: string,
  chapterNo: number
): Promise<GenerationJobStatus | null> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/generation-jobs/latest`
  );
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as GenerationJobStatus;
}
