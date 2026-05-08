import { apiJson } from "./client";

export type Project = {
  id: string;
  name: string;
  language: string;
  targetChapters: number;
  currentChapter: number;
  status: string;
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

export function getProjectDetail(projectId: string): Promise<ProjectDetail> {
  return apiJson<ProjectDetail>(`/api/projects/${encodeURIComponent(projectId)}/detail`);
}

export function createProject(body: {
  name: string;
  language?: string;
  targetChapters?: number;
}): Promise<Project> {
  return apiJson<Project>("/api/projects", {
    method: "POST",
    body: JSON.stringify(body),
  });
}
