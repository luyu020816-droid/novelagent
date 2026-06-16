package com.mythosforge.setup.dto;

import java.util.List;

public record SetupGenreProposeRequest(
        String targetPlatform,
        String genderChannel,
        List<String> preferredGenres,
        List<String> avoid,
        List<String> writingStrength,
        String riskPreference,
        String storyHook,
        Boolean uniqueDirection
) {}
