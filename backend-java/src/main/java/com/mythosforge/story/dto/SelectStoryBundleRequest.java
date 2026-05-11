package com.mythosforge.story.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code PUT .../story/selected-bundle}：切换当前查看/使用的初始化快照。 */
public record SelectStoryBundleRequest(@NotBlank String storyContractId) {
}
