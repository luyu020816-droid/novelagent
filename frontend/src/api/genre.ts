import { apiJson } from "./client";

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
