package com.mythosforge.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 紧急暂停自动驾驶（原因可选）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmergencyPauseBody(String reason) {}
