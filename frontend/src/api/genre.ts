import { apiJson, apiVoid } from "./client";
import { postSseStream } from "./sse";

export type GenreRecommendRequest = {
  targetPlatform: string;
  genderChannel: string;
  preferredGenres: string[];
  avoid: string[];
  writingStrength: string[];
  riskPreference: string;
};

export type SelectedDirection = {
  channel: string;
  genre: string;
  subTags: string[];
  reason: string;
};

export type CandidateRanking = {
  genre: string;
  heatScore: number;
  competitionScore: number;
  payoffDensity: number;
  serializationScore: number;
  originalitySpace: number;
  tokenCostLevel: string;
  finalScore: number;
  recommendReason: string;
  riskNote: string;
};

export type GenreDecisionContract = {
  selectedDirection: SelectedDirection;
  candidateRankings: CandidateRanking[];
  recommendedCoreHook: string;
  riskNotes: string[];
};

export type GenreRecommendResponse = {
  contractId: string;
  contract: GenreDecisionContract;
};

function splitList(s: string): string[] {
  return s
    .split(/[,，]/)
    .map((x) => x.trim())
    .filter(Boolean);
}

export function parseGenreForm(fields: {
  targetPlatform: string;
  genderChannel: string;
  preferredGenresRaw: string;
  avoidRaw: string;
  writingStrengthRaw: string;
  riskPreference: string;
}): GenreRecommendRequest {
  return {
    targetPlatform: fields.targetPlatform.trim(),
    genderChannel: fields.genderChannel.trim(),
    preferredGenres: splitList(fields.preferredGenresRaw),
    avoid: splitList(fields.avoidRaw),
    writingStrength: splitList(fields.writingStrengthRaw),
    riskPreference: fields.riskPreference.trim() || "medium",
  };
}

export function postGenreRecommend(
  projectId: string,
  body: GenreRecommendRequest
): Promise<GenreRecommendResponse> {
  return apiJson<GenreRecommendResponse>(
    `/api/projects/${encodeURIComponent(projectId)}/genre/recommend`,
    {
      method: "POST",
      body: JSON.stringify(body),
    }
  );
}

/** SSE：Java 透传 Writer；`persisted` 事件含落库后的 contractId / contract。 */
export function postGenreRecommendStream(
  projectId: string,
  body: GenreRecommendRequest,
  onFrame: (eventName: string, payload: Record<string, unknown>) => void
): Promise<void> {
  return postSseStream(
    `/api/projects/${encodeURIComponent(projectId)}/genre/recommend/stream`,
    body,
    (eventName, dataJson) => {
      const payload = JSON.parse(dataJson) as Record<string, unknown>;
      onFrame(eventName, payload);
    }
  );
}

export type GenreStoryHookStreamRequest = {
  storyHook: string;
  targetPlatform?: string;
  genderChannel?: string;
  riskPreference?: string;
  /** Skill 定稿后：Writer 单轮唯一题材锁定，无 Scout 多备选 */
  uniqueDirection?: boolean;
};

/** SSE：同一 Writer 流水线，请求体带 storyHook（偏好在后端填空数组）。 */
export function postGenreRecommendFromStoryStream(
  projectId: string,
  body: GenreStoryHookStreamRequest,
  onFrame: (eventName: string, payload: Record<string, unknown>) => void
): Promise<void> {
  return postSseStream(
    `/api/projects/${encodeURIComponent(projectId)}/genre/recommend/from-story/stream`,
    body,
    (eventName, dataJson) => {
      const payload = JSON.parse(dataJson) as Record<string, unknown>;
      onFrame(eventName, payload);
    }
  );
}

export function putGenreSelection(projectId: string, genreContractId: string): Promise<void> {
  return apiVoid(`/api/projects/${encodeURIComponent(projectId)}/genre/selected-contract`, {
    method: "PUT",
    body: JSON.stringify({ genreContractId }),
  });
}

/** 单条题材方案详情（含完整 raw_json）。 */
export type GenreContractDetail = {
  id: string;
  projectId: string;
  createdAt: string;
  source: string;
  storyHookText: string | null;
  rawJson: Record<string, unknown>;
};

export function getGenreContract(projectId: string, contractId: string): Promise<GenreContractDetail> {
  return apiJson<GenreContractDetail>(
    `/api/projects/${encodeURIComponent(projectId)}/genre/${encodeURIComponent(contractId)}`
  );
}

export function putGenreContract(
  projectId: string,
  contractId: string,
  body: { rawJson?: Record<string, unknown>; selectedDirection?: Record<string, unknown> }
): Promise<GenreContractDetail> {
  return apiJson<GenreContractDetail>(
    `/api/projects/${encodeURIComponent(projectId)}/genre/${encodeURIComponent(contractId)}`,
    {
      method: "PUT",
      body: JSON.stringify(body),
    }
  );
}

export function deleteGenreContract(projectId: string, contractId: string): Promise<void> {
  return apiVoid(`/api/projects/${encodeURIComponent(projectId)}/genre/${encodeURIComponent(contractId)}`, {
    method: "DELETE",
  });
}

export type GenreInterviewChatTurn = {
  role: string;
  content: string;
};

export type GenreInterviewResponse = {
  status: string;
  replyToUser: string;
  finalSummary?: string | null;
  coreSettings?: Record<string, unknown> | null;
  persistedNovelSeedContractId?: string | null;
};

export type GenreInterviewRequestBody = {
  chatHistory: GenreInterviewChatTurn[];
  /** 若提供，Writer 注入对应 Skill 全文，模型据此追问细节；可与空 chatHistory 配合自动开场。 */
  writerSkillId?: string | null;
};

/** 路径 B：多轮互动采访（非流式）。可选 writerSkillId 进入 Skill 确认模式。 */
export function postGenreInterview(
  projectId: string,
  chatHistory: GenreInterviewChatTurn[],
  options?: { writerSkillId?: string | null }
): Promise<GenreInterviewResponse> {
  const body: GenreInterviewRequestBody = { chatHistory };
  const sid = options?.writerSkillId?.trim();
  if (sid) {
    body.writerSkillId = sid;
  }
  return apiJson<GenreInterviewResponse>(
    `/api/projects/${encodeURIComponent(projectId)}/genre/interview`,
    {
      method: "POST",
      body: JSON.stringify(body),
    }
  );
}
