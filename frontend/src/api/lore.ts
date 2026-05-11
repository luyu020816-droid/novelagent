import { apiJson } from "./client";

export type LoreCharacterRow = {
  name?: string;
  last_chapter_no?: number;
  evidence?: string;
  role_hint?: string;
};

export type LoreRelationshipRow = {
  from_name?: string;
  to_name?: string;
  kind?: string;
  chapter_no?: number;
  evidence?: string;
};

export type LoreEventRow = {
  summary?: string;
  chapter_no?: number;
  evidence?: string;
};

export type LoreForeshadowRow = {
  text?: string;
  chapter_no?: number;
  evidence?: string;
  resolved?: boolean;
};

export type LoreSnapshot = {
  characters: LoreCharacterRow[];
  relationships: LoreRelationshipRow[];
  events: LoreEventRow[];
  foreshadowing: LoreForeshadowRow[];
  neo4j_enabled?: boolean;
};

export async function getProjectLoreGraph(projectId: string): Promise<LoreSnapshot> {
  return apiJson<LoreSnapshot>(`/api/projects/${encodeURIComponent(projectId)}/lore-graph`);
}
