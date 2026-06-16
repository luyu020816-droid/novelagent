package com.mythosforge.chapter.dto;

/** 番茄编辑点评：可选带正文；为空则后端取本章最新版本正文。 */
public record ChapterFanqieReviewRequest(String chapterText) {}
