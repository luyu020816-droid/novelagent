import { apiJson } from "./client";

export type DagNodeType = {
  node_type: string;
  display_name: string;
  category: string;
  description: string;
  cpms_node_key: string;
  can_disable: boolean;
};

export type DagDefinition = {
  id: string;
  name: string;
  version: number;
  description?: string;
  nodes: Array<{
    id: string;
    type: string;
    label: string;
    enabled: boolean;
    position: { x: number; y: number };
    config?: Record<string, unknown>;
  }>;
  edges: Array<{
    id: string;
    source: string;
    target: string;
    condition?: string;
  }>;
};

export type ProjectDagResponse = {
  id: string | null;
  projectId: string;
  versionNo: number;
  label: string;
  dag: DagDefinition;
  active: boolean;
  usingSystemDefault: boolean;
};

export function getProjectDag(projectId: string): Promise<ProjectDagResponse> {
  return apiJson(`/api/projects/${encodeURIComponent(projectId)}/dag/active`);
}

export function saveProjectDag(projectId: string, dag: DagDefinition, label?: string): Promise<ProjectDagResponse> {
  return apiJson(`/api/projects/${encodeURIComponent(projectId)}/dag/active`, {
    method: "PUT",
    body: JSON.stringify({ dag, label }),
  });
}

export function validateProjectDag(projectId: string, dag: DagDefinition): Promise<{ ok: boolean; errors: string[] }> {
  return apiJson(`/api/projects/${encodeURIComponent(projectId)}/dag/validate`, {
    method: "POST",
    body: JSON.stringify({ dag }),
  });
}

export function listDagNodeTypes(projectId: string): Promise<{ count: number; types: DagNodeType[]; manual_add_types: string[] }> {
  return apiJson(`/api/projects/${encodeURIComponent(projectId)}/dag/node-types`);
}

export function getDefaultDagTemplate(projectId: string): Promise<DagDefinition> {
  return apiJson(`/api/projects/${encodeURIComponent(projectId)}/dag/default-template`);
}

export function scaffoldDagNode(
  projectId: string,
  description: string,
  instanceId: string,
  category = "validation"
): Promise<{ node: DagDefinition["nodes"][0]; meta: Record<string, string> }> {
  return apiJson(`/api/projects/${encodeURIComponent(projectId)}/dag/scaffold-node`, {
    method: "POST",
    body: JSON.stringify({ description, instance_id: instanceId, category }),
  });
}
