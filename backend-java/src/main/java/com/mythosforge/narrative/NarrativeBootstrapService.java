package com.mythosforge.narrative;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.genre.GenreDecisionContract;
import com.mythosforge.narrative.dto.ConfluenceUpsertBody;
import com.mythosforge.narrative.dto.NarrativeStorylineResponse;
import com.mythosforge.narrative.dto.StorylineUpsertBody;
import com.mythosforge.project.Project;
import com.mythosforge.project.ProjectRepository;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 将「故事契约 + 题材创意」落到 PG 故事线表，避免初始化与故事结构两套数据脱节。
 */
@Service
public class NarrativeBootstrapService {

    private final ProjectRepository projectRepository;
    private final NarrativeStorylineRepository storylineRepository;
    private final NarrativeStructureService narrativeStructureService;
    private final StoryContractRepository storyContractRepository;

    public NarrativeBootstrapService(
            ProjectRepository projectRepository,
            NarrativeStorylineRepository storylineRepository,
            NarrativeStructureService narrativeStructureService,
            StoryContractRepository storyContractRepository
    ) {
        this.projectRepository = projectRepository;
        this.storylineRepository = storylineRepository;
        this.narrativeStructureService = narrativeStructureService;
        this.storyContractRepository = storyContractRepository;
    }

    public record BootstrapResult(boolean created, int storylinesCreated, int confluencesCreated, String message) {}

