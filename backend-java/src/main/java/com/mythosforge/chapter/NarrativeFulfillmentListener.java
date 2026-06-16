package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mythosforge.chapter.events.GenerationJobSucceededEvent;
import com.mythosforge.narrative.NarrativeFulfillmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 监听 {@link GenerationJobSucceededEvent}：在后台对新生成版本跑 {@link NarrativeFulfillmentService}，
 * 将报告写入 fulfillment_report_json（与定稿 accept 时的履约评估互补）。
 */
@Component
public class NarrativeFulfillmentListener {

    private static final Logger log = LoggerFactory.getLogger(NarrativeFulfillmentListener.class);

    private final GenerationJobRepository generationJobRepository;
    private final ChapterVersionRepository chapterVersionRepository;
    private final NarrativeFulfillmentService narrativeFulfillmentService;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor applicationTaskExecutor;

    public NarrativeFulfillmentListener(
            GenerationJobRepository generationJobRepository,
            ChapterVersionRepository chapterVersionRepository,
            NarrativeFulfillmentService narrativeFulfillmentService,
            TransactionTemplate transactionTemplate,
            @Qualifier("applicationTaskExecutor") TaskExecutor applicationTaskExecutor
    ) {
        this.generationJobRepository = generationJobRepository;
        this.chapterVersionRepository = chapterVersionRepository;
        this.narrativeFulfillmentService = narrativeFulfillmentService;
        this.transactionTemplate = transactionTemplate;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @EventListener
    public void onGenerationJobSucceeded(GenerationJobSucceededEvent event) {
        GenerationJobEntity job = generationJobRepository.findById(event.getJobId()).orElse(null);
        if (job == null || job.getChapterVersionId() == null || job.getChapterVersionId().isBlank()) {
            return;
        }
        applicationTaskExecutor.execute(() -> run(job.getChapterVersionId(), job.getProjectId(), job.getChapterNo()));
    }

    private void run(String versionId, String projectId, int chapterNo) {
        try {
            ChapterVersionEntity ver = chapterVersionRepository.findById(versionId).orElse(null);
            if (ver == null) {
                return;
            }
            if (ver.getFulfillmentReportJson() != null && !ver.getFulfillmentReportJson().isNull()) {
                return;
            }
            String text = ver.getStyledText();
            if (text == null || text.isBlank()) {
                text = ver.getChapterText();
            }
            if (text == null || text.isBlank()) {
                return;
            }
            ObjectNode report = narrativeFulfillmentService.evaluate(projectId, chapterNo, text);
            transactionTemplate.executeWithoutResult(ts -> {
                chapterVersionRepository.findById(versionId).ifPresent(v -> {
                    v.setFulfillmentReportJson(report);
                    chapterVersionRepository.save(v);
                });
            });
        } catch (Exception ex) {
            log.warn("generation fulfillment failed version={}: {}", versionId, ex.getMessage());
        }
    }
}
