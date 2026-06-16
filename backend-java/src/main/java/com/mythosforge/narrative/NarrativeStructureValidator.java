package com.mythosforge.narrative;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 故事线 DAG 约束：沿 parent 链向上不得回到自身或形成环。
 */
public final class NarrativeStructureValidator {

    private NarrativeStructureValidator() {}

    /**
     * @param selfId      正在编辑的故事线 id（新建时为 null）
     * @param newParentId 拟设置的父故事线 id
     */
    public static void assertNoParentCycle(
            List<NarrativeStorylineEntity> allInProject,
            String selfId,
            String newParentId
    ) {
        if (newParentId == null || newParentId.isBlank()) {
            return;
        }
        if (selfId != null && newParentId.equals(selfId)) {
            throw new IllegalArgumentException("故事线不能以自己为父节点");
        }
        Map<String, String> parentById = new HashMap<>();
        for (NarrativeStorylineEntity e : allInProject) {
            if (e.getParentStorylineId() != null && !e.getParentStorylineId().isBlank()) {
                parentById.put(e.getId(), e.getParentStorylineId());
            }
        }
        String walk = newParentId;
        Set<String> seen = new HashSet<>();
        int guard = 0;
        while (walk != null && guard++ < 256) {
            if (selfId != null && walk.equals(selfId)) {
                throw new IllegalArgumentException("设置父故事线会形成环");
            }
            if (!seen.add(walk)) {
                throw new IllegalArgumentException("现有故事线父链存在环，请先修正");
            }
            walk = parentById.get(walk);
        }
    }
}
