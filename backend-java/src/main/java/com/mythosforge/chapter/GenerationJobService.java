package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.dto.GenerationJobProgressRequest;
import com.mythosforge.chapter.dto.GenerationJobQueuedResponse;
import com.mythosforge.chapter.dto.GenerationJobStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@Service
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private final GenerationJobRepository generationJobRepository;
    private final ChapterGenerationService chapterGenerationService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public GenerationJobService(
            GenerationJobRepository generationJobRepository,
            ChapterGenerationService chapterGenerationService,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate
    ) {
        this.generationJobRepository = generationJobRepository;
        this.chapterGenerationService = chapterGenerationService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public GenerationJobQueuedResponse enqueue(String projectId, int chapterNo, ChapterGenerateBody body) {
        chapterGenerationService.requireProjectStoryAndChapter(projectId, chapterNo);
        JsonNode payload = chapterGenerationService.buildWriterPayload(projectId, chapterNo, body);

        String jobId = UUID.randomUUID().toString().replace("-", "");
        GenerationJobEntity row = new GenerationJobEntity();
        row.setId(jobId);
        row.setProjectId(projectId);
        row.setChapterNo(chapterNo);
        row.setStatus(GenerationJobStatuses.PENDING);
        row.setCurrentStage("已排队");
        row.setProgressPct(0);
        row.setPayloadJson(payload);

        transactionTemplate.executeWithoutResult(ts -> generationJobRepository.save(row));

        ObjectNode msg = objectMapper.createObjectNode();
        msg.put("jobId", jobId);
        msg.put("projectId", projectId);
        msg.put("chapterNo", chapterNo);

        try {
            rabbitTemplate.convertAndSend("", RabbitChapterGenerationConfig.CHAPTER_GENERATION_QUEUE, msg.toString());
        } catch (Exception e) {
            log.error("RabbitMQ publish failed jobId={}: {}", jobId, e.getMessage());
            transactionTemplate.executeWithoutResult(ts -> {
                GenerationJobEntity j = generationJobRepository.findById(jobId).orElseThrow();
                j.setStatus(GenerationJobStatuses.FAILED);
                j.setErrorMessage("RabbitMQ 不可用: " + e.getMessage());
                j.setCurrentStage("排队失败");
                generationJobRepository.save(j);
            });
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "消息队列不可用，请确认 RabbitMQ 已启动");
        }

        return new GenerationJobQueuedResponse(jobId, GenerationJobStatuses.PENDING, "任务已排队");
    }

    public JsonNode getPayloadForWorker(String jobId) {
        GenerationJobEntity job = generationJobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        JsonNode payload = job.getPayloadJson();
        if (payload == null || payload.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "job 无 payload");
        }
        return payload;
    }

    public void applyProgress(String jobId, GenerationJobProgressRequest req) {
        transactionTemplate.executeWithoutResult(ts -> {
            GenerationJobEntity job = generationJobRepository.findById(jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (GenerationJobStatuses.FAILED.equals(job.getStatus())
                    || GenerationJobStatuses.SUCCEEDED.equals(job.getStatus())) {
                return;
            }
            job.setStatus(GenerationJobStatuses.RUNNING);
            if (req.currentStage() != null && !req.currentStage().isBlank()) {
                job.setCurrentStage(req.currentStage());
            }
            if (req.progressPct() != null) {
                job.setProgressPct(Math.max(0, Math.min(100, req.progressPct())));
            }
            generationJobRepository.save(job);
        });
    }

    public void applyComplete(String jobId, JsonNode fin) {
        transactionTemplate.executeWithoutResult(ts -> {
            GenerationJobEntity job = generationJobRepository.findById(jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            if (GenerationJobStatuses.SUCCEEDED.equals(job.getStatus())) {
                return;
            }

            JsonNode tokenBudget = fin.get("token_budget_status");
            JsonNode llmUsage = fin.get("llm_usage_summary");
            int retryCount = fin.path("retry_count").asInt(0);

            ChapterVersionEntity ver = chapterGenerationService.persistPendingReviewVersion(
                    job.getProjectId(),
                    job.getChapterNo(),
                    fin
            );

            job.setStatus(GenerationJobStatuses.SUCCEEDED);
            job.setProgressPct(100);
            job.setCurrentStage("已完成");
            job.setChapterVersionId(ver.getId());
            job.setTokenBudgetStatusJson(tokenBudget == null || tokenBudget.isNull() ? null : tokenBudget);
            job.setLlmUsageSummaryJson(llmUsage == null || llmUsage.isNull() ? null : llmUsage);
            job.setCriticRejectRounds(retryCount);
            job.setTrimmedOptionalCount(GenerationRoiCalculator.trimmedOptionalCount(tokenBudget));
            job.setRetryWasteTokens(GenerationRoiCalculator.retryWasteTokens(llmUsage, retryCount));

            job.setTotalPromptTokens(GenerationRoiCalculator.longOrNull(llmUsage, "total_prompt_tokens", "totalPromptTokens"));
            job.setTotalCompletionTokens(
                    GenerationRoiCalculator.longOrNull(llmUsage, "total_completion_tokens", "totalCompletionTokens"));
            job.setTotalTokens(GenerationRoiCalculator.longOrNull(llmUsage, "total_tokens", "totalTokens"));

            generationJobRepository.save(job);
        });
    }

    public Optional<GenerationJobStatusResponse> latestJob(String projectId, int chapterNo) {
        return generationJobRepository.findTopByProjectIdAndChapterNoOrderByCreatedAtDesc(projectId, chapterNo)
                .map(GenerationJobService::toStatus);
    }

    private static GenerationJobStatusResponse toStatus(GenerationJobEntity j) {
        return new GenerationJobStatusResponse(
                j.getId(),
                j.getProjectId(),
                j.getChapterNo(),
                j.getStatus(),
                j.getCurrentStage(),
                j.getProgressPct(),
                j.getErrorMessage(),
                j.getChapterVersionId(),
                j.getTotalTokens(),
                j.getRetryWasteTokens(),
                j.getTrimmedOptionalCount(),
                j.getCriticRejectRounds(),
                j.getLlmUsageSummaryJson(),
                j.getTokenBudgetStatusJson(),
                j.getCreatedAt() != null ? j.getCreatedAt().toString() : null,
                j.getUpdatedAt() != null ? j.getUpdatedAt().toString() : null
        );
    }

    public void applyFail(String jobId, String message) {
        transactionTemplate.executeWithoutResult(ts -> {
            GenerationJobEntity job = generationJobRepository.findById(jobId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            job.setStatus(GenerationJobStatuses.FAILED);
            job.setCurrentStage("失败");
            job.setErrorMessage(message != null && message.length() > 4000 ? message.substring(0, 4000) : message);
            generationJobRepository.save(job);
        });
    }
}
