package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.chapter.dto.GenerationJobProgressRequest;
import com.mythosforge.chapter.dto.GenerationJobQueuedResponse;
import com.mythosforge.chapter.dto.GenerationJobStatusResponse;
import com.mythosforge.chapter.events.GenerationJobSucceededEvent;
import com.mythosforge.writer.WriterEngineClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class GenerationJobService {

    private static final Logger log = LoggerFactory.getLogger(GenerationJobService.class);

    private final GenerationJobRepository generationJobRepository;
    private final ChapterGenerationService chapterGenerationService;
    private final ChapterPrewritePlanService chapterPrewritePlanService;
    private final WriterEngineClient writerEngineClient;
    private final TaskExecutor applicationTaskExecutor;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final int consecutiveFailedJobsToBlockEnqueue;

    public GenerationJobService(
            GenerationJobRepository generationJobRepository,
            ChapterGenerationService chapterGenerationService,
            ChapterPrewritePlanService chapterPrewritePlanService,
            WriterEngineClient writerEngineClient,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor,
            TransactionTemplate transactionTemplate,
            ApplicationEventPublisher eventPublisher,
            @Value("${mythosforge.generation.consecutive-failed-jobs-to-block-enqueue:5}") int consecutiveFailedJobsToBlockEnqueue
    ) {
        this.generationJobRepository = generationJobRepository;
        this.chapterGenerationService = chapterGenerationService;
        this.chapterPrewritePlanService = chapterPrewritePlanService;
        this.writerEngineClient = writerEngineClient;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.transactionTemplate = transactionTemplate;
        this.eventPublisher = eventPublisher;
        this.consecutiveFailedJobsToBlockEnqueue = consecutiveFailedJobsToBlockEnqueue;
    }

    public GenerationJobQueuedResponse enqueue(String projectId, int chapterNo, ChapterGenerateBody body) {
        chapterGenerationService.requireProjectStoryAndChapter(projectId, chapterNo);
        chapterPrewritePlanService.requireConfirmedPlanForGeneration(projectId, chapterNo);
        if (consecutiveFailedJobsToBlockEnqueue > 0) {
            int fails = countConsecutiveFailuresFromNewest(projectId, chapterNo);
            if (fails >= consecutiveFailedJobsToBlockEnqueue) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "该章节已连续失败 "
                                + fails
                                + " 次生成任务；请检查 Writer 日志或章纲后再试。"
                                + "（可在 application.yml 调整 mythosforge.generation.consecutive-failed-jobs-to-block-enqueue，设为 0 关闭熔断）"
                );
            }
        }
        JsonNode payload = chapterGenerationService.buildWriterPayload(projectId, chapterNo, body);

        String jobId = UUID.randomUUID().toString().replace("-", "");
        GenerationJobEntity row = new GenerationJobEntity();
        row.setId(jobId);
        row.setProjectId(projectId);
        row.setChapterNo(chapterNo);
        row.setJobType(GenerationJobTypes.CHAPTER_GENERATE);
        row.setStatus(GenerationJobStatuses.PENDING);
        row.setCurrentStage("已提交");
        row.setProgressPct(0);
        row.setPayloadJson(payload);

        transactionTemplate.executeWithoutResult(ts -> generationJobRepository.save(row));

        String finalJobId = jobId;
        applicationTaskExecutor.execute(() -> runJobInBackground(finalJobId));

        return new GenerationJobQueuedResponse(jobId, GenerationJobStatuses.PENDING, "任务已提交后台生成");
    }

    private void runJobInBackground(String jobId) {
        try {
            applyProgress(jobId, new GenerationJobProgressRequest("Writer 生成中", 5, null));
            JsonNode payload = getPayloadForWorker(jobId);
            JsonNode result = writerEngineClient.postChapterGenerateComplete(payload, jobId);
            applyComplete(jobId, result);
        } catch (ResponseStatusException e) {
            String reason = e.getReason() != null ? e.getReason() : e.getStatusCode().toString();
            log.warn("job {} aborted: {}", jobId, reason);
            try {
                applyFail(jobId, reason);
            } catch (Exception ex) {
                log.warn("job {} applyFail after ResponseStatusException: {}", jobId, ex.getMessage());
            }
        } catch (RestClientException e) {
            log.error("job {} writer HTTP failed", jobId, e);
            applyFail(jobId, "Writer 不可用或超时: " + e.getMessage());
        } catch (Exception e) {
            log.error("job {} failed", jobId, e);
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            applyFail(jobId, msg);
        }
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
        GenerationJobEntity published = generationJobRepository.findById(jobId).orElse(null);
        if (published != null
                && GenerationJobStatuses.SUCCEEDED.equals(published.getStatus())
                && published.getChapterVersionId() != null
                && !published.getChapterVersionId().isBlank()) {
            eventPublisher.publishEvent(new GenerationJobSucceededEvent(this, jobId));
        }
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

    private int countConsecutiveFailuresFromNewest(String projectId, int chapterNo) {
        List<GenerationJobEntity> list =
                generationJobRepository.findTop20ByProjectIdAndChapterNoOrderByCreatedAtDesc(projectId, chapterNo);
        int c = 0;
        for (GenerationJobEntity j : list) {
            if (GenerationJobStatuses.FAILED.equals(j.getStatus())) {
                c++;
            } else {
                break;
            }
        }
        return c;
    }
}
