import { postSseStream } from "./sse";

export type ChapterVersionSnapshot = {
  id: string;
  projectId: string;
  chapterNo: number;
  version: number;
  status: string;
  chapterText: string;
  styledText: string;
  tokenBudgetStatus: Record<string, unknown> | null;
  llmUsageSummary: Record<string, unknown> | null;
  scenePlanJson: unknown;
  criticReportJson: unknown;
  aiCriticPass: boolean;
};

export type ChapterUsageAggregateRow = {
  chapterNo: number;
  callCount: number;
  totalTokens: number;
};

/** SSE 生成单章（Day 9：结束后 chapter_versions PENDING_REVIEW；artifact 含 chapter_version_pending）。 */
export function postChapterGenerateStream(
  projectId: string,
  chapterNo: number,
  onFrame: (eventName: string, payload: Record<string, unknown>) => void,
  body: Record<string, unknown> = {}
): Promise<void> {
  return postSseStream(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/generate`,
    body,
    (eventName, dataJson) => {
      const payload = JSON.parse(dataJson) as Record<string, unknown>;
      onFrame(eventName, payload);
    }
  );
}

export async function fetchChapterUsageByChapter(projectId: string): Promise<ChapterUsageAggregateRow[]> {
  const res = await fetch(`/api/projects/${encodeURIComponent(projectId)}/chapters/usage-by-chapter`);
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as ChapterUsageAggregateRow[];
}

export async function fetchLatestChapterVersion(
  projectId: string,
  chapterNo: number
): Promise<ChapterVersionSnapshot | null> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/versions/latest`
  );
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as ChapterVersionSnapshot;
}

export async function acceptChapterVersion(versionId: string): Promise<void> {
  const res = await fetch(`/api/chapters/versions/${encodeURIComponent(versionId)}/accept`, {
    method: "POST",
  });
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
}

export async function rejectChapterVersion(versionId: string): Promise<void> {
  const res = await fetch(`/api/chapters/versions/${encodeURIComponent(versionId)}/reject`, {
    method: "POST",
  });
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
}

/** 删除待审核草稿（仅 PENDING_REVIEW）。 */
export async function deleteChapterVersion(versionId: string): Promise<void> {
  const res = await fetch(`/api/chapters/versions/${encodeURIComponent(versionId)}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
}