    @Transactional
    public BootstrapResult bootstrapIfEmpty(
            String projectId,
            JsonNode storyContract,
            String storyHookText,
            String firstVolumeOutline
    ) {
        projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        if (!storylineRepository.findByProjectIdOrderBySortOrderAscCreatedAtAsc(projectId).isEmpty()) {
            return new BootstrapResult(false, 0, 0, "项目已有故事线，未覆盖");
        }
        if (storyContract == null || storyContract.isNull() || !storyContract.isObject()) {
            return new BootstrapResult(false, 0, 0, "无故事契约，无法生成");
        }

        Project project = projectRepository.findById(projectId).orElseThrow();
        int targetCh = project.getTargetChapters() != null && project.getTargetChapters() > 0
                ? project.getTargetChapters()
                : 100;

        String mainTitle = resolveMainTitle(storyContract, project.getName());
        String progress = buildProgressSummary(storyContract, storyHookText, firstVolumeOutline);

        NarrativeStorylineResponse main = narrativeStructureService.createStoryline(
                projectId,
                new StorylineUpsertBody(
                        "main",
                        mainTitle,
                        null,
                        "MAIN",
                        "ACTIVE",
                        1,
                        targetCh,
                        JsonNodeFactory.instance.arrayNode(),
                        0,
                        null,
                        1,
                        progress
                )
        );

        int created = 1;
        List<NarrativeStorylineResponse> subs = new ArrayList<>();
        JsonNode chars = storyContract.get("characters");
        if (chars == null || !chars.isArray()) {
            chars = storyContract.get("supportingCharacters");
        }
        if (chars != null && chars.isArray()) {
            int idx = 0;
            for (JsonNode ch : chars) {
                if (!ch.isObject() || idx >= 4) {
                    break;
                }
                String name = text(ch, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String role = text(ch, "role");
                String hook = text(ch, "oneLineHook", "one_line_hook");
                String title = role != null && !role.isBlank() ? name + "（" + role + "）" : name;
                String subSummary = hook != null && !hook.isBlank() ? hook : role;
                String key = "sub_" + slugKey(name, idx);
                NarrativeStorylineResponse sub = narrativeStructureService.createStoryline(
                        projectId,
                        new StorylineUpsertBody(
                                key,
                                title,
                                main.id(),
                                "SUB",
                                "ACTIVE",
                                1,
                                targetCh,
                                JsonNodeFactory.instance.arrayNode(),
                                0,
                                null,
                                10 + idx,
                                subSummary
                        )
                );
                subs.add(sub);
                idx++;
                created++;
            }
        }

        int confCreated = 0;
        if (!subs.isEmpty()) {
            int meetCh = Math.max(3, Math.min(targetCh, targetCh / 5));
            narrativeStructureService.createConfluence(
                    projectId,
                    new ConfluenceUpsertBody(
                            main.id(),
                            subs.get(0).id(),
                            meetCh,
                            "intersect",
                            "由故事契约自动建议的首次汇合章，可在故事结构里修改。",
                            null,
                            null,
                            JsonNodeFactory.instance.arrayNode()
                    )
            );
            confCreated = 1;
        }

        return new BootstrapResult(
                true,
                created,
                confCreated,
                "已从故事契约生成 " + created + " 条故事线" + (confCreated > 0 ? "与 " + confCreated + " 个汇合点" : "")
        );
    }

    @Transactional
    public BootstrapResult bootstrapFromProjectSelection(String projectId, String storyHookText) {
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        String sid = p.getSelectedStoryContractId();
        if (sid == null || sid.isBlank()) {
            return new BootstrapResult(false, 0, 0, "请先完成「题材与大纲」中的故事初始化");
        }
        StoryContractEntity story = storyContractRepository.findById(sid)
                .orElseThrow(() -> new IllegalArgumentException("故事契约不存在"));
        if (!projectId.equals(story.getProjectId())) {
            throw new IllegalArgumentException("故事契约不属于该项目");
        }
        return bootstrapIfEmpty(projectId, story.getRawJson(), storyHookText, story.getFirstVolumeOutline());
    }

    /**
     * 生成可导入的叙事域草案（使用 storylineKey / parentStorylineKey，不写库）。
     */
    public ObjectNode buildNarrativeProposalDomain(
            JsonNode storyContract,
            String storyHookText,
            String firstVolumeOutline,
            int targetChapters,
            String projectName
    ) {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("_source", "proposal");
        ArrayNode storylines = JsonNodeFactory.instance.arrayNode();
        ArrayNode confluences = JsonNodeFactory.instance.arrayNode();
        ArrayNode subtextSeeds = JsonNodeFactory.instance.arrayNode();

        if (storyContract == null || !storyContract.isObject()) {
            root.set("storylines", storylines);
            root.set("confluences", confluences);
            root.set("subtextSeeds", subtextSeeds);
            return root;
        }

        String mainTitle = resolveMainTitle(storyContract, projectName);
        String progress = buildProgressSummary(storyContract, storyHookText, firstVolumeOutline);

        ObjectNode main = JsonNodeFactory.instance.objectNode();
        main.put("storylineKey", "main");
        main.put("title", mainTitle);
        main.put("storylineRole", "MAIN");
        main.put("status", "ACTIVE");
        main.put("estStartChapter", 1);
        main.put("estEndChapter", targetChapters);
        main.put("sortOrder", 1);
        if (progress != null) {
            main.put("progressSummary", progress);
        }
        main.set("milestonesJson", JsonNodeFactory.instance.arrayNode());
        storylines.add(main);

        List<ObjectNode> subNodes = new ArrayList<>();
        JsonNode chars = storyContract.get("characters");
        if (chars == null || !chars.isArray()) {
            chars = storyContract.get("supportingCharacters");
        }
        if (chars != null && chars.isArray()) {
            int idx = 0;
            for (JsonNode ch : chars) {
                if (!ch.isObject() || idx >= 4) {
                    break;
                }
                String name = text(ch, "name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String role = text(ch, "role");
                String hook = text(ch, "oneLineHook", "one_line_hook");
                String title = role != null && !role.isBlank() ? name + "（" + role + "）" : name;
                String key = "sub_" + slugKey(name, idx);
                ObjectNode sub = JsonNodeFactory.instance.objectNode();
                sub.put("storylineKey", key);
                sub.put("title", title);
                sub.put("parentStorylineKey", "main");
                sub.put("storylineRole", "SUB");
                sub.put("status", "ACTIVE");
                sub.put("estStartChapter", 1);
                sub.put("estEndChapter", targetChapters);
                sub.put("sortOrder", 10 + idx);
                if (hook != null && !hook.isBlank()) {
                    sub.put("progressSummary", hook);
                    ObjectNode seed = JsonNodeFactory.instance.objectNode();
                    seed.put("chapterNo", 1);
                    seed.put("question", hook);
                    seed.put("importance", "medium");
                    seed.put("suggestedResolveChapter", Math.max(3, targetChapters / 4));
                    subtextSeeds.add(seed);
                }
                sub.set("milestonesJson", JsonNodeFactory.instance.arrayNode());
                storylines.add(sub);
                subNodes.add(sub);
                idx++;
            }
        }

        if (!subNodes.isEmpty()) {
            int meetCh = Math.max(3, Math.min(targetChapters, targetChapters / 5));
            ObjectNode cf = JsonNodeFactory.instance.objectNode();
            cf.put("primaryStorylineKey", "main");
            cf.put("secondaryStorylineKey", subNodes.get(0).get("storylineKey").asText());
            cf.put("targetChapter", meetCh);
            cf.put("confluenceType", "intersect");
            cf.put("notes", "由故事契约自动建议的首次汇合，确认后可改。");
            confluences.add(cf);
        }

        root.set("storylines", storylines);
        root.set("confluences", confluences);
        root.set("subtextSeeds", subtextSeeds);
        return root;
    }

    /** 由 StoryService 在 init 落库后调用。 */
    @Transactional
    public BootstrapResult bootstrapAfterStoryInit(
            String projectId,
            JsonNode storyContract,
            GenreDecisionContract genre,
            String firstVolumeOutline
    ) {
        String hook = genre != null ? genre.getStoryHookText() : null;
        return bootstrapIfEmpty(projectId, storyContract, hook, firstVolumeOutline);
    }

    private static String resolveMainTitle(JsonNode contract, String projectName) {
        JsonNode pos = contract.get("positioning");
        if (pos != null && pos.isObject()) {
            JsonNode titles = pos.get("titleCandidates");
            if (titles == null) {
                titles = pos.get("title_candidates");
            }
            if (titles != null && titles.isArray() && !titles.isEmpty()) {
                String t = titles.get(0).asText("").trim();
                if (!t.isBlank()) {
                    return t.length() > 120 ? t.substring(0, 120) : t;
                }
            }
            String hook = text(pos, "coreHook", "core_hook");
            if (hook != null && !hook.isBlank()) {
                return hook.length() > 80 ? hook.substring(0, 80) + "…" : hook;
            }
        }
        if (projectName != null && !projectName.isBlank()) {
            return projectName.trim() + " · 主线";
        }
        return "主线";
    }

    private static String buildProgressSummary(JsonNode contract, String storyHook, String firstVolumeOutline) {
        StringBuilder sb = new StringBuilder();
        if (storyHook != null && !storyHook.isBlank()) {
            sb.append("【题材创意】").append(storyHook.trim()).append("\n\n");
        }
        String fvd = text(contract, "firstVolumeDirection", "first_volume_direction");
        if (fvd != null && !fvd.isBlank()) {
            sb.append(fvd.trim());
        } else if (firstVolumeOutline != null && !firstVolumeOutline.isBlank()) {
            sb.append(firstVolumeOutline.trim());
        }
        String s = sb.toString().trim();
        if (s.length() > 2000) {
            return s.substring(0, 2000);
        }
        return s.isBlank() ? null : s;
    }

    private static String text(JsonNode node, String... keys) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && v.isTextual()) {
                String t = v.asText().trim();
                if (!t.isBlank()) {
                    return t;
                }
            }
        }
        return null;
    }

    private static String slugKey(String name, int idx) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fff]+", "_")
                .toLowerCase(Locale.ROOT);
        if (base.isBlank() || base.length() > 32) {
            return String.valueOf(idx + 1);
        }
        return base;
    }
}
