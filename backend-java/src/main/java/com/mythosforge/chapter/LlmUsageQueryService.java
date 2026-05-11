package com.mythosforge.chapter;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LlmUsageQueryService {

    private final JdbcTemplate jdbcTemplate;

    public LlmUsageQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 按项目聚合 {@code llm_usage_log}（agent_name = chapter_gen），同一章多次生成会累计。
     */
    public List<ChapterUsageAggregateRow> aggregateTokensByChapter(String projectId) {
        String sql = """
                SELECT chapter_no,
                       COUNT(*)::bigint AS call_count,
                       COALESCE(SUM(COALESCE(actual_total_tokens, estimated_total_tokens)), 0)::bigint AS total_tokens
                FROM llm_usage_log
                WHERE project_id = ? AND agent_name = 'chapter_gen' AND chapter_no IS NOT NULL
                GROUP BY chapter_no
                ORDER BY chapter_no
                """;
        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ChapterUsageAggregateRow(
                        rs.getInt("chapter_no"),
                        rs.getLong("call_count"),
                        rs.getLong("total_tokens")
                ),
                projectId
        );
    }
}
