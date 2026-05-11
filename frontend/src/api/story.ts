import { apiJson, apiVoid } from "./client";
import { postSseStream } from "./sse";

export type StoryInitResponse = {
  novelSeedContractId: string;
  storyContractId: string;
  novelSeed: Record<string, unknown>;
  storyContract: Record<string, unknown>;
  firstVolumeOutline: string;
  chapterContracts: unknown[];
  /** 作者长期意图（治理）；可与题材大纲并存 */
  authorIntent?: string | null;
  /** 不可违背条目，通常为 JSON 数组 */
  nonNegotiables?: unknown;
};

export function postStoryInit(
  projectId: string,
  options?: { wizardNotes?: string }
): Promise<StoryInitResponse> {
  const body: Record<string, string> = {};
  if (options?.wizardNotes != null && options.wizardNotes.trim() !== "") {
    body.wizardNotes = options.wizardNotes.trim();
  }
  return apiJson<StoryInitResponse>(
    `/api/projects/${encodeURIComponent(projectId)}/story/init`,
    { method: "POST", body: JSON.stringify(body) }
  );
}

/** SSE：长流程实时事件；`artifact.data` 为完整 Init 包；`persisted` 含 novelSeedContractId / storyContractId。 */
export function postStoryInitStream(
  projectId: string,
  onFrame: (eventName: string, payload: Record<string, unknown>) => void,
  options?: { wizardNotes?: string }
): Promise<void> {
  const body: Record<string, string> = {};
  if (options?.wizardNotes != null && options.wizardNotes.trim() !== "") {
    body.wizardNotes = options.wizardNotes.trim();
  }
  return postSseStream(
    `/api/projects/${encodeURIComponent(projectId)}/story/init/stream`,
    body,
    (eventName, dataJson) => {
      const payload = JSON.parse(dataJson) as Record<string, unknown>;
      onFrame(eventName, payload);
    }
  );
}

export async function getSelectedStoryBundle(projectId: string): Promise<StoryInitResponse | null> {
  const res = await fetch(`/api/projects/${encodeURIComponent(projectId)}/story/selected-bundle`);
  if (res.status === 404) {
    return null;
  }
  if (!res.ok) {
    throw new Error((await res.text()) || res.statusText);
  }
  return res.json() as Promise<StoryInitResponse>;
}

export function putStorySelection(projectId: string, storyContractId: string): Promise<void> {
  return apiVoid(`/api/projects/${encodeURIComponent(projectId)}/story/selected-bundle`, {
    method: "PUT",
    body: JSON.stringify({ storyContractId }),
  });
}

/** 保存当前选中快照的第一卷大纲正文。 */
export function putFirstVolumeOutline(projectId: string, firstVolumeOutline: string): Promise<void> {
  return apiVoid(`/api/projects/${encodeURIComponent(projectId)}/story/selected-bundle/outline`, {
    method: "PUT",
    body: JSON.stringify({ firstVolumeOutline }),
  });
}

/** 作者意图、硬约束与可选风格指南（写入当前选中 story_contract）。 */
export function putStoryGovernance(
  projectId: string,
  body: {
    authorIntent: string;
    nonNegotiables: unknown;
    styleGuideMd?: string | null;
  }
): Promise<void> {
  const payload: Record<string, unknown> = {
    authorIntent: body.authorIntent,
    nonNegotiables: body.nonNegotiables,
  };
  if (body.styleGuideMd !== undefined) {
    payload.styleGuideMd = body.styleGuideMd;
  }
  return apiVoid(`/api/projects/${encodeURIComponent(projectId)}/story/selected-bundle/governance`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}
