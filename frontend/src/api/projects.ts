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

export function listProjects(): Promise<Project[]> {
  return apiJson<Project[]>("/api/projects");
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
