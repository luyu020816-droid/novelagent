package com.mythosforge.setup;

import com.mythosforge.genre.dto.GenreInterviewRequest;
import com.mythosforge.genre.dto.GenreInterviewResponse;
import com.mythosforge.setup.dto.SetupApplyRequest;
import com.mythosforge.setup.dto.SetupGenreProposeRequest;
import com.mythosforge.setup.dto.SetupModeRequest;
import com.mythosforge.setup.dto.SetupProposalResponse;
import com.mythosforge.setup.dto.SetupReviseRequest;
import com.mythosforge.setup.dto.SetupStatusResponse;
import com.mythosforge.setup.dto.SetupStoryProposeRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @GetMapping("/status")
    public SetupStatusResponse status(@PathVariable String projectId) {
        return setupService.getStatus(projectId);
    }

    @PostMapping("/mode")
    public void setMode(@PathVariable String projectId, @RequestBody SetupModeRequest body) {
        setupService.setSetupMode(projectId, body != null ? body.setupMode() : null);
    }

    @PostMapping("/genre/propose")
    public SetupProposalResponse proposeGenre(
            @PathVariable String projectId,
            @RequestBody(required = false) SetupGenreProposeRequest body
    ) {
        return setupService.proposeGenre(projectId, body != null ? body : new SetupGenreProposeRequest(
                "番茄", "男频", null, null, null, "medium", null, false
        ));
    }

    @PostMapping("/genre/propose-from-interview")
    public SetupProposalResponse proposeGenreFromInterview(
            @PathVariable String projectId,
            @RequestBody GenreInterviewResponse interview
    ) {
        return setupService.proposeGenreFromInterview(projectId, interview);
    }

    @PostMapping("/genre/revise")
    public SetupProposalResponse reviseGenre(
            @PathVariable String projectId,
            @Valid @RequestBody SetupReviseRequest body
    ) {
        return setupService.reviseGenre(projectId, body);
    }

    @PostMapping("/genre/apply")
    public SetupProposalResponse applyGenre(
            @PathVariable String projectId,
            @RequestBody(required = false) SetupApplyRequest body
    ) {
        return setupService.applyGenre(projectId, body != null ? body : new SetupApplyRequest(null, false));
    }

    @PostMapping("/story/propose")
    public SetupProposalResponse proposeStory(
            @PathVariable String projectId,
            @RequestBody(required = false) SetupStoryProposeRequest body
    ) {
        return setupService.proposeStory(projectId, body != null ? body : new SetupStoryProposeRequest(null));
    }

    @PostMapping("/story/revise")
    public SetupProposalResponse reviseStory(
            @PathVariable String projectId,
            @Valid @RequestBody SetupReviseRequest body
    ) {
        return setupService.reviseStory(projectId, body);
    }

    @PostMapping("/story/apply")
    public SetupProposalResponse applyStory(
            @PathVariable String projectId,
            @RequestBody(required = false) SetupApplyRequest body
    ) {
        return setupService.applyStory(projectId, body != null ? body : new SetupApplyRequest(null, false));
    }

    @PostMapping("/narrative/propose")
    public SetupProposalResponse proposeNarrative(
            @PathVariable String projectId,
            @RequestParam(defaultValue = "false") boolean useLlm
    ) {
        return setupService.proposeNarrative(projectId, useLlm);
    }

    @PostMapping("/narrative/revise")
    public SetupProposalResponse reviseNarrative(
            @PathVariable String projectId,
            @Valid @RequestBody SetupReviseRequest body
    ) {
        return setupService.reviseNarrative(projectId, body);
    }

    @PostMapping("/narrative/apply")
    public SetupProposalResponse applyNarrative(
            @PathVariable String projectId,
            @RequestBody(required = false) SetupApplyRequest body
    ) {
        return setupService.applyNarrative(projectId, body != null ? body : new SetupApplyRequest(null, true));
    }

    @GetMapping("/proposals/{proposalId}")
    public SetupProposalResponse getProposal(@PathVariable String projectId, @PathVariable String proposalId) {
        return setupService.getProposal(projectId, proposalId);
    }

    @PostMapping("/proposals/{proposalId}/discard")
    public SetupProposalResponse discardProposal(@PathVariable String projectId, @PathVariable String proposalId) {
        return setupService.discardProposal(projectId, proposalId);
    }

    /** P0/P3：按 Setup 进度顺序生成题材 / 故事 / 结构草案（均需分别确认采纳）。 */
    @PostMapping("/propose-all")
    public java.util.Map<String, String> proposeAll(
            @PathVariable String projectId,
            @RequestBody(required = false) SetupGenreProposeRequest genreReq
    ) {
        return setupService.proposeAll(projectId, genreReq);
    }
}
