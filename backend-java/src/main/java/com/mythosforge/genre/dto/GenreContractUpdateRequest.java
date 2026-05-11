package com.mythosforge.genre.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 更新题材方案：至少提供 rawJson 或 selectedDirection 之一。
 * 若二者皆提供，先将 rawJson 作为基底，再用 selectedDirection 覆盖其中的主推方向字段。
 */
public record GenreContractUpdateRequest(
        JsonNode rawJson,
        JsonNode selectedDirection
) {
}
