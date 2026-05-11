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
