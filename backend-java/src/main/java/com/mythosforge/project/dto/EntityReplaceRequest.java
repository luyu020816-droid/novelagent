package com.mythosforge.project.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** POST .../entities/replace：全书替换专名（越长字符串优先）。 */
public record EntityReplaceRequest(
        @NotEmpty(message = "replacements 不能为空") List<@Valid EntityReplacement> replacements
) {

    public record EntityReplacement(
            @NotBlank(message = "from 不能为空") String from,
            String to
    ) {}
}
