package com.mythosforge.narrative;

import com.mythosforge.project.Project;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * 按全书 {@link StoryPhase} 拦截「开新坑」类写操作（子文本、新故事线）。
 */
@Component
public class NarrativePhaseGuard {

    public void assertAllowNewSubtext(Project project, int referenceChapter) {
        assertAllowNewSubtext(project, referenceChapter, "子文本");
    }

    public void assertAllowNewSubtext(Project project, int referenceChapter, String what) {
        StoryPhasePolicy policy = policyFor(project, referenceChapter);
        if (!policy.allowNewSubtext()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前全书处于「" + policy.phase().displayName() + "」（进度 "
                            + percent(policy.progressRatio())
                            + "），不可新增"
                            + what
            );
        }
    }

    public void assertAllowNewStoryline(Project project, int referenceChapter) {
        StoryPhasePolicy policy = policyFor(project, referenceChapter);
        if (policy.phase() == StoryPhase.CONVERGENCE || policy.phase() == StoryPhase.FINALE) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "当前全书处于「" + policy.phase().displayName() + "」，不可新建故事线，请收束既有汇合与里程碑"
            );
        }
    }

    public static StoryPhasePolicy policyFor(Project project, int referenceChapter) {
        int target = project.getTargetChapters() != null && project.getTargetChapters() > 0
                ? project.getTargetChapters()
                : 100;
        int ch = Math.max(1, referenceChapter);
        return StoryPhasePolicy.forChapter(ch, target);
    }

    private static String percent(double ratio) {
        return Math.round(ratio * 100) + "%";
    }
}
