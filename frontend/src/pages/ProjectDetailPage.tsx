import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  deleteGenreContract,
  getGenreContract,
  parseGenreForm,
  postGenreInterview,
  postGenreRecommendFromStoryStream,
  postGenreRecommendStream,
  putGenreContract,
  putGenreSelection,
  type GenreDecisionContract,
  type GenreInterviewChatTurn,
  type GenreInterviewResponse,
  type GenreRecommendResponse,
} from "../api/genre";
import {
  consumeSubtextLedger,
  createSubtextLedger,
  deleteProject,
  emergencyPauseAutopilot,
  getProjectDetail,
  getProjectWorkspace,
  listChapterNarrativeMetrics,
  listSubtextLedger,
  patchNarrativeDomain,
  setFanSeriesPreset,
  startAutopilotRun,
  updateAutopilotSettings,
  type ChapterNarrativeMetricRow,
  type ProjectDetail,
  type ProjectWorkspace,
  type SubtextLedgerItem,
} from "../api/projects";
import {
  createNarrativeConfluence,
  createNarrativeStoryline,
  deleteNarrativeConfluence,
  deleteNarrativeStoryline,
  getChapterObligationsPreview,
  exportNarrativeDomainFromPg,
  validateNarrativeStructure,
  syncNarrativeDomainJson,
  importNarrativeFromDomainJson,
  listNarrativeConfluences,
  listNarrativeStorylines,
  resolveNarrativeConfluence,
  updateNarrativeStoryline,
  type NarrativeConfluenceRow,
  type NarrativeStorylineRow,
} from "../api/narrative";
import { listWriterSkills, type WriterSkillOption } from "../api/writerSkills";
import ProjectWorkflowBanner from "../components/ProjectWorkflowBanner";
import { getSetupStatus, type SetupStatus } from "../api/setup";
import {
  AUTOPILOT_MODE_OPTIONS,
  AUTO_ACCEPT_OPTIONS,
  CONFLUENCE_TYPE_OPTIONS,
  STORYLINE_ROLE_OPTIONS,
  STORYLINE_STATUS_OPTIONS,
  SUBTEXT_IMPORTANCE_OPTIONS,
  labelAutoAcceptPolicy,
  labelAutopilotMode,
  labelConfluenceResolved,
  labelConfluenceType,
  labelStoryPhase,
  labelStorylineRole,
  labelStorylineStatus,
  labelSubtextStatus,
} from "../lib/uiLabels";

