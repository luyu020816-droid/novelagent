/** 将后端枚举值映射为作者可读中文（界面展示用，API 仍用原值）。 */

export const STORYLINE_ROLE_OPTIONS = [
  { value: "MAIN", label: "主线" },
  { value: "SUB", label: "支线" },
  { value: "DARK", label: "暗线" },
] as const;

export const STORYLINE_STATUS_OPTIONS = [
  { value: "ACTIVE", label: "进行中" },
  { value: "DORMANT", label: "休眠" },
  { value: "CLOSED", label: "已完结" },
] as const;

export const CONFLUENCE_TYPE_OPTIONS = [
  { value: "intersect", label: "交叉（两线同框碰撞）" },
  { value: "absorb", label: "收束（副线并入主线）" },
  { value: "reveal", label: "揭露（暗线真相揭晓）" },
] as const;

export const STORY_PHASE_LABELS: Record<string, string> = {
  opening: "开局期",
  development: "发展期",
  convergence: "收敛期",
  finale: "终局期",
};

export const AUTOPILOT_MODE_OPTIONS = [
  { value: "MANUAL", label: "手动（仅自己操作）" },
  { value: "AUTO_QUEUE_GENERATE", label: "自动排队写下一章（不自动定稿）" },
  { value: "FULL_UNATTENDED", label: "全自动（条件允许时自动定稿并继续）" },
] as const;

export const AUTO_ACCEPT_OPTIONS = [
  { value: "NEVER", label: "从不自动定稿" },
  { value: "CRITIC_PASS", label: "审查通过即可定稿" },
  { value: "CRITIC_AND_METRICS", label: "审查通过且章后指标已生成" },
  { value: "CRITIC_PASS_AND_NARRATIVE", label: "审查通过且叙事任务已落实" },
] as const;

export const SUBTEXT_IMPORTANCE_OPTIONS = [
  { value: "low", label: "低" },
  { value: "medium", label: "中" },
  { value: "high", label: "高" },
] as const;

export const SUBTEXT_STATUS_LABELS: Record<string, string> = {
  pending: "待回收",
  consumed: "已回收",
};

function pickLabel(map: Record<string, string>, value: string | null | undefined, fallback = "—"): string {
  if (value == null || value === "") return fallback;
  return map[value] ?? value;
}

export function labelStorylineRole(value: string | null | undefined): string {
  return pickLabel(
    Object.fromEntries(STORYLINE_ROLE_OPTIONS.map((o) => [o.value, o.label])),
    value,
    "支线"
  );
}

export function labelStorylineStatus(value: string | null | undefined): string {
  return pickLabel(
    Object.fromEntries(STORYLINE_STATUS_OPTIONS.map((o) => [o.value, o.label])),
    value,
    "—"
  );
}

export function labelConfluenceType(value: string | null | undefined): string {
  return pickLabel(
    Object.fromEntries(CONFLUENCE_TYPE_OPTIONS.map((o) => [o.value, o.label])),
    value,
    "—"
  );
}

export function labelConfluenceResolved(resolved: boolean): string {
  return resolved ? "已兑现" : "未兑现";
}

export function labelStoryPhase(value: string | null | undefined): string {
  return pickLabel(STORY_PHASE_LABELS, value, "—");
}

export function labelAutopilotMode(value: string | null | undefined): string {
  return pickLabel(
    Object.fromEntries(AUTOPILOT_MODE_OPTIONS.map((o) => [o.value, o.label])),
    value,
    "手动"
  );
}

export function labelAutoAcceptPolicy(value: string | null | undefined): string {
  return pickLabel(
    Object.fromEntries(AUTO_ACCEPT_OPTIONS.map((o) => [o.value, o.label])),
    value,
    "从不自动定稿"
  );
}

export function labelSubtextImportance(value: string | null | undefined): string {
  return pickLabel(
    Object.fromEntries(SUBTEXT_IMPORTANCE_OPTIONS.map((o) => [o.value, o.label])),
    value,
    "中"
  );
}

export function labelSubtextStatus(value: string | null | undefined): string {
  return pickLabel(SUBTEXT_STATUS_LABELS, value, "—");
}

/** 下拉选项渲染辅助 */
export function renderSelectOptions(
  options: readonly { value: string; label: string }[]
): { value: string; label: string }[] {
  return [...options];
}
