package com.mythosforge.story.dto;

/** 追加一行到作者意图（全书长期约束），不覆盖原有全文。 */
public record StoryGovernanceAppendIntentRequest(String line) {}
