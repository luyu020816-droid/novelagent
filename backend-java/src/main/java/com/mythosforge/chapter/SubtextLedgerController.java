package com.mythosforge.chapter;

import com.mythosforge.chapter.dto.SubtextLedgerCreateRequest;
import com.mythosforge.chapter.dto.SubtextLedgerItemResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 子文本账本（与滚动摘要解耦的短悬念条目）。 */
@RestController
@RequestMapping("/api/projects/{projectId}/subtext")
public class SubtextLedgerController {

    private final SubtextLedgerService subtextLedgerService;

    public SubtextLedgerController(SubtextLedgerService subtextLedgerService) {
        this.subtextLedgerService = subtextLedgerService;
    }

    @GetMapping
    public List<SubtextLedgerItemResponse> list(@PathVariable String projectId) {
        return subtextLedgerService.listByProject(projectId).stream().map(SubtextLedgerItemResponse::from).toList();
    }

    @PostMapping
    public SubtextLedgerItemResponse create(
            @PathVariable String projectId,
            @Valid @RequestBody SubtextLedgerCreateRequest body
    ) {
        return SubtextLedgerItemResponse.from(subtextLedgerService.create(projectId, body));
    }

    public record ConsumeBody(int consumedAtChapter) {}

    @PostMapping("/{entryId}/consume")
    public SubtextLedgerItemResponse consume(
            @PathVariable String projectId,
            @PathVariable String entryId,
            @Valid @RequestBody ConsumeBody body
    ) {
        return SubtextLedgerItemResponse.from(
                subtextLedgerService.markConsumed(projectId, entryId, body.consumedAtChapter())
        );
    }
}
