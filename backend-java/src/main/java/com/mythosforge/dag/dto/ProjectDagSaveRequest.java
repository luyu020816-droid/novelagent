package com.mythosforge.dag.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record ProjectDagSaveRequest(JsonNode dag, String label) {}
