import { apiJson, apiVoid } from "./client";
import type { GenreInterviewResponse } from "./genre";

export type SetupStatus = {
  setupMode: string;
  currentStage: string;
  genreConfirmed: boolean;
  storyConfirmed: boolean;
  narrativeConfirmed: boolean;
  readyToWrite: boolean;
  nextActionHint: string;
  pendingGenreProposalId: string | null;
  pendingStoryProposalId: string | null;
  pendingNarrativeProposalId: string | null;
  confirmedGenrePreview: unknown;
  confirmedStoryPreview: unknown;
  confirmedNarrativePreview: unknown;
  storylineCount: number;
  writingStarted: boolean;
  setupLocked: boolean;
  acceptedChapterCount: number;
  draftVersionCount: number;
  resumeChapterNo: number;
};

export type SetupProposal = {
  id: string;
  projectId: string;
  stage: string;
  status: string;
  payload: unknown;
  assistantReply: string | null;
  baseVersion: number;
  createdAt: string;
};

export type SetupGenreProposeBody = {
  targetPlatform?: string;
  genderChannel?: string;
  preferredGenres?: string[];
  avoid?: string[];
  writingStrength?: string[];
  riskPreference?: string;
  storyHook?: string;
  uniqueDirection?: boolean;
};

const base = (projectId: string) => `/api/projects/${encodeURIComponent(projectId)}/setup`;

export function getSetupStatus(projectId: string): Promise<SetupStatus> {
  return apiJson<SetupStatus>(`${base(projectId)}/status`);
}

export function setSetupMode(projectId: string, setupMode: "standard" | "skill"): Promise<void> {
  return apiVoid(`${base(projectId)}/mode`, {
    method: "POST",
    body: JSON.stringify({ setupMode }),
  });
}

export function proposeGenre(projectId: string, body: SetupGenreProposeBody): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/genre/propose`, {
    method: "POST",
    body: JSON.stringify(body),
  });
}

export function proposeGenreFromInterview(projectId: string, interview: GenreInterviewResponse): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/genre/propose-from-interview`, {
    method: "POST",
    body: JSON.stringify(interview),
  });
}

export function reviseGenre(projectId: string, feedback: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/genre/revise`, {
    method: "POST",
    body: JSON.stringify({ feedback }),
  });
}

export function applyGenre(projectId: string, proposalId?: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/genre/apply`, {
    method: "POST",
    body: JSON.stringify({ proposalId: proposalId ?? null, replaceExisting: false }),
  });
}

export function proposeStory(projectId: string, wizardNotes?: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/story/propose`, {
    method: "POST",
    body: JSON.stringify({ wizardNotes: wizardNotes ?? null }),
  });
}

export function reviseStory(projectId: string, feedback: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/story/revise`, {
    method: "POST",
    body: JSON.stringify({ feedback }),
  });
}

export function applyStory(projectId: string, proposalId?: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/story/apply`, {
    method: "POST",
    body: JSON.stringify({ proposalId: proposalId ?? null, replaceExisting: false }),
  });
}

export function proposeNarrative(projectId: string, useLlm = false): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/narrative/propose?useLlm=${useLlm}`, {
    method: "POST",
  });
}

export function reviseNarrative(projectId: string, feedback: string, writerSkillId?: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/narrative/revise`, {
    method: "POST",
    body: JSON.stringify({ feedback, writerSkillId: writerSkillId ?? null }),
  });
}

export function applyNarrative(projectId: string, proposalId?: string, replaceExisting = true): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/narrative/apply`, {
    method: "POST",
    body: JSON.stringify({ proposalId: proposalId ?? null, replaceExisting }),
  });
}

export function getSetupProposal(projectId: string, proposalId: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/proposals/${encodeURIComponent(proposalId)}`);
}

export function discardSetupProposal(projectId: string, proposalId: string): Promise<SetupProposal> {
  return apiJson<SetupProposal>(`${base(projectId)}/proposals/${encodeURIComponent(proposalId)}/discard`, {
    method: "POST",
  });
}

/** 按当前 Setup 进度顺序生成题材 / 故事 / 结构草案（均需分别确认采纳）。 */
export function proposeAll(projectId: string, genreReq?: SetupGenreProposeBody): Promise<Record<string, string>> {
  return apiJson<Record<string, string>>(`${base(projectId)}/propose-all`, {
    method: "POST",
    body: JSON.stringify(genreReq ?? {}),
  });
}
