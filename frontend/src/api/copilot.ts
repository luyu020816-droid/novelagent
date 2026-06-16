import { apiJson } from "./client";

export type CopilotScene = "init_wizard" | "outline_edit" | "chapter_coach" | "setup_coach";

export type CopilotChatMessage = { role: "user" | "assistant"; content: string };

export async function postCopilotChat(body: {
  projectId: string;
  scene: CopilotScene;
  messages: CopilotChatMessage[];
  contextBlob?: string;
}): Promise<{ reply: string }> {
  return apiJson<{ reply: string }>("/api/writer/copilot/chat", {
    method: "POST",
    body: JSON.stringify({
      projectId: body.projectId,
      scene: body.scene,
      messages: body.messages,
      contextBlob: body.contextBlob,
    }),
  });
}
