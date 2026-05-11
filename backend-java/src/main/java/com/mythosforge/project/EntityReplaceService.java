package com.mythosforge.project;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mythosforge.chapter.ChapterContractEntity;
import com.mythosforge.chapter.ChapterContractRepository;
import com.mythosforge.chapter.ChapterVersionEntity;
import com.mythosforge.chapter.ChapterVersionRepository;
import com.mythosforge.project.dto.EntityReplaceRequest;
import com.mythosforge.story.StoryContractEntity;
import com.mythosforge.story.StoryContractRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 跨快照正文 / 章纲 / 版本草稿的全局字符串替换（不重跑 Neo4j；图谱需后续手动校正或使用导入管线）。 */
@Service
public class EntityReplaceService {

    private final StoryContractRepository storyContractRepository;
    private final ChapterContractRepository chapterContractRepository;
    private final ChapterVersionRepository chapterVersionRepository;
    private final ObjectMapper objectMapper;

    public EntityReplaceService(
            StoryContractRepository storyContractRepository,
            ChapterContractRepository chapterContractRepository,
            ChapterVersionRepository chapterVersionRepository,
            ObjectMapper objectMapper
    ) {
        this.storyContractRepository = storyContractRepository;
        this.chapterContractRepository = chapterContractRepository;
        this.chapterVersionRepository = chapterVersionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void replaceEntities(String projectId, EntityReplaceRequest request) {
        if (request == null || request.replacements() == null || request.replacements().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "replacements 不能为空");
        }
        List<EntityReplaceRequest.EntityReplacement> reps = new ArrayList<>(request.replacements());
        reps.removeIf(r -> r.from() == null || r.from().isBlank());
        if (reps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "无有效的 from 字段");
        }
        reps.sort(Comparator.comparingInt((EntityReplaceRequest.EntityReplacement r) -> -r.from().length()));

        List<StoryContractEntity> stories = storyContractRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        for (StoryContractEntity s : stories) {
            if (s.getFirstVolumeOutline() != null) {
                s.setFirstVolumeOutline(replaceChain(s.getFirstVolumeOutline(), reps));
            }
            if (s.getAuthorIntent() != null) {
                s.setAuthorIntent(replaceChain(s.getAuthorIntent(), reps));
            }
            JsonNode raw = s.getRawJson();
            if (raw != null) {
                try {
                    String json = objectMapper.writeValueAsString(raw);
                    json = replaceChain(json, reps);
                    s.setRawJson(objectMapper.readTree(json));
                } catch (Exception e) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "story_contract raw_json 替换失败");
                }
            }
            storyContractRepository.save(s);
        }

        List<ChapterContractEntity> chRows = chapterContractRepository.findByProjectId(projectId);
        for (ChapterContractEntity ch : chRows) {
            JsonNode raw = ch.getRawJson();
            if (raw == null) {
                continue;
            }
            try {
                String json = objectMapper.writeValueAsString(raw);
                json = replaceChain(json, reps);
                ch.setRawJson(objectMapper.readTree(json));
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "chapter_contract 替换失败");
            }
            chapterContractRepository.save(ch);
        }

        List<ChapterVersionEntity> vers = chapterVersionRepository.findByProjectId(projectId);
        for (ChapterVersionEntity v : vers) {
            if (v.getChapterText() != null) {
                v.setChapterText(replaceChain(v.getChapterText(), reps));
            }
            if (v.getStyledText() != null) {
                v.setStyledText(replaceChain(v.getStyledText(), reps));
            }
            JsonNode cr = v.getCriticReportJson();
            if (cr != null) {
                try {
                    String json = objectMapper.writeValueAsString(cr);
                    json = replaceChain(json, reps);
                    v.setCriticReportJson(objectMapper.readTree(json));
                } catch (Exception ignored) {
                    // best-effort
                }
            }
            chapterVersionRepository.save(v);
        }
    }

    private static String replaceChain(String s, List<EntityReplaceRequest.EntityReplacement> reps) {
        String out = s;
        for (EntityReplaceRequest.EntityReplacement r : reps) {
            String to = r.to() != null ? r.to() : "";
            out = out.replace(r.from(), to);
        }
        return out;
    }
}
