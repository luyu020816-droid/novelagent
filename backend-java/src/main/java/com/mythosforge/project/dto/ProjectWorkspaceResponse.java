package com.mythosforge.project.dto;

import java.util.List;

/** {@code GET .../workspace}：当前选题、当前初始化快照、两类列表供前端渲染。 */
public record ProjectWorkspaceResponse(
        String selectedGenreContractId,
        String selectedStoryContractId,
        List<GenreContractListItem> genreContracts,
        List<StoryInitListItem> storyInits
) {
}
