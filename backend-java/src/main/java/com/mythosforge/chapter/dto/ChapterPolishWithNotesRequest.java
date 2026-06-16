package com.mythosforge.chapter.dto;

/** 合并番茄编辑意见 + 作者意见，交给润色模型改稿（单轮，非整条 LangGraph）。 */
public record ChapterPolishWithNotesRequest(
        String chapterText,
        String tomatoReview,
        String authorNotes
) {}
