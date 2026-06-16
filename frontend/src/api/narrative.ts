import { apiJson, apiVoid } from "./client";

export type NarrativeStorylineRow = {
  id: string;
  projectId: string;
  storylineKey: string;
  title: string;
  parentStorylineId: string | null;
  storylineRole?: string;
  status: string;
  estStartChapter: number | null;
  estEndChapter: number | null;
  milestonesJson: unknown;
  currentMilestoneIndex: number;
  lastActiveChapterNo: number | null;
  sortOrder: number;
  updatedAt: string;
};

export type NarrativeConfluenceRow = {
  id: string;
  projectId: string;
  primaryStorylineId: string;
  secondaryStorylineId: string;
  targetChapter: number;
  confluenceType: string;
  resolved: boolean;
  notes: string | null;
  contextSummary?: string | null;
  preRevealHint?: string | null;
  behaviorGuards?: unknown;
  createdAt: string;
};

export type NarrativeValidationResult = {
  errors: { code: string; message: string; severity: string }[];
  warnings: { code: string; message: string; severity: string }[];
  ok?: boolean;
};

export type StorylineUpsertPayload = {
  storylineKey: string;
  title: string;
  parentStorylineId?: string | null;
  storylineRole?: string | null;
  status?: string | null;
  estStartChapter?: number | null;
  estEndChapter?: number | null;
  milestonesJson?: unknown;
  currentMilestoneIndex?: number | null;
  lastActiveChapterNo?: number | null;
  sortOrder?: number | null;
};

export type ConfluenceCreatePayload = {
  primaryStorylineId: string;
  secondaryStorylineId: string;
  targetChapter: number;
  confluenceType?: string | null;
  notes?: string | null;
  contextSummary?: string | null;
  preRevealHint?: string | null;
  behaviorGuards?: unknown;
};

const base = (projectId: string) => `/api/projects/${encodeURIComponent(projectId)}/narrative`;

export function listNarrativeStorylines(projectId: string): Promise<NarrativeStorylineRow[]> {
  return apiJson<NarrativeStorylineRow[]>(`${base(projectId)}/storylines`);
}

export function createNarrativeStoryline(projectId: string, body: StorylineUpsertPayload): Promise<NarrativeStorylineRow> {
  return apiJson<NarrativeStorylineRow>(`${base(projectId)}/storylines`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function updateNarrativeStoryline(
  projectId: string,
  storylineId: string,
  body: StorylineUpsertPayload
): Promise<NarrativeStorylineRow> {
  return apiJson<NarrativeStorylineRow>(`${base(projectId)}/storylines/${encodeURIComponent(storylineId)}`, {
    method: "PATCH",
    body: JSON.stringify(body),
  });
}

export function deleteNarrativeStoryline(projectId: string, storylineId: string): Promise<void> {
  return apiVoid(`${base(projectId)}/storylines/${encodeURIComponent(storylineId)}`, { method: "DELETE" });
}

export function listNarrativeConfluences(projectId: string): Promise<NarrativeConfluenceRow[]> {
  return apiJson<NarrativeConfluenceRow[]>(`${base(projectId)}/confluences`);
}

export function createNarrativeConfluence(projectId: string, body: ConfluenceCreatePayload): Promise<NarrativeConfluenceRow> {
  return apiJson<NarrativeConfluenceRow>(`${base(projectId)}/confluences`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function resolveNarrativeConfluence(projectId: string, confluenceId: string, resolved: boolean): Promise<NarrativeConfluenceRow> {
  return apiJson<NarrativeConfluenceRow>(`${base(projectId)}/confluences/${encodeURIComponent(confluenceId)}/resolve`, {
    method: "POST",
    body: JSON.stringify({ resolved }),
  });
}

export function deleteNarrativeConfluence(projectId: string, confluenceId: string): Promise<void> {
  return apiVoid(`${base(projectId)}/confluences/${encodeURIComponent(confluenceId)}`, { method: "DELETE" });
}

export function getChapterObligationsPreview(projectId: string, chapterNo: number): Promise<unknown> {
  return apiJson<unknown>(`${base(projectId)}/chapter-obligations-preview?chapterNo=${chapterNo}`);
}

export function validateNarrativeStructure(projectId: string): Promise<NarrativeValidationResult> {
  return apiJson<NarrativeValidationResult>(`${base(projectId)}/validate`);
}

export function exportNarrativeDomainFromPg(projectId: string): Promise<unknown> {
  return apiJson<unknown>(`${base(projectId)}/export-domain-json`);
}

export type NarrativeDomainImportResult = {
  storylinesUpserted: number;
  confluencesCreated: number;
  domainSnapshot: unknown;
};

/** 将 PG 导出写入 projects.narrative_domain_json。 */
export function syncNarrativeDomainJson(projectId: string): Promise<unknown> {
  return apiJson<unknown>(`${base(projectId)}/sync-domain-json`, { method: "POST" });
}

/** 从 JSON 对象导入 PG（保存叙事域时后端也会自动调用）。 */
export function importNarrativeFromDomainJson(projectId: string, domain?: unknown): Promise<NarrativeDomainImportResult> {
  return apiJson<NarrativeDomainImportResult>(`${base(projectId)}/import-from-domain-json`, {
    method: "POST",
    body: domain != null ? JSON.stringify(domain) : "{}",
  });
}

export function patchNarrativeAcceptPolicy(projectId: string, policy: unknown): Promise<unknown> {
  return apiJson<unknown>(`${base(projectId)}/accept-policy`, {
    method: "POST",
    body: JSON.stringify(policy),
  });
}