function asRecord(v: unknown): Record<string, unknown> | null {
  return v != null && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

function strField(obj: Record<string, unknown>, ...keys: string[]): string {
  for (const k of keys) {
    const v = obj[k];
    if (typeof v === "string") return v;
  }
  return "";
}

function subTagsToComma(sd: Record<string, unknown>): string {
  const st = sd.subTags ?? sd.sub_tags;
  if (Array.isArray(st)) return st.filter((x): x is string => typeof x === "string").join(", ");
  return "";
}

function riskNotesFromRaw(raw: Record<string, unknown>): string[] {
  const r = raw.riskNotes ?? raw.risk_notes;
  if (!Array.isArray(r)) return [];
  return r.filter((x): x is string => typeof x === "string");
}

function candidatesFromRaw(raw: Record<string, unknown>): Record<string, unknown>[] {
  const c = raw.candidateRankings ?? raw.candidate_rankings;
  if (!Array.isArray(c)) return [];
  return c.map((item) => asRecord(item)).filter((x): x is Record<string, unknown> => x != null);
}

/** 须与 `writer-python/app/api/genre.py` 中 `_SKILL_INTERVIEW_AUTO_OPENER` 一致 */
const SKILL_INTERVIEW_OPENER =
  "我已在本项目中选择了丛书 Skill。请先结合 Skill 核对规则与禁忌，用 1～2 个问题请作者说明希望落笔的题材或子类型（若 Skill 已限定则只做复述确认），再问一个关键场景或开篇基调假设。";

function storylinePickLabel(s: NarrativeStorylineRow): string {
  const t = (s.title || s.storylineKey).length > 32 ? `${(s.title || s.storylineKey).slice(0, 32)}…` : s.title || s.storylineKey;
  const role = labelStorylineRole(s.storylineRole);
  return `${t}（${role}）`;
}

function storylineLabelById(lines: NarrativeStorylineRow[], id: string): string {
  const s = lines.find((x) => x.id === id);
  return s ? storylinePickLabel(s) : `${id.slice(0, 8)}…`;
}

function parseBehaviorGuards(raw: string): unknown {
  const t = raw.trim();
  if (!t || t === "[]") return undefined;
  try {
    return JSON.parse(t) as unknown;
  } catch {
    return undefined;
  }
}

export default function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [data, setData] = useState<ProjectDetail | null>(null);
  const [workspace, setWorkspace] = useState<ProjectWorkspace | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteErr, setDeleteErr] = useState<string | null>(null);

  const [fanPresetDraft, setFanPresetDraft] = useState("");
  const [fanPresetBusy, setFanPresetBusy] = useState(false);
  const [fanPresetErr, setFanPresetErr] = useState<string | null>(null);
  const [autopilotModeDraft, setAutopilotModeDraft] = useState("MANUAL");
  const [autoAcceptDraft, setAutoAcceptDraft] = useState("NEVER");
  const [maxAutoRunDraft, setMaxAutoRunDraft] = useState(20);
  const [pauseOnVectorFailDraft, setPauseOnVectorFailDraft] = useState(true);
  const [autopilotBusy, setAutopilotBusy] = useState(false);
  const [autopilotErr, setAutopilotErr] = useState<string | null>(null);
  const [narrativeDomainDraft, setNarrativeDomainDraft] = useState("{}\n");
  const [narrativeDomainErr, setNarrativeDomainErr] = useState<string | null>(null);
  const [narrativeDomainBusy, setNarrativeDomainBusy] = useState(false);
  const [narrativeUiTab, setNarrativeUiTab] = useState<"structure" | "advanced">("structure");
  const [narrStructStorylines, setNarrStructStorylines] = useState<NarrativeStorylineRow[]>([]);
  const [narrStructConfluences, setNarrStructConfluences] = useState<NarrativeConfluenceRow[]>([]);
  const [narrStructErr, setNarrStructErr] = useState<string | null>(null);
  const [narrStructBusy, setNarrStructBusy] = useState(false);
  const [hubTab, setHubTab] = useState<"guide" | "structure" | "genre" | "more">("guide");
  const [setupStatus, setSetupStatus] = useState<SetupStatus | null>(null);
  const [oblPreviewChapter, setOblPreviewChapter] = useState(1);
  const [oblPreviewJson, setOblPreviewJson] = useState<unknown | null>(null);
  const [slNewKey, setSlNewKey] = useState("");
  const [slNewTitle, setSlNewTitle] = useState("");
  const [slNewParent, setSlNewParent] = useState("");
  const [slNewRole, setSlNewRole] = useState("SUB");
  const [slNewStatus, setSlNewStatus] = useState("ACTIVE");
  const [slNewEstS, setSlNewEstS] = useState("");
  const [slNewEstE, setSlNewEstE] = useState("");
  const [slNewMilestones, setSlNewMilestones] = useState("[]");
  const [editingSl, setEditingSl] = useState<NarrativeStorylineRow | null>(null);
  const [cfPrimary, setCfPrimary] = useState("");
  const [cfSecondary, setCfSecondary] = useState("");
  const [cfTarget, setCfTarget] = useState(1);
  const [cfType, setCfType] = useState("intersect");
  const [cfNotes, setCfNotes] = useState("");
  const [cfContextSummary, setCfContextSummary] = useState("");
  const [cfPreRevealHint, setCfPreRevealHint] = useState("");
  const [cfBehaviorGuards, setCfBehaviorGuards] = useState("[]");
  const [editTitle, setEditTitle] = useState("");
  const [editRole, setEditRole] = useState("SUB");
  const [editStatus, setEditStatus] = useState("ACTIVE");
  const [editParent, setEditParent] = useState("");
  const [editEstS, setEditEstS] = useState("");
  const [editEstE, setEditEstE] = useState("");
  const [editSort, setEditSort] = useState("");
  const [editMilestones, setEditMilestones] = useState("[]");
  const [editCurrentMi, setEditCurrentMi] = useState("0");
  const [editLastActive, setEditLastActive] = useState("");
  const [subtextEntries, setSubtextEntries] = useState<SubtextLedgerItem[]>([]);
  const [subtextErr, setSubtextErr] = useState<string | null>(null);
  const [subtextBusy, setSubtextBusy] = useState(false);
  const [subtextNewChapterNo, setSubtextNewChapterNo] = useState(1);
  const [subtextNewQuestion, setSubtextNewQuestion] = useState("");
  const [subtextNewChar, setSubtextNewChar] = useState("");
  const [subtextNewSug, setSubtextNewSug] = useState<string>("");
  const [subtextNewImp, setSubtextNewImp] = useState("medium");
  const [metricsRows, setMetricsRows] = useState<ChapterNarrativeMetricRow[]>([]);
  const [metricsErr, setMetricsErr] = useState<string | null>(null);
  const [writerSkills, setWriterSkills] = useState<WriterSkillOption[]>([]);
  const [writerSkillsErr, setWriterSkillsErr] = useState<string | null>(null);


  const [genreBusy, setGenreBusy] = useState(false);
  const [genreErr, setGenreErr] = useState<string | null>(null);
  const [genreResult, setGenreResult] = useState<GenreRecommendResponse | null>(null);
  const [genreStreamLog, setGenreStreamLog] = useState("");
  const [targetPlatform, setTargetPlatform] = useState("番茄");
  const [genderChannel, setGenderChannel] = useState("男频");
  const [preferredGenresRaw, setPreferredGenresRaw] = useState("");
  const [avoidRaw, setAvoidRaw] = useState("强虐, 纯后宫");
  const [writingStrengthRaw, setWritingStrengthRaw] = useState("爽点, 反转");
  const [riskPreference, setRiskPreference] = useState("medium");

  const [hookBusy, setHookBusy] = useState(false);
  const [hookErr, setHookErr] = useState<string | null>(null);
  const [hookStreamLog, setHookStreamLog] = useState("");
  const [hookPlatform, setHookPlatform] = useState("番茄");
  const [hookChannel, setHookChannel] = useState("男频");
  const [hookRisk, setHookRisk] = useState("medium");

  const [skillPickId, setSkillPickId] = useState("");
  const [skillMessages, setSkillMessages] = useState<GenreInterviewChatTurn[]>([]);
  const [skillInput, setSkillInput] = useState("");
  const [skillBusy, setSkillBusy] = useState(false);
  const [skillErr, setSkillErr] = useState<string | null>(null);
  const [skillDone, setSkillDone] = useState<GenreInterviewResponse | null>(null);

  const [interviewMessages, setInterviewMessages] = useState<GenreInterviewChatTurn[]>([]);
  const [interviewInput, setInterviewInput] = useState("");
  const [interviewBusy, setInterviewBusy] = useState(false);
  const [interviewErr, setInterviewErr] = useState<string | null>(null);
  const [interviewDone, setInterviewDone] = useState<GenreInterviewResponse | null>(null);

  const [pickGenreId, setPickGenreId] = useState<string | null>(null);
  const [pickBusy, setPickBusy] = useState(false);
  const [pickErr, setPickErr] = useState<string | null>(null);

  const [genreModalId, setGenreModalId] = useState<string | null>(null);
  const [genreModalBusy, setGenreModalBusy] = useState(false);
  const [genreModalSaveBusy, setGenreModalSaveBusy] = useState(false);
  const [genreModalErr, setGenreModalErr] = useState<string | null>(null);
  const [genreModalRaw, setGenreModalRaw] = useState<Record<string, unknown> | null>(null);
  const [genreModalHook, setGenreModalHook] = useState("");
  const [genreModalGenre, setGenreModalGenre] = useState("");
  const [genreModalChannel, setGenreModalChannel] = useState("");
  const [genreModalReason, setGenreModalReason] = useState("");
  const [genreModalSubTags, setGenreModalSubTags] = useState("");
  const [genreModalCandidates, setGenreModalCandidates] = useState<Record<string, unknown>[]>([]);
  const [genreModalRiskText, setGenreModalRiskText] = useState("");

  const refreshWorkspace = useCallback(async () => {
    if (!projectId) return;
    try {
      const w = await getProjectWorkspace(projectId);
      setWorkspace(w);
      setPickGenreId(w.selectedGenreContractId);
    } catch {
      /* ignore */
    }
  }, [projectId]);

  useEffect(() => {
    if (!projectId) {
      setErr("缺少项目 ID");
      return;
    }
    setErr(null);
    getProjectDetail(projectId)
      .then(setData)
      .catch((e: Error) => setErr(e.message));
    refreshWorkspace();
    getSetupStatus(projectId)
      .then(setSetupStatus)
      .catch(() => setSetupStatus(null));
  }, [projectId, refreshWorkspace]);

  useEffect(() => {
    if (!data?.project) return;
    setFanPresetDraft(data.project.fanSeriesPreset ?? "");
  }, [data?.project?.id, data?.project?.fanSeriesPreset]);

  useEffect(() => {
    if (!data?.project) return;
    setAutopilotModeDraft(data.project.autopilotMode ?? "MANUAL");
    setAutoAcceptDraft(data.project.autoAcceptPolicy ?? "NEVER");
    setMaxAutoRunDraft(data.project.maxAutoChaptersPerRun ?? 20);
    setPauseOnVectorFailDraft(data.project.pauseOnVectorSyncFailed !== false);
  }, [
    data?.project?.id,
    data?.project?.autopilotMode,
    data?.project?.autoAcceptPolicy,
    data?.project?.maxAutoChaptersPerRun,
    data?.project?.pauseOnVectorSyncFailed,
  ]);

  useEffect(() => {
    if (!data?.project) return;
    const nd = data.project.narrativeDomainJson;
    if (nd == null || nd === undefined) {
      setNarrativeDomainDraft("{}\n");
      return;
    }
    try {
      setNarrativeDomainDraft(JSON.stringify(nd, null, 2));
    } catch {
      setNarrativeDomainDraft(String(nd));
    }
  }, [data?.project?.id, data?.project?.updatedAt]);

  useEffect(() => {
    if (data?.project?.currentChapter != null) {
      setOblPreviewChapter(Math.max(1, data.project.currentChapter));
    }
  }, [data?.project?.id, data?.project?.currentChapter]);

  useEffect(() => {
    if (!projectId || !data?.project) return;
    let cancelled = false;
    void (async () => {
      setSubtextErr(null);
      setMetricsErr(null);
      try {
        const [st, mt, sl, cf] = await Promise.all([
          listSubtextLedger(projectId),
          listChapterNarrativeMetrics(projectId),
          listNarrativeStorylines(projectId),
          listNarrativeConfluences(projectId),
        ]);
        if (!cancelled) {
          setSubtextEntries(st);
          setMetricsRows(mt);
          setNarrStructStorylines(sl);
          setNarrStructConfluences(cf);
          setNarrStructErr(null);
        }
      } catch (ex) {
        if (!cancelled) {
          const msg = ex instanceof Error ? ex.message : String(ex);
          setSubtextErr(msg);
          setMetricsErr(msg);
          setNarrStructErr(msg);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [projectId, data?.project?.id, data?.project?.updatedAt]);

  useEffect(() => {
    if (!projectId) return;
    listWriterSkills()
      .then((r) => {
        setWriterSkills(r.skills);
        setWriterSkillsErr(null);
      })
      .catch((e: Error) => {
        setWriterSkills([]);
        setWriterSkillsErr(e.message);
      });
  }, [projectId]);

  useEffect(() => {
    setInterviewMessages([]);
    setInterviewInput("");
    setInterviewErr(null);
    setInterviewDone(null);
  }, [projectId]);

  useEffect(() => {
    setSkillPickId("");
    setSkillMessages([]);
    setSkillInput("");
    setSkillErr(null);
    setSkillDone(null);
  }, [projectId]);

  useEffect(() => {
    setSkillMessages([]);
    setSkillInput("");
    setSkillErr(null);
    setSkillDone(null);
  }, [skillPickId]);

  const onSaveFanPreset = useCallback(async () => {
    if (!projectId) return;
    setFanPresetErr(null);
    setFanPresetBusy(true);
    try {
      const updated = await setFanSeriesPreset(projectId, fanPresetDraft || null);
      setData((prev) => (prev ? { ...prev, project: updated } : prev));
    } catch (ex) {
      setFanPresetErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setFanPresetBusy(false);
    }
  }, [projectId, fanPresetDraft]);

  const onSaveAutopilot = useCallback(async () => {
    if (!projectId) return;
    setAutopilotErr(null);
    setAutopilotBusy(true);
    try {
      const updated = await updateAutopilotSettings(projectId, {
        autopilotMode: autopilotModeDraft,
        autoAcceptPolicy: autoAcceptDraft,
        maxAutoChaptersPerRun: maxAutoRunDraft,
        pauseOnVectorSyncFailed: pauseOnVectorFailDraft,
      });
      setData((prev) => (prev ? { ...prev, project: updated } : prev));
    } catch (ex) {
      setAutopilotErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setAutopilotBusy(false);
    }
  }, [projectId, autopilotModeDraft, autoAcceptDraft, maxAutoRunDraft, pauseOnVectorFailDraft]);

  const onEmergencyStopAutopilot = useCallback(async () => {
    if (!projectId) return;
    setAutopilotErr(null);
    setAutopilotBusy(true);
    try {
      const updated = await emergencyPauseAutopilot(projectId, "ui_emergency_stop");
      setData((prev) => (prev ? { ...prev, project: updated } : prev));
    } catch (ex) {
      setAutopilotErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setAutopilotBusy(false);
    }
  }, [projectId]);

  const onStartAutopilotRun = useCallback(async () => {
    if (!projectId) return;
    setAutopilotErr(null);
    setAutopilotBusy(true);
    try {
      const updated = await startAutopilotRun(projectId);
      setData((prev) => (prev ? { ...prev, project: updated } : prev));
    } catch (ex) {
      setAutopilotErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setAutopilotBusy(false);
    }
  }, [projectId]);

  const onSaveNarrativeDomain = useCallback(async () => {
    if (!projectId) return;
    setNarrativeDomainErr(null);
    setNarrativeDomainBusy(true);
    let parsed: unknown;
    try {
      parsed = JSON.parse(narrativeDomainDraft) as unknown;
      if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
        throw new Error("叙事域须为 JSON 对象（根不能为 null 或数组）");
      }
    } catch (e) {
      setNarrativeDomainErr(e instanceof Error ? e.message : "JSON 无效");
      setNarrativeDomainBusy(false);
      return;
    }
    try {
      const updated = await patchNarrativeDomain(projectId, parsed);
      setData((prev) => (prev ? { ...prev, project: updated } : prev));
    } catch (ex) {
      setNarrativeDomainErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setNarrativeDomainBusy(false);
    }
  }, [projectId, narrativeDomainDraft]);

  useEffect(() => {
    if (!editingSl) return;
    setEditTitle(editingSl.title);
    setEditRole(editingSl.storylineRole ?? "SUB");
    setEditStatus(editingSl.status);
    setEditParent(editingSl.parentStorylineId ?? "");
    setEditEstS(editingSl.estStartChapter != null ? String(editingSl.estStartChapter) : "");
    setEditEstE(editingSl.estEndChapter != null ? String(editingSl.estEndChapter) : "");
    setEditSort(String(editingSl.sortOrder));
    try {
      setEditMilestones(JSON.stringify(editingSl.milestonesJson ?? [], null, 2));
    } catch {
      setEditMilestones("[]");
    }
    setEditCurrentMi(String(editingSl.currentMilestoneIndex));
    setEditLastActive(editingSl.lastActiveChapterNo != null ? String(editingSl.lastActiveChapterNo) : "");
  }, [editingSl]);

  const onCreateNarrativeStoryline = useCallback(async () => {
    if (!projectId) return;
    const key = slNewKey.trim();
    const title = slNewTitle.trim();
    if (!key || !title) {
      setNarrStructErr("故事线 key 与标题必填");
      return;
    }
    let milestonesJson: unknown;
    if (slNewMilestones.trim()) {
      try {
        milestonesJson = JSON.parse(slNewMilestones) as unknown;
      } catch {
        setNarrStructErr("里程碑 JSON 无效");
        return;
      }
    }
    setNarrStructErr(null);
    setNarrStructBusy(true);
    try {
      await createNarrativeStoryline(projectId, {
        storylineKey: key,
        title,
        parentStorylineId: slNewParent.trim() || undefined,
        storylineRole: slNewRole || "SUB",
        status: slNewStatus || "ACTIVE",
        estStartChapter: slNewEstS.trim() === "" ? undefined : Number.parseInt(slNewEstS, 10),
        estEndChapter: slNewEstE.trim() === "" ? undefined : Number.parseInt(slNewEstE, 10),
        milestonesJson,
      });
      setSlNewKey("");
      setSlNewTitle("");
      setSlNewParent("");
      setSlNewRole("SUB");
      setSlNewStatus("ACTIVE");
      setSlNewEstS("");
      setSlNewEstE("");
      setSlNewMilestones("[]");
      const [sl, cf] = await Promise.all([listNarrativeStorylines(projectId), listNarrativeConfluences(projectId)]);
      setNarrStructStorylines(sl);
      setNarrStructConfluences(cf);
    } catch (ex) {
      setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setNarrStructBusy(false);
    }
  }, [projectId, slNewKey, slNewTitle, slNewParent, slNewStatus, slNewEstS, slNewEstE, slNewMilestones]);

  const onSaveEditingStoryline = useCallback(async () => {
    if (!projectId || !editingSl) return;
    let milestonesJson: unknown;
    try {
      milestonesJson = JSON.parse(editMilestones) as unknown;
    } catch {
      setNarrStructErr("里程碑 JSON 无效");
      return;
    }
    const estS = editEstS.trim() === "" ? null : Number.parseInt(editEstS, 10);
    const estE = editEstE.trim() === "" ? null : Number.parseInt(editEstE, 10);
    const la = editLastActive.trim() === "" ? null : Number.parseInt(editLastActive, 10);
    const so = Number.parseInt(editSort, 10);
    const cmi = Number.parseInt(editCurrentMi, 10);
    setNarrStructErr(null);
    setNarrStructBusy(true);
    try {
      await updateNarrativeStoryline(projectId, editingSl.id, {
        storylineKey: editingSl.storylineKey,
        title: editTitle.trim(),
        parentStorylineId: editParent.trim() === "" ? "" : editParent.trim(),
        storylineRole: editRole || "SUB",
        status: editStatus.trim() || "ACTIVE",
        estStartChapter: estS != null && !Number.isNaN(estS) ? estS : null,
        estEndChapter: estE != null && !Number.isNaN(estE) ? estE : null,
        milestonesJson,
        currentMilestoneIndex: Number.isNaN(cmi) ? 0 : Math.max(0, cmi),
        lastActiveChapterNo: la != null && !Number.isNaN(la) ? la : null,
        sortOrder: Number.isNaN(so) ? editingSl.sortOrder : so,
      });
      setEditingSl(null);
      const [sl, cf] = await Promise.all([listNarrativeStorylines(projectId), listNarrativeConfluences(projectId)]);
      setNarrStructStorylines(sl);
      setNarrStructConfluences(cf);
    } catch (ex) {
      setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setNarrStructBusy(false);
    }
  }, [
    projectId,
    editingSl,
    editTitle,
    editParent,
    editStatus,
    editEstS,
    editEstE,
    editMilestones,
    editCurrentMi,
    editLastActive,
    editSort,
  ]);

  const onDeleteNarrativeStoryline = useCallback(
    async (id: string) => {
      if (!projectId) return;
      if (!window.confirm("删除该故事线？将移除引用其的汇合点。")) return;
      setNarrStructErr(null);
      setNarrStructBusy(true);
      try {
        await deleteNarrativeStoryline(projectId, id);
        setEditingSl((cur) => (cur?.id === id ? null : cur));
        const [sl, cf] = await Promise.all([listNarrativeStorylines(projectId), listNarrativeConfluences(projectId)]);
        setNarrStructStorylines(sl);
        setNarrStructConfluences(cf);
      } catch (ex) {
        setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
      } finally {
        setNarrStructBusy(false);
      }
    },
    [projectId]
  );

  const onCreateNarrativeConfluence = useCallback(async () => {
    if (!projectId) return;
    if (!cfPrimary || !cfSecondary || cfPrimary === cfSecondary) {
      setNarrStructErr("请选择两条不同的故事线作为汇合主线与副线");
      return;
    }
    setNarrStructErr(null);
    setNarrStructBusy(true);
    try {
      await createNarrativeConfluence(projectId, {
        primaryStorylineId: cfPrimary,
        secondaryStorylineId: cfSecondary,
        targetChapter: Math.max(1, cfTarget),
        confluenceType: cfType || "intersect",
        notes: cfNotes.trim() || undefined,
        contextSummary: cfContextSummary.trim() || undefined,
        preRevealHint: cfPreRevealHint.trim() || undefined,
        behaviorGuards: parseBehaviorGuards(cfBehaviorGuards),
      });
      setCfNotes("");
      setCfContextSummary("");
      setCfPreRevealHint("");
      setCfBehaviorGuards("[]");
      const [sl, cf] = await Promise.all([listNarrativeStorylines(projectId), listNarrativeConfluences(projectId)]);
      setNarrStructStorylines(sl);
      setNarrStructConfluences(cf);
    } catch (ex) {
      setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setNarrStructBusy(false);
    }
  }, [projectId, cfPrimary, cfSecondary, cfTarget, cfType, cfNotes, cfContextSummary, cfPreRevealHint, cfBehaviorGuards]);

  const onResolveNarrativeConfluence = useCallback(
    async (id: string, resolved: boolean) => {
      if (!projectId) return;
      setNarrStructBusy(true);
      setNarrStructErr(null);
      try {
        await resolveNarrativeConfluence(projectId, id, resolved);
        setNarrStructConfluences(await listNarrativeConfluences(projectId));
      } catch (ex) {
        setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
      } finally {
        setNarrStructBusy(false);
      }
    },
    [projectId]
  );

  const onDeleteNarrativeConfluence = useCallback(
    async (id: string) => {
      if (!projectId) return;
      if (!window.confirm("删除该汇合点？")) return;
      setNarrStructBusy(true);
      setNarrStructErr(null);
      try {
        await deleteNarrativeConfluence(projectId, id);
        setNarrStructConfluences(await listNarrativeConfluences(projectId));
      } catch (ex) {
        setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
      } finally {
        setNarrStructBusy(false);
      }
    },
    [projectId]
  );

  const onPreviewChapterObligations = useCallback(async () => {
    if (!projectId) return;
    setNarrStructErr(null);
    setNarrStructBusy(true);
    try {
      const j = await getChapterObligationsPreview(projectId, Math.max(1, oblPreviewChapter));
      setOblPreviewJson(j);
    } catch (ex) {
      setNarrStructErr(ex instanceof Error ? ex.message : String(ex));
      setOblPreviewJson(null);
    } finally {
      setNarrStructBusy(false);
    }
  }, [projectId, oblPreviewChapter]);

  const onCreateSubtext = useCallback(async () => {
    if (!projectId) return;
    const q = subtextNewQuestion.trim();
    if (!q) {
      setSubtextErr("请填写「读者/POV 短疑问」");
      return;
    }
    let sug: number | undefined;
    if (subtextNewSug.trim() !== "") {
      const n = parseInt(subtextNewSug, 10);
      if (Number.isNaN(n) || n < 1) {
        setSubtextErr("建议回收章须为 ≥1 的整数或留空");
        return;
      }
      sug = n;
    }
    setSubtextErr(null);
    setSubtextBusy(true);
    try {
      await createSubtextLedger(projectId, {
        chapterNo: subtextNewChapterNo,
        question: q,
        characterRef: subtextNewChar.trim() || undefined,
        suggestedResolveChapter: sug,
        importance: subtextNewImp.trim() || "medium",
      });
      setSubtextEntries(await listSubtextLedger(projectId));
      setSubtextNewQuestion("");
    } catch (ex) {
      setSubtextErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setSubtextBusy(false);
    }
  }, [
    projectId,
    subtextNewChapterNo,
    subtextNewQuestion,
    subtextNewChar,
    subtextNewSug,
    subtextNewImp,
  ]);

  const onConsumeSubtext = useCallback(
    async (entryId: string) => {
      if (!projectId) return;
      const raw = window.prompt("于第几章标记该子文本为已消费？", "1");
      if (raw === null) return;
      const ch = parseInt(raw, 10);
      if (Number.isNaN(ch) || ch < 1) {
        window.alert("章号须为 ≥1 的整数");
        return;
      }
      setSubtextErr(null);
      setSubtextBusy(true);
      try {
        await consumeSubtextLedger(projectId, entryId, ch);
        setSubtextEntries(await listSubtextLedger(projectId));
      } catch (ex) {
        setSubtextErr(ex instanceof Error ? ex.message : String(ex));
      } finally {
        setSubtextBusy(false);
      }
    },
    [projectId]
  );

  const onRefreshNarrativeTables = useCallback(async () => {
    if (!projectId) return;
    setSubtextErr(null);
    setMetricsErr(null);
    setNarrStructErr(null);
    try {
      const [st, mt, sl, cf] = await Promise.all([
        listSubtextLedger(projectId),
        listChapterNarrativeMetrics(projectId),
        listNarrativeStorylines(projectId),
        listNarrativeConfluences(projectId),
      ]);
      setSubtextEntries(st);
      setMetricsRows(mt);
      setNarrStructStorylines(sl);
      setNarrStructConfluences(cf);
    } catch (ex) {
      const msg = ex instanceof Error ? ex.message : String(ex);
      setSubtextErr(msg);
      setMetricsErr(msg);
      setNarrStructErr(msg);
    }
  }, [projectId]);

  async function onDeleteProject() {
    if (!projectId) return;
    if (
      !window.confirm(
        "确定删除整个作品？所有章节、题材方案、初始化记录与本地导出文件都会删除，且不可恢复。"
      )
    ) {
      return;
    }
    setDeleteErr(null);
    setDeleteBusy(true);
    try {
      await deleteProject(projectId);
      navigate("/");
    } catch (ex) {
      setDeleteErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setDeleteBusy(false);
    }
  }

  if (!projectId) {
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">缺少项目 ID</p>
      </section>
    );
  }

  if (err) {
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">加载失败：{err}</p>
      </section>
    );
  }

  if (!data) {
    return (
      <section className="mf-page">
        <p className="mf-muted">加载中…</p>
      </section>
    );
  }

  const { project, writerEngine } = data;
  const writerConnected = writerEngine.health.ok && writerEngine.test.ok;

  async function onGenreSubmit(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    setGenreErr(null);
    setGenreBusy(true);
    setGenreStreamLog("");
    try {
      const body = parseGenreForm({
        targetPlatform,
        genderChannel,
        preferredGenresRaw,
        avoidRaw,
        writingStrengthRaw,
        riskPreference,
      });
      await postGenreRecommendStream(projectId, body, (eventName, payload) => {
        if (eventName === "llm_delta") {
          const node = typeof payload.node === "string" ? payload.node : "";
          const text = typeof payload.text === "string" ? payload.text : "";
          setGenreStreamLog((prev) => prev + `[${node}] ${text}`);
        }
        if (eventName === "persisted") {
          const contractId = typeof payload.contractId === "string" ? payload.contractId : "";
          const contract = payload.contract as GenreDecisionContract | undefined;
          if (contractId && contract) {
            setGenreResult({ contractId, contract });
          }
          void refreshWorkspace();
        }
        if (eventName === "error") {
          const msg = typeof payload.message === "string" ? payload.message : JSON.stringify(payload);
          setGenreErr(msg);
        }
      });
    } catch (ex) {
      setGenreErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreBusy(false);
    }
  }

  async function onSkillConfirmedGenreStream(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const hook = (skillDone?.finalSummary ?? "").trim();
    if (!hook) {
      setHookErr("请先完成 Skill 确认对话（模型给出最终故事线摘要后再生成）。");
      return;
    }
    setHookErr(null);
    setHookBusy(true);
    setHookStreamLog("");
    try {
      await postGenreRecommendFromStoryStream(
        projectId,
        {
          storyHook: hook,
          targetPlatform: hookPlatform.trim() || "番茄",
          genderChannel: hookChannel.trim() || "男频",
          riskPreference: hookRisk.trim() || "medium",
          uniqueDirection: true,
        },
        (eventName, payload) => {
          if (eventName === "llm_delta") {
            const node = typeof payload.node === "string" ? payload.node : "";
            const text = typeof payload.text === "string" ? payload.text : "";
            setHookStreamLog((prev) => prev + `[${node}] ${text}`);
          }
          if (eventName === "persisted") {
            const contractId = typeof payload.contractId === "string" ? payload.contractId : "";
            const contract = payload.contract as GenreDecisionContract | undefined;
            if (contractId && contract) {
              setGenreResult({ contractId, contract });
            }
            void refreshWorkspace();
          }
          if (eventName === "error") {
            const msg = typeof payload.message === "string" ? payload.message : JSON.stringify(payload);
            setHookErr(msg);
          }
        }
      );
    } catch (ex) {
      setHookErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setHookBusy(false);
    }
  }

  async function onSkillConversationStart() {
    if (!projectId || !skillPickId.trim()) return;
    setSkillErr(null);
    setSkillBusy(true);
    try {
      const res = await postGenreInterview(projectId, [], { writerSkillId: skillPickId.trim() });
      const assistantTurn: GenreInterviewChatTurn = {
        role: "assistant",
        content: res.replyToUser,
      };
      const opener: GenreInterviewChatTurn = {
        role: "user",
        content: SKILL_INTERVIEW_OPENER,
      };
      setSkillMessages([opener, assistantTurn]);
      if (res.status === "complete") {
        setSkillDone(res);
      } else {
        setSkillDone(null);
      }
    } catch (ex) {
      setSkillErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setSkillBusy(false);
    }
  }

  async function onSkillInterviewSend(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !skillPickId.trim()) return;
    const text = skillInput.trim();
    if (!text) return;
    setSkillErr(null);
    const userTurn: GenreInterviewChatTurn = { role: "user", content: text };
    const history = [...skillMessages, userTurn];
    setSkillMessages(history);
    setSkillInput("");
    setSkillBusy(true);
    try {
      const res = await postGenreInterview(projectId, history, { writerSkillId: skillPickId.trim() });
      const assistantTurn: GenreInterviewChatTurn = {
        role: "assistant",
        content: res.replyToUser,
      };
      setSkillMessages((prev) => [...prev, assistantTurn]);
      if (res.status === "complete") {
        setSkillDone(res);
      }
    } catch (ex) {
      setSkillErr(ex instanceof Error ? ex.message : String(ex));
      setSkillMessages((prev) => prev.slice(0, -1));
    } finally {
      setSkillBusy(false);
    }
  }

  async function onInterviewSend(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const text = interviewInput.trim();
    if (!text) return;
    setInterviewErr(null);
    const userTurn: GenreInterviewChatTurn = { role: "user", content: text };
    const history = [...interviewMessages, userTurn];
    setInterviewMessages(history);
    setInterviewInput("");
    setInterviewBusy(true);
    try {
      const res = await postGenreInterview(projectId, history);
      const assistantTurn: GenreInterviewChatTurn = {
        role: "assistant",
        content: res.replyToUser,
      };
      setInterviewMessages((prev) => [...prev, assistantTurn]);
      if (res.status === "complete") {
        setInterviewDone(res);
      }
    } catch (ex) {
      setInterviewErr(ex instanceof Error ? ex.message : String(ex));
      setInterviewMessages((prev) => prev.slice(0, -1));
    } finally {
      setInterviewBusy(false);
    }
  }

  async function onConfirmGenreSelection(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !pickGenreId) {
      setPickErr("请先在列表中选择一份题材方案。");
      return;
    }
    setPickErr(null);
    setPickBusy(true);
    try {
      await putGenreSelection(projectId, pickGenreId);
      await refreshWorkspace();
    } catch (ex) {
      setPickErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setPickBusy(false);
    }
  }

  async function openGenreModal(contractId: string) {
    if (!projectId) return;
    setGenreModalId(contractId);
    setGenreModalBusy(true);
    setGenreModalErr(null);
    setGenreModalRaw(null);
    try {
      const d = await getGenreContract(projectId, contractId);
      const raw = asRecord(d.rawJson);
      if (!raw) throw new Error("rawJson 不是对象");
      const sd = asRecord(raw.selectedDirection) ?? asRecord(raw.selected_direction) ?? {};
      setGenreModalRaw(JSON.parse(JSON.stringify(raw)) as Record<string, unknown>);
      setGenreModalHook(strField(raw, "recommendedCoreHook", "recommended_core_hook"));
      setGenreModalGenre(strField(sd, "genre"));
      setGenreModalChannel(strField(sd, "channel"));
      setGenreModalReason(strField(sd, "reason"));
      setGenreModalSubTags(subTagsToComma(sd));
      setGenreModalCandidates(candidatesFromRaw(raw).map((c) => ({ ...c })));
      setGenreModalRiskText(riskNotesFromRaw(raw).join("\n"));
    } catch (ex) {
      setGenreModalErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreModalBusy(false);
    }
  }

  function closeGenreModal() {
    setGenreModalId(null);
    setGenreModalBusy(false);
    setGenreModalSaveBusy(false);
    setGenreModalErr(null);
    setGenreModalRaw(null);
  }

  async function saveGenreModal() {
    if (!projectId || !genreModalId || !genreModalRaw) return;
    setGenreModalErr(null);
    const next = JSON.parse(JSON.stringify(genreModalRaw)) as Record<string, unknown>;
    const subTags = genreModalSubTags
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter(Boolean);
    const sd: Record<string, unknown> = {
      ...(asRecord(next.selectedDirection) ?? asRecord(next.selected_direction) ?? {}),
    };
    sd.genre = genreModalGenre;
    sd.channel = genreModalChannel;
    sd.reason = genreModalReason;
    sd.subTags = subTags;
    next.selectedDirection = sd;
    next.selected_direction = sd;
    next.recommendedCoreHook = genreModalHook;
    next.recommended_core_hook = genreModalHook;
    next.candidateRankings = genreModalCandidates;
    next.candidate_rankings = genreModalCandidates;
    const rn = genreModalRiskText
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean);
    next.riskNotes = rn;
    next.risk_notes = rn;
    setGenreModalSaveBusy(true);
    try {
      await putGenreContract(projectId, genreModalId, { rawJson: next });
      await refreshWorkspace();
      closeGenreModal();
    } catch (ex) {
      setGenreModalErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreModalSaveBusy(false);
    }
  }

  async function onDeleteGenreContractRow(contractId: string) {
    if (!projectId) return;
    if (!window.confirm(`确定删除该题材方案？删除后不可恢复。\nID：${contractId}`)) return;
    try {
      await deleteGenreContract(projectId, contractId);
      if (pickGenreId === contractId) setPickGenreId(null);
      await refreshWorkspace();
    } catch (ex) {
      window.alert(ex instanceof Error ? ex.message : String(ex));
    }
  }

  const contract: GenreDecisionContract | undefined = genreResult?.contract;

  const sourceLabel = (s: string) => {
    if (s === "story_hook") return "故事线";
    if (s === "skill_unique") return "Skill 唯一";
    return "偏好";
  };

  return (
    <section className="mf-page mf-prose">
      <Link to="/" className="mf-back">
        ← 返回作品列表
      </Link>
      <h1 className="mf-page-title">{project.name}</h1>
      <p className="mf-page-lede">
        {setupStatus?.setupLocked
          ? "本书创作进行中：在工作台继续写章；创作设定仅可查看，不可重新选题材。"
          : "建议从「创作向导」完成题材、契约与故事结构；本页可微调与高级设置。"}
      </p>
      <ProjectWorkflowBanner projectId={projectId} status={setupStatus} />

      <div
        style={{ display: "flex", gap: 8, marginBottom: 20, flexWrap: "wrap" }}
        role="tablist"
        aria-label="项目主页分区"
      >
        {(
          [
            ["guide", "工作台"],
            ["structure", "故事结构"],
            ["genre", "题材"],
            ["more", "更多"],
          ] as const
        ).map(([id, label]) => (
          <button
            key={id}
            type="button"
            role="tab"
            aria-selected={hubTab === id}
            className={hubTab === id ? "mf-btn mf-btn-primary" : "mf-btn"}
            onClick={() => setHubTab(id)}
          >
            {label}
          </button>
        ))}
      </div>

      {hubTab === "guide" ? (
      <>
      <div className="mf-hub-grid" style={{ marginBottom: 24 }}>
        {setupStatus?.setupLocked ? (
          <>
            <Link
              to={`/projects/${encodeURIComponent(projectId)}/chapters/${setupStatus.resumeChapterNo || project.currentChapter || 1}/workspace`}
              className="mf-hub-card mf-hub-card-primary"
            >
              <strong>继续写作</strong>
              <span className="mf-hub-card-sub">
                第 {setupStatus.resumeChapterNo || project.currentChapter || 1} 章 · 已定稿 {setupStatus.acceptedChapterCount} 章
              </span>
            </Link>
            <Link to={`/projects/${encodeURIComponent(projectId)}/setup`} className="mf-hub-card">
              <strong>查看创作设定</strong>
              <span className="mf-hub-card-sub">题材、故事契约与结构（只读）</span>
            </Link>
          </>
        ) : (
          <>
            <Link to={`/projects/${encodeURIComponent(projectId)}/setup`} className="mf-hub-card">
              <strong>① 创作向导</strong>
              <span className="mf-hub-card-sub">题材 → 故事（含 20 章契约）→ 故事结构</span>
            </Link>
            <Link
              to={`/projects/${encodeURIComponent(projectId)}/chapters/${setupStatus?.readyToWrite ? (setupStatus.resumeChapterNo || 1) : 1}/workspace`}
              className={`mf-hub-card ${setupStatus?.readyToWrite ? "mf-hub-card-primary" : ""}${setupStatus?.readyToWrite ? "" : " mf-hub-card-muted"}`}
              onClick={(e) => {
                if (setupStatus && !setupStatus.readyToWrite) e.preventDefault();
              }}
              aria-disabled={setupStatus != null && !setupStatus.readyToWrite}
            >
              <strong>② 章节写作</strong>
              <span className="mf-hub-card-sub">
                {setupStatus?.readyToWrite
                  ? `从第 ${setupStatus.resumeChapterNo || 1} 章开始生成与定稿`
                  : "请先完成创作向导"}
              </span>
            </Link>
          </>
        )}
        <Link to={`/projects/${encodeURIComponent(projectId)}/workflow`} className="mf-hub-card">
          <strong>章节流水线 DAG</strong>
          <span className="mf-hub-card-sub">拖拽编排生成节点，保存到本书</span>
        </Link>
        <Link to={`/projects/${encodeURIComponent(projectId)}/graph`} className="mf-hub-card">
          <strong>人物与世界观</strong>
          <span className="mf-hub-card-sub">已定稿章节整理出的人物与关系</span>
        </Link>
        <Link to={`/projects/${encodeURIComponent(projectId)}/roi`} className="mf-hub-card">
          <strong>用量统计</strong>
          <span className="mf-hub-card-sub">各章写作消耗的估算用量</span>
        </Link>
        <Link to={`/projects/${encodeURIComponent(projectId)}/governance`} className="mf-hub-card">
          <strong>写作治理</strong>
          <span className="mf-hub-card-sub">作者意图、硬约束、风格与全书替换</span>
        </Link>
        <a
          href={`/api/projects/${encodeURIComponent(projectId)}/export/accepted-book.md`}
          target="_blank"
          rel="noreferrer"
          className="mf-hub-card"
        >
          <strong>导出全书</strong>
          <span className="mf-hub-card-sub">合并已定稿章节为一份文稿</span>
        </a>
      </div>

      <div className="mf-panel mf-panel-warn" style={{ marginBottom: 24, fontSize: 14 }}>
        <strong style={{ color: "#9a3412" }}>危险操作</strong>
        <p style={{ margin: "8px 0 10px", color: "#57534e" }}>
          删除作品后无法恢复，请确认已备份重要内容。
        </p>
        {deleteErr && (
          <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
            {deleteErr}
          </p>
        )}
        <button
          type="button"
          disabled={deleteBusy}
          onClick={() => void onDeleteProject()}
          className="mf-btn mf-btn-danger"
        >
          {deleteBusy ? "删除中…" : "删除整个作品"}
        </button>
      </div>

      <h2 className="mf-section-title">作品信息</h2>
      <dl className="mf-dl">
        <dt>ID</dt>
        <dd style={{ margin: 0 }}>{project.id}</dd>
        <dt>名称</dt>
        <dd style={{ margin: 0 }}>{project.name}</dd>
        <dt>语言</dt>
        <dd style={{ margin: 0 }}>{project.language}</dd>
        <dt>目标章节</dt>
        <dd style={{ margin: 0 }}>{project.targetChapters}</dd>
        <dt>当前章节</dt>
        <dd style={{ margin: 0 }}>{project.currentChapter}</dd>
        <dt>状态</dt>
        <dd style={{ margin: 0 }}>{project.status}</dd>
        <dt>创建时间</dt>
        <dd style={{ margin: 0 }}>{project.createdAt}</dd>
        <dt>更新时间</dt>
        <dd style={{ margin: 0 }}>{project.updatedAt}</dd>
        <dt>全书叙事阶段</dt>
        <dd style={{ margin: 0 }}>{labelStoryPhase(project.narrativePhase)}</dd>
        <dt>自动驾驶</dt>
        <dd style={{ margin: 0 }}>
          {labelAutopilotMode(project.autopilotMode)}
          {" · "}
          {labelAutoAcceptPolicy(project.autoAcceptPolicy)}
          {project.autopilotPaused ? " · 已暂停" : ""}
        </dd>
        {project.autopilotPauseReason ? (
          <>
            <dt>暂停原因</dt>
            <dd style={{ margin: 0, whiteSpace: "pre-wrap" }}>{project.autopilotPauseReason}</dd>
          </>
        ) : null}
        <dt>本 run 已自动排队章数</dt>
        <dd style={{ margin: 0 }}>
          {project.autopilotChaptersThisRun ?? 0} / {project.maxAutoChaptersPerRun ?? 20}
        </dd>
      </dl>

      <h3 className="mf-subsection-title">全书自动驾驶</h3>
      <p className="mf-muted mf-text-sm" style={{ maxWidth: 640, marginTop: 0 }}>
        <strong>手动</strong>：只由您操作每一章。<strong>自动排队写下一章</strong>：定稿后自动开始写下一章，但不会自动替您点「接受」。<strong>
          全自动
        </strong>
        ：在下方「自动定稿」条件满足时，系统可自动接受章节并继续。自动定稿可选「审查通过」「审查+章后指标」「审查+叙事任务落实」等档位。
      </p>
      {autopilotErr && (
        <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
          {autopilotErr}
        </p>
      )}
      {project.autopilotLastActionJson != null && (
        <pre className="mf-pre" style={{ fontSize: 12, marginBottom: 10 }}>
          {JSON.stringify(project.autopilotLastActionJson, null, 2)}
        </pre>
      )}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "center", marginBottom: 10 }}>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 14 }}>
          模式
          <select
            className="mf-select mf-select-inline"
            value={autopilotModeDraft}
            onChange={(e) => setAutopilotModeDraft(e.target.value)}
          >
            {AUTOPILOT_MODE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 14 }}>
          自动定稿条件
          <select
            className="mf-select mf-select-inline"
            value={autoAcceptDraft}
            onChange={(e) => setAutoAcceptDraft(e.target.value)}
          >
            {AUTO_ACCEPT_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 6, fontSize: 14 }}>
          每 run 上限
          <input
            type="number"
            min={1}
            max={500}
            value={maxAutoRunDraft}
            onChange={(e) => setMaxAutoRunDraft(Number(e.target.value) || 1)}
            style={{ width: 72 }}
          />
        </label>
        <label style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 14 }}>
          <input
            type="checkbox"
            checked={pauseOnVectorFailDraft}
            onChange={(e) => setPauseOnVectorFailDraft(e.target.checked)}
          />
          向量同步失败时暂停
        </label>
      </div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 16 }}>
        <button type="button" className="mf-btn" disabled={autopilotBusy} onClick={() => void onSaveAutopilot()}>
          {autopilotBusy ? "处理中…" : "保存自动驾驶设置"}
        </button>
        <button type="button" className="mf-btn" disabled={autopilotBusy} onClick={() => void onStartAutopilotRun()}>
          重置本 run 计数并继续
        </button>
        <button
          type="button"
          className="mf-btn mf-btn-danger"
          disabled={autopilotBusy}
          onClick={() => void onEmergencyStopAutopilot()}
        >
          紧急全停
        </button>
      </div>
      </>
      ) : null}

      {hubTab === "structure" ? (
      <>
      <h3 className="mf-subsection-title">故事结构</h3>
      <p className="mf-muted mf-text-sm" style={{ maxWidth: 720, marginTop: 0 }}>
        在这里规划主线、支线、暗线与「在哪一章汇合」。保存后，系统会在每章动笔前生成「本章要写什么」的清单；章节定稿后也会同步到人物与伏笔手册。
      </p>
      <div style={{ display: "flex", gap: 8, marginBottom: 14, flexWrap: "wrap", alignItems: "center" }}>
        <button
          type="button"
          className={narrativeUiTab === "structure" ? "mf-btn mf-btn-primary" : "mf-btn"}
          disabled={narrStructBusy}
          onClick={() => setNarrativeUiTab("structure")}
        >
          故事线编辑
        </button>
        <button
          type="button"
          className={narrativeUiTab === "advanced" ? "mf-btn mf-btn-primary" : "mf-btn"}
          disabled={narrativeDomainBusy}
          onClick={() => setNarrativeUiTab("advanced")}
        >
          备份与导入（高级）
        </button>
        <button type="button" className="mf-btn" disabled={narrStructBusy || !projectId} onClick={() => void onRefreshNarrativeTables()}>
          刷新叙事数据
        </button>
        <button
          type="button"
          className="mf-btn"
          disabled={!projectId}
          onClick={() => {
            if (!projectId) return;
            void validateNarrativeStructure(projectId)
              .then((r) => {
                const n = r.errors?.length ?? 0;
                const w = r.warnings?.length ?? 0;
                window.alert(n === 0 ? `校验通过（${w} 条警告）` : `校验：${n} 错误，${w} 警告`);
              })
              .catch((e) => window.alert(e instanceof Error ? e.message : String(e)));
          }}
        >
          校验结构
        </button>
        <button
          type="button"
          className="mf-btn"
          disabled={!projectId}
          onClick={() => {
            if (!projectId) return;
            void exportNarrativeDomainFromPg(projectId)
              .then((j) => {
                void navigator.clipboard.writeText(JSON.stringify(j, null, 2));
                window.alert("已导出故事结构备份并复制到剪贴板");
              })
              .catch((e) => window.alert(e instanceof Error ? e.message : String(e)));
          }}
        >
          导出结构备份
        </button>
      </div>

      {narrativeUiTab === "structure" ? (
        <>
          {narrStructErr ? (
            <p className="mf-alert mf-alert-error" style={{ marginBottom: 10 }}>
              {narrStructErr}
            </p>
          ) : null}

          <div className="mf-card mf-card-pad" style={{ marginBottom: 16 }}>
            <h4 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>故事线</h4>
            <div
              style={{
                display: "grid",
                gap: 10,
                gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))",
                marginBottom: 12,
                alignItems: "end",
              }}
            >
              <label style={{ fontSize: 13 }}>
                内部代号（可选）
                <input
                  value={slNewKey}
                  onChange={(e) => setSlNewKey(e.target.value)}
                  placeholder="如 main，留空可自动生成"
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                />
              </label>
              <label style={{ fontSize: 13, gridColumn: "span 2" }}>
                标题
                <input
                  value={slNewTitle}
                  onChange={(e) => setSlNewTitle(e.target.value)}
                  placeholder="主线名"
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                />
              </label>
              <label style={{ fontSize: 13 }}>
                挂在哪条故事线下（可选）
                <select
                  className="mf-select mf-select-inline"
                  value={slNewParent}
                  onChange={(e) => setSlNewParent(e.target.value)}
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                >
                  <option value="">无（独立一条线）</option>
                  {narrStructStorylines.map((s) => (
                    <option key={s.id} value={s.id}>
                      {storylinePickLabel(s)}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ fontSize: 13 }}>
                线型
                <select
                  className="mf-select mf-select-inline"
                  value={slNewRole}
                  onChange={(e) => setSlNewRole(e.target.value)}
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                >
                  {STORYLINE_ROLE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ fontSize: 13 }}>
                状态
                <select
                  className="mf-select mf-select-inline"
                  value={slNewStatus}
                  onChange={(e) => setSlNewStatus(e.target.value)}
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                >
                  {STORYLINE_STATUS_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ fontSize: 13 }}>
                预计从第几章开始
                <input
                  value={slNewEstS}
                  onChange={(e) => setSlNewEstS(e.target.value)}
                  placeholder="可留空"
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                />
              </label>
              <label style={{ fontSize: 13 }}>
                预计至第几章结束
                <input
                  value={slNewEstE}
                  onChange={(e) => setSlNewEstE(e.target.value)}
                  placeholder="可留空"
                  style={{ display: "block", width: "100%", marginTop: 4 }}
                />
              </label>
            </div>
            <label style={{ fontSize: 13, display: "block", marginBottom: 10 }}>
              里程碑（高级：JSON，可留空 []）
              <textarea
                className="mf-pre"
                spellCheck={false}
                value={slNewMilestones}
                onChange={(e) => setSlNewMilestones(e.target.value)}
                rows={3}
                style={{ width: "100%", marginTop: 4, fontSize: 12 }}
              />
            </label>
            <button type="button" className="mf-btn" disabled={narrStructBusy} onClick={() => void onCreateNarrativeStoryline()}>
              添加故事线
            </button>

            <div style={{ overflowX: "auto", marginTop: 14 }}>
              <table style={{ borderCollapse: "collapse", fontSize: 13, width: "100%" }}>
                <thead>
                  <tr style={{ textAlign: "left", borderBottom: "1px solid var(--mf-border)" }}>
                    <th style={{ padding: "6px 8px" }}>标题</th>
                    <th style={{ padding: "6px 8px" }}>线型</th>
                    <th style={{ padding: "6px 8px" }}>状态</th>
                    <th style={{ padding: "6px 8px" }}>预计章节</th>
                    <th style={{ padding: "6px 8px" }} />
                  </tr>
                </thead>
                <tbody>
                  {narrStructStorylines.length === 0 ? (
                    <tr>
                      <td colSpan={5} style={{ padding: 10, color: "var(--mf-muted)" }}>
                        暂无故事线
                      </td>
                    </tr>
                  ) : (
                    narrStructStorylines.map((row) => (
                      <tr key={row.id} style={{ borderBottom: "1px solid var(--mf-border)" }}>
                        <td style={{ padding: "6px 8px", maxWidth: 220 }}>{row.title}</td>
                        <td style={{ padding: "6px 8px" }}>{labelStorylineRole(row.storylineRole)}</td>
                        <td style={{ padding: "6px 8px" }}>{labelStorylineStatus(row.status)}</td>
                        <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>
                          {row.estStartChapter ?? "—"}–{row.estEndChapter ?? "—"}
                        </td>
                        <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>
                          <button type="button" className="mf-btn" style={{ fontSize: 12, padding: "2px 8px" }} disabled={narrStructBusy} onClick={() => setEditingSl(row)}>
                            编辑
                          </button>{" "}
                          <button
                            type="button"
                            className="mf-btn mf-btn-danger"
                            style={{ fontSize: 12, padding: "2px 8px" }}
                            disabled={narrStructBusy}
                            onClick={() => void onDeleteNarrativeStoryline(row.id)}
                          >
                            删
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {editingSl ? (
              <div className="mf-panel" style={{ marginTop: 14 }}>
                <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 10 }}>编辑「{editingSl.title || editingSl.storylineKey}」</div>
                <div style={{ display: "grid", gap: 10, gridTemplateColumns: "repeat(auto-fill, minmax(160px, 1fr))" }}>
                  <label style={{ fontSize: 13 }}>
                    标题
                    <input value={editTitle} onChange={(e) => setEditTitle(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
                  </label>
                  <label style={{ fontSize: 13 }}>
                    线型
                    <select className="mf-select mf-select-inline" value={editRole} onChange={(e) => setEditRole(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }}>
                      {STORYLINE_ROLE_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label style={{ fontSize: 13 }}>
                    状态
                    <select className="mf-select mf-select-inline" value={editStatus} onChange={(e) => setEditStatus(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }}>
                      {STORYLINE_STATUS_OPTIONS.map((o) => (
                        <option key={o.value} value={o.value}>
                          {o.label}
                        </option>
                      ))}
                    </select>
                  </label>
                  <label style={{ fontSize: 13 }}>
                    挂在哪条故事线下（留空=独立）
                    <select
                      className="mf-select mf-select-inline"
                      value={editParent}
                      onChange={(e) => setEditParent(e.target.value)}
                      style={{ display: "block", width: "100%", marginTop: 4 }}
                    >
                      <option value="">无</option>
                      {narrStructStorylines
                        .filter((s) => s.id !== editingSl.id)
                        .map((s) => (
                          <option key={s.id} value={s.id}>
                            {storylinePickLabel(s)}
                          </option>
                        ))}
                    </select>
                  </label>
                  <label style={{ fontSize: 13 }}>
                    预计从第几章
                    <input value={editEstS} onChange={(e) => setEditEstS(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
                  </label>
                  <label style={{ fontSize: 13 }}>
                    预计至第几章
                    <input value={editEstE} onChange={(e) => setEditEstE(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
                  </label>
                  <label style={{ fontSize: 13 }}>
                    排序权重
                    <input value={editSort} onChange={(e) => setEditSort(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
                  </label>
                  <label style={{ fontSize: 13 }}>
                    当前里程碑序号
                    <input value={editCurrentMi} onChange={(e) => setEditCurrentMi(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
                  </label>
                  <label style={{ fontSize: 13 }}>
                    最近活跃章
                    <input value={editLastActive} onChange={(e) => setEditLastActive(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
                  </label>
                </div>
                <label style={{ fontSize: 13, display: "block", marginTop: 10 }}>
                  里程碑（高级：JSON）
                  <textarea className="mf-pre" spellCheck={false} value={editMilestones} onChange={(e) => setEditMilestones(e.target.value)} rows={4} style={{ width: "100%", marginTop: 4, fontSize: 12 }} />
                </label>
                <div style={{ display: "flex", gap: 8, marginTop: 10 }}>
                  <button type="button" className="mf-btn mf-btn-primary" disabled={narrStructBusy} onClick={() => void onSaveEditingStoryline()}>
                    保存
                  </button>
                  <button type="button" className="mf-btn" disabled={narrStructBusy} onClick={() => setEditingSl(null)}>
                    取消
                  </button>
                </div>
              </div>
            ) : null}
          </div>

          <div className="mf-card mf-card-pad" style={{ marginBottom: 16 }}>
            <h4 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>汇合点</h4>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "flex-end", marginBottom: 12 }}>
              <label style={{ fontSize: 13 }}>
                主线
                <select className="mf-select mf-select-inline" value={cfPrimary} onChange={(e) => setCfPrimary(e.target.value)} style={{ display: "block", minWidth: 200, marginTop: 4 }}>
                  <option value="">选择…</option>
                  {narrStructStorylines.map((s) => (
                    <option key={s.id} value={s.id}>
                      {storylinePickLabel(s)}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ fontSize: 13 }}>
                副线
                <select className="mf-select mf-select-inline" value={cfSecondary} onChange={(e) => setCfSecondary(e.target.value)} style={{ display: "block", minWidth: 200, marginTop: 4 }}>
                  <option value="">选择…</option>
                  {narrStructStorylines.map((s) => (
                    <option key={s.id} value={s.id}>
                      {storylinePickLabel(s)}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ fontSize: 13 }}>
                目标章
                <input type="number" min={1} value={cfTarget} onChange={(e) => setCfTarget(Math.max(1, parseInt(e.target.value, 10) || 1))} style={{ display: "block", width: 72, marginTop: 4 }} />
              </label>
              <label style={{ fontSize: 13 }}>
                汇合类型
                <select
                  className="mf-select mf-select-inline"
                  value={cfType}
                  onChange={(e) => setCfType(e.target.value)}
                  style={{ display: "block", minWidth: 200, marginTop: 4 }}
                >
                  {CONFLUENCE_TYPE_OPTIONS.map((o) => (
                    <option key={o.value} value={o.value}>
                      {o.label}
                    </option>
                  ))}
                </select>
              </label>
              <label style={{ fontSize: 13, flex: "1 1 160px" }}>
                备注
                <input value={cfNotes} onChange={(e) => setCfNotes(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
              </label>
              <label style={{ fontSize: 13, flex: "1 1 200px" }}>
                汇合摘要
                <input value={cfContextSummary} onChange={(e) => setCfContextSummary(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
              </label>
              <label style={{ fontSize: 13, flex: "1 1 140px" }}>
                揭露前提示（暗线揭晓用）
                <input value={cfPreRevealHint} onChange={(e) => setCfPreRevealHint(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }} />
              </label>
              <label style={{ fontSize: 13, display: "block", width: "100%" }}>
                写作禁忌（高级：JSON 数组，可留空 []）
                <input value={cfBehaviorGuards} onChange={(e) => setCfBehaviorGuards(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4, fontFamily: "monospace" }} />
              </label>
              <button type="button" className="mf-btn" disabled={narrStructBusy} onClick={() => void onCreateNarrativeConfluence()}>
                添加汇合
              </button>
            </div>
            <div style={{ overflowX: "auto" }}>
              <table style={{ borderCollapse: "collapse", fontSize: 13, width: "100%" }}>
                <thead>
                  <tr style={{ textAlign: "left", borderBottom: "1px solid var(--mf-border)" }}>
                    <th style={{ padding: "6px 8px" }}>目标章</th>
                    <th style={{ padding: "6px 8px" }}>类型</th>
                    <th style={{ padding: "6px 8px" }}>主线</th>
                    <th style={{ padding: "6px 8px" }}>副线</th>
                    <th style={{ padding: "6px 8px" }}>状态</th>
                    <th style={{ padding: "6px 8px" }} />
                  </tr>
                </thead>
                <tbody>
                  {narrStructConfluences.length === 0 ? (
                    <tr>
                      <td colSpan={6} style={{ padding: 10, color: "var(--mf-muted)" }}>
                        暂无汇合点
                      </td>
                    </tr>
                  ) : (
                    narrStructConfluences.map((c) => (
                      <tr key={c.id} style={{ borderBottom: "1px solid var(--mf-border)" }}>
                        <td style={{ padding: "6px 8px" }}>{c.targetChapter}</td>
                        <td style={{ padding: "6px 8px" }}>{labelConfluenceType(c.confluenceType)}</td>
                        <td style={{ padding: "6px 8px", fontSize: 12 }}>{storylineLabelById(narrStructStorylines, c.primaryStorylineId)}</td>
                        <td style={{ padding: "6px 8px", fontSize: 12 }}>{storylineLabelById(narrStructStorylines, c.secondaryStorylineId)}</td>
                        <td style={{ padding: "6px 8px" }}>{labelConfluenceResolved(c.resolved)}</td>
                        <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>
                          {!c.resolved ? (
                            <button type="button" className="mf-btn" style={{ fontSize: 12, padding: "2px 8px" }} disabled={narrStructBusy} onClick={() => void onResolveNarrativeConfluence(c.id, true)}>
                              标为已兑现
                            </button>
                          ) : (
                            <button type="button" className="mf-btn" style={{ fontSize: 12, padding: "2px 8px" }} disabled={narrStructBusy} onClick={() => void onResolveNarrativeConfluence(c.id, false)}>
                              恢复未兑现
                            </button>
                          )}{" "}
                          <button type="button" className="mf-btn mf-btn-danger" style={{ fontSize: 12, padding: "2px 8px" }} disabled={narrStructBusy} onClick={() => void onDeleteNarrativeConfluence(c.id)}>
                            删
                          </button>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="mf-card mf-card-pad" style={{ marginBottom: 16 }}>
            <h4 style={{ margin: "0 0 12px", fontSize: 15, fontWeight: 600 }}>本章任务单预览</h4>
            <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "center", marginBottom: 10 }}>
              <label style={{ fontSize: 13 }}>
                章号
                <input type="number" min={1} value={oblPreviewChapter} onChange={(e) => setOblPreviewChapter(Math.max(1, parseInt(e.target.value, 10) || 1))} style={{ display: "block", width: 72, marginTop: 4 }} />
              </label>
              <button type="button" className="mf-btn" disabled={narrStructBusy} onClick={() => void onPreviewChapterObligations()}>
                拉取预览
              </button>
            </div>
            {oblPreviewJson != null ? (
              <pre className="mf-pre" style={{ fontSize: 12, maxHeight: 360, overflow: "auto", margin: 0 }}>
                {JSON.stringify(oblPreviewJson, null, 2)}
              </pre>
            ) : (
              <p className="mf-muted mf-text-sm" style={{ margin: 0 }}>
                点击「拉取预览」可查看该章应推进的故事线、到期汇合点等（下方为完整数据）。
              </p>
            )}
          </div>
        </>
      ) : (
        <>
          <p className="mf-muted mf-text-sm" style={{ maxWidth: 720, marginTop: 0 }}>
            供高级用户备份/迁移故事结构。日常请在「故事线编辑」里操作；此处保存会把 JSON <strong>导入</strong>到上方表格，在表格里改也会同步到下方。
          </p>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 10 }}>
            <button
              type="button"
              className="mf-btn"
              disabled={narrativeDomainBusy || !projectId}
              onClick={() => {
                if (!projectId) return;
                void syncNarrativeDomainJson(projectId)
                  .then((j) => {
                    setNarrativeDomainDraft(JSON.stringify(j, null, 2));
                    window.alert("已从故事结构同步到下方 JSON");
                  })
                  .catch((e) => window.alert(e instanceof Error ? e.message : String(e)));
              }}
            >
              从故事结构同步到 JSON
            </button>
            <button
              type="button"
              className="mf-btn"
              disabled={narrativeDomainBusy || !projectId}
              onClick={() => {
                if (!projectId) return;
                let parsed: unknown;
                try {
                  parsed = JSON.parse(narrativeDomainDraft);
                } catch (e) {
                  window.alert(e instanceof Error ? e.message : "JSON 无效");
                  return;
                }
                void importNarrativeFromDomainJson(projectId, parsed)
                  .then((r) => {
                    window.alert(`已导入：故事线 ${r.storylinesUpserted} 条，汇合点 ${r.confluencesCreated} 个`);
                    void onRefreshNarrativeTables();
                  })
                  .catch((e) => window.alert(e instanceof Error ? e.message : String(e)));
              }}
            >
              仅导入故事结构（不改书名等其它字段）
            </button>
          </div>
          {narrativeDomainErr && (
            <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
              {narrativeDomainErr}
            </p>
          )}
          <textarea
            className="mf-pre"
            spellCheck={false}
            value={narrativeDomainDraft}
            onChange={(e) => setNarrativeDomainDraft(e.target.value)}
            rows={12}
            style={{ width: "100%", maxWidth: 900, fontFamily: "monospace", fontSize: 13, marginBottom: 8 }}
          />
          <button type="button" className="mf-btn" disabled={narrativeDomainBusy} onClick={() => void onSaveNarrativeDomain()}>
            {narrativeDomainBusy ? "保存中…" : "保存叙事域"}
          </button>
        </>
      )}

      <h3 className="mf-subsection-title" style={{ marginTop: 28 }}>
        读者悬念钩
      </h3>
      <p className="mf-muted mf-text-sm" style={{ maxWidth: 720, marginTop: 0 }}>
        短悬念/内心钩子，与滚动摘要解耦；建议回收章不得早于埋设章。
      </p>
      {subtextErr && (
        <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
          {subtextErr}
        </p>
      )}
      <div style={{ display: "flex", flexWrap: "wrap", gap: 10, alignItems: "flex-end", marginBottom: 10 }}>
        <label style={{ fontSize: 14 }}>
          埋设章
          <input
            type="number"
            min={1}
            value={subtextNewChapterNo}
            onChange={(e) => setSubtextNewChapterNo(Math.max(1, parseInt(e.target.value, 10) || 1))}
            style={{ display: "block", width: 72, marginTop: 4 }}
          />
        </label>
        <label style={{ fontSize: 14, flex: "1 1 200px" }}>
          疑问
          <input
            value={subtextNewQuestion}
            onChange={(e) => setSubtextNewQuestion(e.target.value)}
            placeholder="读者此刻的短疑问…"
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <label style={{ fontSize: 14 }}>
          人物（可选）
          <input
            value={subtextNewChar}
            onChange={(e) => setSubtextNewChar(e.target.value)}
            style={{ display: "block", width: 120, marginTop: 4 }}
          />
        </label>
        <label style={{ fontSize: 14 }}>
          建议回收章
          <input
            value={subtextNewSug}
            onChange={(e) => setSubtextNewSug(e.target.value)}
            placeholder="空"
            style={{ display: "block", width: 72, marginTop: 4 }}
          />
        </label>
        <label style={{ fontSize: 14 }}>
          重要度
          <select
            className="mf-select mf-select-inline"
            value={subtextNewImp}
            onChange={(e) => setSubtextNewImp(e.target.value)}
            style={{ display: "block", marginTop: 4 }}
          >
            {SUBTEXT_IMPORTANCE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        </label>
        <button type="button" className="mf-btn" disabled={subtextBusy} onClick={() => void onCreateSubtext()}>
          添加条目
        </button>
        <button type="button" className="mf-btn" disabled={subtextBusy} onClick={() => void onRefreshNarrativeTables()}>
          刷新列表
        </button>
      </div>
      <div style={{ overflowX: "auto", marginBottom: 20 }}>
        <table style={{ borderCollapse: "collapse", fontSize: 13, minWidth: 560 }}>
          <thead>
            <tr style={{ textAlign: "left", borderBottom: "1px solid #cbd5e1" }}>
              <th style={{ padding: "6px 8px" }}>章</th>
              <th style={{ padding: "6px 8px" }}>状态</th>
              <th style={{ padding: "6px 8px" }}>疑问</th>
              <th style={{ padding: "6px 8px" }}>建议回收</th>
              <th style={{ padding: "6px 8px" }} />
            </tr>
          </thead>
          <tbody>
            {subtextEntries.length === 0 ? (
              <tr>
                <td colSpan={5} style={{ padding: 8, color: "#64748b" }}>
                  暂无条目
                </td>
              </tr>
            ) : (
              subtextEntries.map((row) => (
                <tr key={row.id} style={{ borderBottom: "1px solid #e2e8f0" }}>
                  <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>{row.chapterNo}</td>
                  <td style={{ padding: "6px 8px" }}>{labelSubtextStatus(row.status)}</td>
                  <td style={{ padding: "6px 8px", maxWidth: 360 }}>{row.question}</td>
                  <td style={{ padding: "6px 8px" }}>{row.suggestedResolveChapter ?? "—"}</td>
                  <td style={{ padding: "6px 8px" }}>
                    {row.status === "pending" ? (
                      <button
                        type="button"
                        className="mf-btn"
                        style={{ fontSize: 12, padding: "2px 8px" }}
                        disabled={subtextBusy}
                        onClick={() => void onConsumeSubtext(row.id)}
                      >
                        标记已回收
                      </button>
                    ) : (
                      <span className="mf-muted">—</span>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <h3 className="mf-subsection-title">章后叙事指标</h3>
      <p className="mf-muted mf-text-sm" style={{ maxWidth: 720, marginTop: 0 }}>
        每章定稿并接受后由系统异步写入；用于「审查通过且章后指标已生成」等自动定稿条件。
      </p>
      {metricsErr && (
        <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
          {metricsErr}
        </p>
      )}
      <div style={{ overflowX: "auto", marginBottom: 24 }}>
        <table style={{ borderCollapse: "collapse", fontSize: 13, minWidth: 480 }}>
          <thead>
            <tr style={{ textAlign: "left", borderBottom: "1px solid #cbd5e1" }}>
              <th style={{ padding: "6px 8px" }}>章</th>
              <th style={{ padding: "6px 8px" }}>张力</th>
              <th style={{ padding: "6px 8px" }}>文风相似度</th>
              <th style={{ padding: "6px 8px" }}>时间</th>
            </tr>
          </thead>
          <tbody>
            {metricsRows.length === 0 ? (
              <tr>
                <td colSpan={4} style={{ padding: 8, color: "#64748b" }}>
                  暂无记录（定稿并接受章节后会出现）
                </td>
              </tr>
            ) : (
              metricsRows.map((row) => (
                <tr key={row.id} style={{ borderBottom: "1px solid #e2e8f0" }}>
                  <td style={{ padding: "6px 8px" }}>{row.chapterNo}</td>
                  <td style={{ padding: "6px 8px" }}>{row.tensionScore ?? "—"}</td>
                  <td style={{ padding: "6px 8px" }}>{row.styleSimilarity ?? "—"}</td>
                  <td style={{ padding: "6px 8px", whiteSpace: "nowrap" }}>{row.createdAt}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      </>
      ) : null}

      {hubTab === "more" ? (
      <>
      <h3 className="mf-subsection-title">写作 Skill（YAML）</h3>
      <p className="mf-muted mf-text-sm" style={{ maxWidth: 560, marginTop: 0 }}>
        在 Writer 下的 <code className="mf-code">app/skills/library/</code>{" "}
        放置根目录 <code className="mf-code">*.yaml</code>，或<strong>子文件夹</strong>内{" "}
        <code className="mf-code">skill.yaml</code>/<code className="mf-code">index.yaml</code>
        （详见该目录 README）；重启 Writer 后列表自动更新。
        保存后，<strong>故事初始化</strong>合并全书字段进契约，<strong>章节生成</strong>附带每章短约束。
        可选「无」表示不使用任何 Skill。改 Skill 不会自动更新已有快照。
      </p>
      {writerSkillsErr && (
        <p className="mf-alert mf-alert-warn" style={{ marginBottom: 8, fontSize: 13 }}>
          无法加载 Skill 列表（Writer 未启动或代理失败）：{writerSkillsErr}
        </p>
      )}
      {fanPresetErr && (
        <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
          {fanPresetErr}
        </p>
      )}
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 8 }}>
        <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: 14 }}>选用 Skill</span>
          <select
            className="mf-select mf-select-inline"
            value={fanPresetDraft}
            onChange={(e) => setFanPresetDraft(e.target.value)}
          >
            <option value="">无（不使用）</option>
            {fanPresetDraft &&
              !writerSkills.some((s) => s.id === fanPresetDraft) && (
                <option value={fanPresetDraft}>
                  已保存但 library 中未找到：{fanPresetDraft}
                </option>
              )}
            {writerSkills.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label} ({s.id})
              </option>
            ))}
          </select>
        </label>
        <button type="button" disabled={fanPresetBusy} onClick={() => void onSaveFanPreset()} className="mf-btn">
          {fanPresetBusy ? "保存中…" : "保存预设"}
        </button>
      </div>

      <details className="mf-details" style={{ marginTop: 20, marginBottom: 8 }}>
        <summary>
          写作服务连接状态（排障时可展开）
        </summary>
        <p style={{ marginTop: 10 }}>
          <strong>当前：</strong>
          {writerConnected ? (
            <span style={{ color: "#15803d" }}>正常</span>
          ) : (
            <span style={{ color: "crimson" }}>异常（生成可能失败）</span>
          )}
        </p>
        <pre className="mf-pre" style={{ fontSize: 12, marginTop: 10 }}>
          {JSON.stringify(writerEngine, null, 2)}
        </pre>
      </details>
      </>
      ) : null}

      {hubTab === "genre" ? (
      <>
      <h2 className="mf-section-title" style={{ marginTop: 32 }}>
        题材方案（三条路径并行）
      </h2>
      <p style={{ maxWidth: 900, fontSize: 14, color: "#334155" }}>
        <strong>① 偏好推荐</strong>与<strong>② 互动采访</strong>为通用路径（仍走多 Agent 备选卡）；<strong>③ Skill 路径</strong>：选 Skill
        后先做题材/场景追问，对话定稿后生成<strong>唯一锁定</strong>的题材方案（无多路 Scout 备选，仅满足表结构的三条同向占位）。
        三条路径可同时尝试；生成的卡片都进入下列表。
        <strong>进入「题材与大纲」跑初始化前，仍需在下方列表选定一份题材方案</strong>（后端校验）。
      </p>

      <div
        className="genre-three-cols"
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(3, minmax(0, 1fr))",
          gap: 14,
          alignItems: "start",
          marginTop: 16,
        }}
      >
        <div className="mf-panel mf-panel-bordered" style={{ minWidth: 0 }}>
          <h3 style={{ marginTop: 0, fontSize: 15 }}>① 偏好推荐</h3>
          <p style={{ fontSize: 12, color: "#64748b", marginTop: 0 }}>
            填平台与偏好，流式生成若干题材卡。
          </p>
          <form onSubmit={onGenreSubmit} style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            <label>
              目标平台
              <input
                value={targetPlatform}
                onChange={(e) => setTargetPlatform(e.target.value)}
                required
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              频道
              <input
                value={genderChannel}
                onChange={(e) => setGenderChannel(e.target.value)}
                required
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              偏好题材（逗号分隔，可空）
              <input
                value={preferredGenresRaw}
                onChange={(e) => setPreferredGenresRaw(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              避雷（逗号分隔）
              <input
                value={avoidRaw}
                onChange={(e) => setAvoidRaw(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              写法强项（逗号分隔）
              <input
                value={writingStrengthRaw}
                onChange={(e) => setWritingStrengthRaw(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              风险承受
              <select
                value={riskPreference}
                onChange={(e) => setRiskPreference(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              >
                <option value="low">low</option>
                <option value="medium">medium</option>
                <option value="high">high</option>
              </select>
            </label>
            <button type="submit" disabled={genreBusy}>
              {genreBusy ? "生成中…" : "生成题材推荐"}
            </button>
          </form>
          {!writerConnected && (
            <p style={{ color: "crimson", marginTop: 8, fontSize: 12 }}>
              Writer 未连通时建议先排除故障再生成。
            </p>
          )}
          {genreStreamLog.length > 0 && (
            <pre
              style={{
                marginTop: 12,
                maxHeight: 140,
                overflow: "auto",
                background: "#f0f7ff",
                padding: 10,
                borderRadius: 8,
                fontSize: 11,
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
              }}
            >
              {genreStreamLog}
            </pre>
          )}
          {genreErr && <p style={{ color: "crimson", marginTop: 8, fontSize: 13 }}>{genreErr}</p>}
        </div>

        <div className="mf-panel mf-panel-bordered" style={{ minWidth: 0 }}>
          <h3 style={{ marginTop: 0, fontSize: 15 }}>② 互动采访（对话）</h3>
          <p style={{ fontSize: 12, color: "#64748b", marginTop: 0 }}>
            无 Skill 时的自由多轮对话，把脑洞压实成故事种子；与①③并行。
          </p>
          <div
            style={{
              border: "1px solid #ddd",
              borderRadius: 8,
              padding: 12,
              minHeight: 160,
              maxHeight: 260,
              overflow: "auto",
              background: "#fff",
              marginBottom: 10,
            }}
          >
            {interviewMessages.length === 0 ? (
              <p style={{ color: "#888", margin: 0, fontSize: 13 }}>输入脑洞或第一句话，开始采访。</p>
            ) : (
              interviewMessages.map((m, i) => (
                <div
                  key={i}
                  style={{
                    marginBottom: 10,
                    textAlign: m.role === "user" ? "right" : "left",
                  }}
                >
                  <span
                    style={{
                      display: "inline-block",
                      padding: "8px 12px",
                      borderRadius: 8,
                      background: m.role === "user" ? "#d6e8ff" : "#fff",
                      border: "1px solid #e0e0e0",
                      whiteSpace: "pre-wrap",
                      textAlign: "left",
                      maxWidth: "92%",
                      fontSize: 13,
                    }}
                  >
                    <strong>{m.role === "user" ? "你" : "编辑"}：</strong>
                    {m.content}
                  </span>
                </div>
              ))
            )}
          </div>
          <form onSubmit={onInterviewSend} style={{ display: "flex", flexDirection: "column", gap: 8 }}>
            <textarea
              value={interviewInput}
              onChange={(e) => setInterviewInput(e.target.value)}
              rows={3}
              placeholder="说说你的想法、设定或困惑…"
              disabled={interviewBusy || interviewDone?.status === "complete"}
              style={{ width: "100%", fontSize: 13 }}
            />
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button type="submit" disabled={interviewBusy || interviewDone?.status === "complete"}>
                {interviewBusy ? "等待回复…" : "发送"}
              </button>
              <button
                type="button"
                onClick={() => {
                  setInterviewMessages([]);
                  setInterviewInput("");
                  setInterviewErr(null);
                  setInterviewDone(null);
                }}
              >
                清空会话
              </button>
            </div>
          </form>
          {interviewErr && <p style={{ color: "crimson", marginTop: 8, fontSize: 13 }}>{interviewErr}</p>}
          {interviewDone?.status === "complete" && interviewDone.finalSummary && (
            <div
              style={{
                marginTop: 12,
                padding: 12,
                borderRadius: 10,
                border: "2px solid #6b8cff",
                background: "#f4f7ff",
              }}
            >
              <h4 style={{ marginTop: 0, fontSize: 14 }}>采访完成</h4>
              <p style={{ whiteSpace: "pre-wrap", lineHeight: 1.5, fontSize: 13 }}>{interviewDone.finalSummary}</p>
              <p style={{ fontSize: 12 }}>
                <strong>种子草稿：</strong>
                <code>{interviewDone.persistedNovelSeedContractId ?? "—"}</code>
              </p>
              {interviewDone.coreSettings && (
                <details style={{ marginTop: 8 }}>
                  <summary style={{ fontSize: 12 }}>coreSettings</summary>
                  <pre style={{ fontSize: 11, overflow: "auto", maxHeight: 120 }}>
                    {JSON.stringify(interviewDone.coreSettings, null, 2)}
                  </pre>
                </details>
              )}
            </div>
          )}
        </div>

        <div className="mf-panel mf-panel-bordered" style={{ minWidth: 0 }}>
          <h3 style={{ marginTop: 0, fontSize: 15 }}>③ Skill → 确认对话 → 唯一题材方案</h3>
          <p style={{ fontSize: 12, color: "#64748b", marginTop: 0 }}>
            选择 Skill 后，模型结合 Skill 追问您希望的题材与关键场景；定稿后点「生成题材方案」将走<strong>单轮锁定</strong>管线（不再输出多套 Scout 备选）。
          </p>
          <label style={{ display: "block", fontSize: 13, marginBottom: 8 }}>
            加载 Skill（必选本栏能力）
            <select
              value={skillPickId}
              onChange={(e) => setSkillPickId(e.target.value)}
              style={{ display: "block", width: "100%", marginTop: 4, padding: "8px 10px", borderRadius: 8 }}
            >
              <option value="">— 未选择（本栏不可用）—</option>
              {writerSkills.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.label} ({s.id})
                </option>
              ))}
            </select>
          </label>
          {writerSkillsErr && <p style={{ color: "crimson", fontSize: 12 }}>{writerSkillsErr}</p>}
          {!skillPickId ? (
            <p style={{ fontSize: 13, color: "#64748b", marginTop: 8 }}>
              未选择 Skill 时此处不进行「一句话故事线」输入；请用①或②，或先在 Writer{" "}
              <code style={{ fontSize: 11 }}>app/skills/library/</code> 配置 Skill。
            </p>
          ) : (
            <>
              {skillMessages.length === 0 ? (
                <button
                  type="button"
                  disabled={skillBusy}
                  onClick={() => void onSkillConversationStart()}
                  style={{
                    padding: "10px 14px",
                    borderRadius: 8,
                    border: "1px solid #93c5fd",
                    background: "#eff6ff",
                    cursor: skillBusy ? "wait" : "pointer",
                    fontWeight: 600,
                  }}
                >
                  {skillBusy ? "正在开场…" : "开始 Skill 确认对话"}
                </button>
              ) : (
                <>
                  <div
                    style={{
                      border: "1px solid #ddd",
                      borderRadius: 8,
                      padding: 12,
                      minHeight: 120,
                      maxHeight: 220,
                      overflow: "auto",
                      background: "#fff",
                      marginBottom: 10,
                    }}
                  >
                    {skillMessages.map((m, i) => (
                      <div
                        key={i}
                        style={{
                          marginBottom: 10,
                          textAlign: m.role === "user" ? "right" : "left",
                        }}
                      >
                        <span
                          style={{
                            display: "inline-block",
                            padding: "8px 12px",
                            borderRadius: 8,
                            background: m.role === "user" ? "#dbeafe" : "#fff",
                            border: "1px solid #e0e0e0",
                            whiteSpace: "pre-wrap",
                            textAlign: "left",
                            maxWidth: "92%",
                            fontSize: 13,
                          }}
                        >
                          <strong>{m.role === "user" ? "你" : "编辑"}：</strong>
                          {m.content}
                        </span>
                      </div>
                    ))}
                  </div>
                  <form onSubmit={onSkillInterviewSend} style={{ display: "flex", flexDirection: "column", gap: 8 }}>
                    <textarea
                      value={skillInput}
                      onChange={(e) => setSkillInput(e.target.value)}
                      rows={3}
                      placeholder="回答模型的确认问题…"
                      disabled={skillBusy || skillDone?.status === "complete"}
                      style={{ width: "100%", fontSize: 13 }}
                    />
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <button type="submit" disabled={skillBusy || skillDone?.status === "complete"}>
                        {skillBusy ? "等待回复…" : "发送"}
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setSkillMessages([]);
                          setSkillInput("");
                          setSkillErr(null);
                          setSkillDone(null);
                        }}
                      >
                        清空本栏对话
                      </button>
                    </div>
                  </form>
                </>
              )}
              {skillErr && <p style={{ color: "crimson", marginTop: 8, fontSize: 13 }}>{skillErr}</p>}
              {skillDone?.status === "complete" && skillDone.finalSummary && (
                <div
                  style={{
                    marginTop: 12,
                    padding: 12,
                    borderRadius: 10,
                    border: "2px solid #7c3aed",
                    background: "#f5f3ff",
                  }}
                >
                  <h4 style={{ marginTop: 0, fontSize: 14 }}>Skill 侧确认完成</h4>
                  <p style={{ whiteSpace: "pre-wrap", lineHeight: 1.5, fontSize: 13 }}>{skillDone.finalSummary}</p>
                  <form onSubmit={onSkillConfirmedGenreStream} style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 8 }}>
                    <label>
                      目标平台
                      <input
                        value={hookPlatform}
                        onChange={(e) => setHookPlatform(e.target.value)}
                        style={{ display: "block", width: "100%", marginTop: 4 }}
                      />
                    </label>
                    <label>
                      频道
                      <input
                        value={hookChannel}
                        onChange={(e) => setHookChannel(e.target.value)}
                        style={{ display: "block", width: "100%", marginTop: 4 }}
                      />
                    </label>
                    <label>
                      风险承受
                      <select
                        value={hookRisk}
                        onChange={(e) => setHookRisk(e.target.value)}
                        style={{ display: "block", width: "100%", marginTop: 4 }}
                      >
                        <option value="low">low</option>
                        <option value="medium">medium</option>
                        <option value="high">high</option>
                      </select>
                    </label>
                    <button type="submit" disabled={hookBusy}>
                      {hookBusy ? "生成中…" : "生成唯一题材方案（Skill 锁定管线）"}
                    </button>
                  </form>
                </div>
              )}
              {hookStreamLog.length > 0 && (
                <pre
                  style={{
                    marginTop: 12,
                    maxHeight: 140,
                    overflow: "auto",
                    background: "#f7f0ff",
                    padding: 10,
                    borderRadius: 8,
                    fontSize: 11,
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                  }}
                >
                  {hookStreamLog}
                </pre>
              )}
              {hookErr && skillPickId ? <p style={{ color: "crimson", marginTop: 8, fontSize: 13 }}>{hookErr}</p> : null}
            </>
          )}
        </div>
      </div>

      <style>{`
        @media (max-width: 1024px) {
          .genre-three-cols {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>

      <h2 style={{ marginTop: 32 }}>已保存的题材方案（数据库）</h2>
      {workspace && (
        <p style={{ marginBottom: 8 }}>
          当前用于初始化小说的选定方案 ID：<code>{workspace.selectedGenreContractId ?? "（未选定）"}</code>
        </p>
      )}
      {workspace && workspace.genreContracts.length === 0 ? (
        <p>暂无记录；请先用上方任一路径生成。</p>
      ) : (
        <form onSubmit={onConfirmGenreSelection}>
          <table style={{ borderCollapse: "collapse", width: "100%", maxWidth: 960, fontSize: 14 }}>
            <thead>
              <tr>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>选用</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>来源</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>主推题材</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>故事线摘要</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>时间</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {(workspace?.genreContracts ?? []).map((g) => (
                <tr key={g.id}>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>
                    <input
                      type="radio"
                      name="pickGenre"
                      checked={pickGenreId === g.id}
                      onChange={() => setPickGenreId(g.id)}
                    />
                  </td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>{sourceLabel(g.source)}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>{g.primaryGenreLabel || "—"}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", maxWidth: 220 }}>{g.storyHookPreview || "—"}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", whiteSpace: "nowrap" }}>{g.createdAt}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", whiteSpace: "nowrap" }}>
                    <button type="button" style={{ marginRight: 8 }} onClick={() => openGenreModal(g.id)}>
                      详情 / 编辑
                    </button>
                    <button type="button" onClick={() => onDeleteGenreContractRow(g.id)}>
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button type="submit" disabled={pickBusy || !pickGenreId} style={{ marginTop: 10 }}>
            {pickBusy ? "保存中…" : "确认：用所选方案进入初始化"}
          </button>
        </form>
      )}
      {pickErr && <p style={{ color: "crimson", marginTop: 8 }}>{pickErr}</p>}

      {genreModalId && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            zIndex: 1000,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 16,
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) closeGenreModal();
          }}
        >
          <div
            style={{
              background: "#fff",
              maxWidth: 640,
              width: "100%",
              maxHeight: "90vh",
              overflow: "auto",
              borderRadius: 10,
              padding: 20,
              boxShadow: "0 8px 32px rgba(0,0,0,0.2)",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginTop: 0 }}>题材方案 · 详情与手动编辑</h3>
            <p style={{ fontSize: 13, color: "#444" }}>
              <code>{genreModalId}</code>
            </p>
            {genreModalBusy ? (
              <p>加载中…</p>
            ) : genreModalErr && !genreModalRaw ? (
              <p style={{ color: "crimson" }}>{genreModalErr}</p>
            ) : genreModalRaw ? (
              <>
                <h4 style={{ marginBottom: 8 }}>编辑（保存后写入数据库）</h4>
                <label style={{ display: "block", marginBottom: 12 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>核心钩子 recommendedCoreHook</div>
                  <textarea
                    value={genreModalHook}
                    onChange={(e) => setGenreModalHook(e.target.value)}
                    rows={4}
                    style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>主推题材 genre</div>
                  <input
                    value={genreModalGenre}
                    onChange={(e) => setGenreModalGenre(e.target.value)}
                    style={{ width: "100%", boxSizing: "border-box", padding: 6 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>频道 channel</div>
                  <input
                    value={genreModalChannel}
                    onChange={(e) => setGenreModalChannel(e.target.value)}
                    style={{ width: "100%", boxSizing: "border-box", padding: 6 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>推荐理由 reason</div>
                  <textarea
                    value={genreModalReason}
                    onChange={(e) => setGenreModalReason(e.target.value)}
                    rows={3}
                    style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 16 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>标签 subTags（逗号分隔）</div>
                  <input
                    value={genreModalSubTags}
                    onChange={(e) => setGenreModalSubTags(e.target.value)}
                    style={{ width: "100%", boxSizing: "border-box", padding: 6 }}
                  />
                </label>

                <h4 style={{ marginBottom: 8 }}>全局风险（一行一条）</h4>
                <textarea
                  value={genreModalRiskText}
                  onChange={(e) => setGenreModalRiskText(e.target.value)}
                  rows={5}
                  placeholder="每行一条风险说明"
                  style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
                />

                <h4 style={{ marginTop: 16, marginBottom: 8 }}>候选方向 candidateRankings</h4>
                <p style={{ fontSize: 13, color: "#64748b", marginTop: 0 }}>
                  可增删改；保存后写入题材 JSON。
                </p>
                {genreModalCandidates.map((c, i) => (
                  <div
                    key={i}
                    style={{
                      marginBottom: 14,
                      padding: 10,
                      border: "1px solid #e5e7eb",
                      borderRadius: 8,
                      background: "#fafafa",
                    }}
                  >
                    <label style={{ display: "block", marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>题材标签 genre</span>
                      <input
                        value={String(c.genre ?? "")}
                        onChange={(e) => {
                          const next = [...genreModalCandidates];
                          next[i] = { ...next[i], genre: e.target.value };
                          setGenreModalCandidates(next);
                        }}
                        style={{ display: "block", width: "100%", marginTop: 4, padding: 6 }}
                      />
                    </label>
                    <label style={{ display: "block", marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>推荐理由</span>
                      <textarea
                        value={String(
                          c.recommendReason ?? c.recommend_reason ?? "",
                        )}
                        onChange={(e) => {
                          const next = [...genreModalCandidates];
                          next[i] = {
                            ...next[i],
                            recommendReason: e.target.value,
                            recommend_reason: e.target.value,
                          };
                          setGenreModalCandidates(next);
                        }}
                        rows={2}
                        style={{ display: "block", width: "100%", marginTop: 4, fontFamily: "inherit", fontSize: 13 }}
                      />
                    </label>
                    <label style={{ display: "block", marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>风险说明</span>
                      <textarea
                        value={String(c.riskNote ?? c.risk_note ?? "")}
                        onChange={(e) => {
                          const next = [...genreModalCandidates];
                          next[i] = {
                            ...next[i],
                            riskNote: e.target.value,
                            risk_note: e.target.value,
                          };
                          setGenreModalCandidates(next);
                        }}
                        rows={2}
                        style={{ display: "block", width: "100%", marginTop: 4, fontFamily: "inherit", fontSize: 13 }}
                      />
                    </label>
                    <button
                      type="button"
                      onClick={() =>
                        setGenreModalCandidates((prev) => prev.filter((_, j) => j !== i))
                      }
                      style={{ fontSize: 13, color: "#b91c1c" }}
                    >
                      删除本条候选
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() =>
                    setGenreModalCandidates((prev) => [
                      ...prev,
                      {
                        genre: "",
                        recommendReason: "",
                        recommend_reason: "",
                        riskNote: "",
                        risk_note: "",
                        finalScore: 0,
                        tokenCostLevel: "medium",
                      },
                    ])
                  }
                  style={{ marginBottom: 12 }}
                >
                  + 添加候选
                </button>

                {genreModalErr && <p style={{ color: "crimson" }}>{genreModalErr}</p>}
                <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
                  <button type="button" disabled={genreModalSaveBusy} onClick={() => saveGenreModal()}>
                    {genreModalSaveBusy ? "保存中…" : "保存"}
                  </button>
                  <button type="button" disabled={genreModalSaveBusy} onClick={() => closeGenreModal()}>
                    取消
                  </button>
                </div>
              </>
            ) : (
              <p style={{ color: "crimson" }}>{genreModalErr ?? "未知错误"}</p>
            )}
            {!genreModalBusy && !genreModalRaw && genreModalErr ? (
              <button type="button" style={{ marginTop: 12 }} onClick={() => closeGenreModal()}>
                关闭
              </button>
            ) : null}
          </div>
        </div>
      )}

      {contract && (
        <div style={{ marginTop: 24 }}>
          <p>
            <strong>最近一次流式完成 · contractId：</strong>
            {genreResult?.contractId}
          </p>
          <h3>主推方向 selectedDirection</h3>
          <p>
            <strong>{contract.selectedDirection.genre}</strong>（{contract.selectedDirection.channel}）
          </p>
          <p>{contract.selectedDirection.reason}</p>
          <p>
            <strong>标签：</strong>
            {contract.selectedDirection.subTags.join("、")}
          </p>

          <h3>核心钩子 recommendedCoreHook</h3>
          <p>{contract.recommendedCoreHook}</p>

          <h3>全局风险 riskNotes</h3>
          {contract.riskNotes.length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {contract.riskNotes.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h3>三个候选 candidateRankings</h3>
          <p style={{ fontSize: 13, color: "#64748b" }}>
            编辑请在上方题材列表中点击对应方案打开弹窗修改。
          </p>
          <ol>
            {contract.candidateRankings.map((c, i) => (
              <li key={i} style={{ marginBottom: 16 }}>
                <strong>{c.genre}</strong> — finalScore {c.finalScore} / token {c.tokenCostLevel}
                <div style={{ marginTop: 6 }}>
                  <strong>推荐理由：</strong>
                  {c.recommendReason}
                </div>
                <div style={{ marginTop: 6 }}>
                  <strong>风险：</strong>
                  {c.riskNote}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
      </>
      ) : null}
    </section>
  );
}
