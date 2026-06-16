import { apiJson, apiVoid } from "./client";

export type GenreContractListItem = {
  id: string;
  createdAt: string;
  source: string;
  primaryGenreLabel: string;
  storyHookPreview: string;
};

export type StoryInitListItem = {
  storyContractId: string;
  novelSeedContractId: string | null;
  createdAt: string;
};

export type ProjectWorkspace = {
  selectedGenreContractId: string | null;
  selectedStoryContractId: string | null;
  genreContracts: GenreContractListItem[];
  storyInits: StoryInitListItem[];
};

export type Project = {
  id: string;
  name: string;
  language: string;
  targetChapters: number;
  currentChapter: number;
  status: string;
  /** 丛书预设，如 hp_fan；章节生成与初始化会传给 Writer */
  fanSeriesPreset?: string | null;
  narrativePhase?: string | null;
  narrativeCheckpointJson?: unknown;
  autopilotMode?: string;
  autoAcceptPolicy?: string;
  maxAutoChaptersPerRun?: number;
  autopilotChaptersThisRun?: number;
  autopilotPaused?: boolean;
  autopilotPauseReason?: string | null;
  pauseOnVectorSyncFailed?: boolean;
  narrativeDomainJson?: unknown;
  autopilotLastActionJson?: unknown;
  createdAt: string;
  updatedAt: string;
};

export type WriterProbeResult = {
  ok: boolean;
  responseBody: string | null;
  error: string | null;
};

export type WriterEngineStatus = {
  health: WriterProbeResult;
  test: WriterProbeResult;
};

export type ProjectDetail = {
  project: Project;
  writerEngine: WriterEngineStatus;
};

export function listProjects(): Promise<Project[]> {
  return apiJson<Project[]>("/api/projects");
}

/** 轻量项目摘要（不含 Writer 探测）。双书对照等场景用。 */
export function getProject(projectId: string): Promise<Project> {
  return apiJson<Project>(`/api/projects/${encodeURIComponent(projectId)}`);
}

export function getProjectDetail(projectId: string): Promise<ProjectDetail> {
  return apiJson<ProjectDetail>(`/api/projects/${encodeURIComponent(projectId)}/detail`);
}

export function getProjectWorkspace(projectId: string): Promise<ProjectWorkspace> {
  return apiJson<ProjectWorkspace>(`/api/projects/${encodeURIComponent(projectId)}/workspace`);
}

export function createProject(body: {
  name: string;
  language?: string;
  targetChapters?: number;
  fanSeriesPreset?: string | null;
}): Promise<Project> {
  return apiJson<Project>("/api/projects", {
    method: "POST",
    body: JSON.stringify(body),
  });
}

/** 设置或清除丛书预设（传 null 或 "" 清除） */
export function setFanSeriesPreset(projectId: string, fanSeriesPreset: string | null): Promise<Project> {
  return apiJson<Project>(`/api/projects/${encodeURIComponent(projectId)}/fan-series-preset`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      fanSeriesPreset:
        fanSeriesPreset === null || fanSeriesPreset === undefined || fanSeriesPreset === ""
          ? null
          : fanSeriesPreset,
    }),
  });
}

export function updateAutopilotSettings(
  projectId: string,
  body: {
    autopilotMode?: string;
    autoAcceptPolicy?: string;
    maxAutoChaptersPerRun?: number;
    pauseOnVectorSyncFailed?: boolean;
  }
): Promise<Project> {
  return apiJson<Project>(`/api/projects/${encodeURIComponent(projectId)}/autopilot/settings`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function emergencyPauseAutopilot(projectId: string, reason?: string | null): Promise<Project> {
  return apiJson<Project>(`/api/projects/${encodeURIComponent(projectId)}/autopilot/emergency-pause`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(reason != null && reason !== "" ? { reason } : {}),
  });
}

export function startAutopilotRun(projectId: string): Promise<Project> {
  return apiJson<Project>(`/api/projects/${encodeURIComponent(projectId)}/autopilot/start-run`, {
    method: "POST",
  });
}

/** PlotPilot 式叙事域快照（storylines / confluences 等），须为合法 JSON 对象。 */
export function patchNarrativeDomain(projectId: string, narrativeDomainJson: unknown): Promise<Project> {
  return apiJson<Project>(`/api/projects/${encodeURIComponent(projectId)}/narrative-domain`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ narrativeDomainJson }),
  });
}

export type SubtextLedgerItem = {
  id: string;
  projectId: string;
  chapterNo: number;
  characterRef: string | null;
  question: string;
  status: string;
  suggestedResolveChapter: number | null;
  consumedAtChapter: number | null;
  importance: string;
  createdAt: string;
};

export type ChapterNarrativeMetricRow = {
  id: string;
  chapterNo: number;
  tensionScore: number | null;
  styleSimilarity: number | null;
  commitId: string | null;
  createdAt: string;
  rawJson: unknown;
};

export function listSubtextLedger(projectId: string): Promise<SubtextLedgerItem[]> {
  return apiJson<SubtextLedgerItem[]>(`/api/projects/${encodeURIComponent(projectId)}/subtext`);
}

export function createSubtextLedger(
  projectId: string,
  body: {
    chapterNo: number;
    question: string;
    characterRef?: string | null;
    suggestedResolveChapter?: number | null;
    importance?: string | null;
  }
): Promise<SubtextLedgerItem> {
  return apiJson<SubtextLedgerItem>(`/api/projects/${encodeURIComponent(projectId)}/subtext`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

export function consumeSubtextLedger(
  projectId: string,
  entryId: string,
  consumedAtChapter: number
): Promise<SubtextLedgerItem> {
  return apiJson<SubtextLedgerItem>(
    `/api/projects/${encodeURIComponent(projectId)}/subtext/${encodeURIComponent(entryId)}/consume`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ consumedAtChapter }),
    }
  );
}

export function listChapterNarrativeMetrics(projectId: string): Promise<ChapterNarrativeMetricRow[]> {
  return apiJson<ChapterNarrativeMetricRow[]>(
    `/api/projects/${encodeURIComponent(projectId)}/narrative-metrics`
  );
}

export async function deleteProject(projectId: string): Promise<void> {
  const res = await fetch(`/api/projects/${encodeURIComponent(projectId)}`, { method: "DELETE" });
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
}

/** 全书字符串替换（越长字符串优先）；影响快照大纲、章纲 JSON、章节正文。 */
export function postEntityReplace(
  projectId: string,
  replacements: { from: string; to: string }[]
): Promise<void> {
  return apiVoid(`/api/projects/${encodeURIComponent(projectId)}/entities/replace`, {
    method: "POST",
    body: JSON.stringify({ replacements }),
  });
}
