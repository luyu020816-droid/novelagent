package com.mythosforge.chapter;

/** 与 {@code generation_jobs.job_type} 取值一致；便于扩展其它异步任务类型。 */
public final class GenerationJobTypes {

    /** LangGraph 单章生成（Java 线程池 HTTP 调 Writer 同步接口）。 */
    public static final String CHAPTER_GENERATE = "chapter_generate";

    private GenerationJobTypes() {}
}
