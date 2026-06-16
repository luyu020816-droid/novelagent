package com.mythosforge.chapter.dto;

/** 保存本章动笔前摘要草稿（保存后需重新点「确认」才会允许生成正文）。 */
public record ChapterPrewritePlanSaveRequest(String planSummary) {}
