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

export type ChapterPrewritePlan = {
  chapterNo: number;
  prevChapterCommitSummary: unknown;
  planSummary: string;
  confirmed: boolean;
};

/** 后端/模型可能返回对象或嵌套 JSON，统一成可编辑的多行文本。 */
function coerceMultilineText(raw: unknown): string {
  if (raw == null) return "";
  if (typeof raw === "string") return raw;
  try {
    return JSON.stringify(raw, null, 2);
  } catch {
    return String(raw);
  }
}

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

export type ChapterLatestCommitVector = {
  commitId: string;
  chapterNo: number;
  version: number;
  vectorSyncStatus: string | null;
  vectorSyncError: string | null;
  vectorSyncAt: string | null;
  vectorSyncAttempts: number;
  summary: unknown;
};

export async function fetchLatestCommitVector(
  projectId: string,
  chapterNo: number
): Promise<ChapterLatestCommitVector | null> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/commits/latest-vector`
  );
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  const j = (await res.json()) as Record<string, unknown>;
  return {
    commitId: String(j.commitId ?? ""),
    chapterNo: Number(j.chapterNo ?? chapterNo),
    version: Number(j.version ?? 0),
    vectorSyncStatus: j.vectorSyncStatus != null ? String(j.vectorSyncStatus) : null,
    vectorSyncError: j.vectorSyncError != null ? String(j.vectorSyncError) : null,
    vectorSyncAt: j.vectorSyncAt != null ? String(j.vectorSyncAt) : null,
    vectorSyncAttempts: Number(j.vectorSyncAttempts ?? 0),
    summary: j.summary,
  };
}

export async function postRetryChapterVectorSync(commitId: string): Promise<void> {
  const res = await fetch(`/api/chapters/commits/${encodeURIComponent(commitId)}/retry-vector-sync`, {
    method: "POST",
  });
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
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

export async function fetchChapterPrewritePlan(
  projectId: string,
  chapterNo: number
): Promise<ChapterPrewritePlan> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/prewrite-plan`
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as ChapterPrewritePlan;
}

export async function putChapterPrewritePlan(
  projectId: string,
  chapterNo: number,
  planSummary: string
): Promise<ChapterPrewritePlan> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/prewrite-plan`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ planSummary }),
    }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as ChapterPrewritePlan;
}

export async function postChapterPrewritePlanConfirm(
  projectId: string,
  chapterNo: number
): Promise<ChapterPrewritePlan> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/prewrite-plan/confirm`,
    { method: "POST" }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as ChapterPrewritePlan;
}

export async function postChapterPrewritePlanProposeAi(
  projectId: string,
  chapterNo: number
): Promise<string> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/prewrite-plan/propose-ai`,
    { method: "POST" }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  const j = (await res.json()) as { planSummary?: string };
  return j.planSummary ?? "";
}

export async function postFanqieEditorReview(
  projectId: string,
  chapterNo: number,
  chapterText?: string
): Promise<string> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/fanqie-editor-review`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(chapterText != null && chapterText !== "" ? { chapterText } : {}),
    }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  const j = (await res.json()) as Record<string, unknown>;
  return coerceMultilineText(j.review ?? j["Review"]).trim();
}

export async function postPolishWithNotes(
  projectId: string,
  chapterNo: number,
  body: { chapterText?: string; tomatoReview: string; authorNotes: string }
): Promise<string> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/polish-with-notes`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        tomatoReview: body.tomatoReview,
        authorNotes: body.authorNotes,
        ...(body.chapterText != null && body.chapterText !== "" ? { chapterText: body.chapterText } : {}),
      }),
    }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  const j = (await res.json()) as Record<string, unknown>;
  return coerceMultilineText(j.polishedText ?? j.polished_text).trim();
}

export async function postImportPolishedDraft(
  projectId: string,
  chapterNo: number,
  chapterText: string,
  styledText?: string
): Promise<ChapterVersionSnapshot> {
  const res = await fetch(
    `/api/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/import-polished-draft`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chapterText, styledText: styledText ?? "" }),
    }
  );
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return (await res.json()) as ChapterVersionSnapshot;
}
