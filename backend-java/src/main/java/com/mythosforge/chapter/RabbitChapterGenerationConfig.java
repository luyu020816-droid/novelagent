package com.mythosforge.chapter;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitChapterGenerationConfig {

    /** Python Worker 消费的队列名（与 Day 14 规格一致）。 */
    public static final String CHAPTER_GENERATION_QUEUE = "chapter.generation.queue";

    @Bean
    public Queue chapterGenerationQueue() {
        return new Queue(CHAPTER_GENERATION_QUEUE, true);
    }
}
