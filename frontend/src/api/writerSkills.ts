import { apiJson } from "./client";

export type WriterSkillOption = {
  id: string;
  label: string;
};

export type WriterSkillsResponse = {
  libraryDir: string;
  skills: WriterSkillOption[];
};

/** 经 Java 代理 Writer，列出 library 目录下可用 YAML Skill */
export function listWriterSkills(): Promise<WriterSkillsResponse> {
  return apiJson<WriterSkillsResponse>("/api/writer/skills");
}

export type StyleAnalyzeResult = {
  avgSentenceLen: number;
  sampleChars: number;
  styleGuideMd: string;
};

/** 从正文样本生成风格约束 Markdown（经 Java 代理 Writer）。 */
export function postWriterStyleAnalyze(sampleText: string): Promise<StyleAnalyzeResult> {
  return apiJson<StyleAnalyzeResult>("/api/writer/style/analyze", {
    method: "POST",
    body: JSON.stringify({ sampleText }),
  });
}

export type IntentSuggestedAction = {
  action: string;
  detail: string;
};

export type IntentPreviewResult = {
  suggestedActions: IntentSuggestedAction[];
};

/** 自然语言 → 建议下一步操作（经 Java 代理 Writer）。 */
export function postWriterIntentPreview(projectId: string, message: string): Promise<IntentPreviewResult> {
  return apiJson<IntentPreviewResult>("/api/writer/agent/intent-preview", {
    method: "POST",
    body: JSON.stringify({ projectId, message }),
  });
}
