package com.mythosforge.project;

import com.mythosforge.chapter.ChapterCommitEntity;
import com.mythosforge.chapter.ChapterCommitRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Day 15：将已定稿 Accept 章节合成单文件 Markdown。 */
@Service
public class ProjectBookExportService {

    private final ChapterCommitRepository chapterCommitRepository;
    private final Path exportRoot;

    public ProjectBookExportService(
            ChapterCommitRepository chapterCommitRepository,
            @Value("${mythosforge.export.root:exports}") String exportRoot
    ) {
        this.chapterCommitRepository = chapterCommitRepository;
        this.exportRoot = Path.of(exportRoot).toAbsolutePath().normalize();
    }

    public String buildAcceptedBookMarkdown(String projectId) {
        List<ChapterCommitEntity> commits = chapterCommitRepository.findByProjectIdAndStatusOrderByChapterNoAscVersionAsc(
                projectId,
                "accepted"
        );
        if (commits.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无已 Accepted 的章节");
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# Book export\n\nproject=").append(projectId).append("\n\n---\n\n");
        for (ChapterCommitEntity c : commits) {
            String rel = c.getFinalTextPath();
            if (rel == null || rel.isBlank()) {
                continue;
            }
            Path file = exportRoot.resolve(rel).normalize();
            if (!file.startsWith(exportRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法导出路径");
            }
            if (!Files.isRegularFile(file)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "章节文件缺失: " + rel);
            }
            try {
                String body = Files.readString(file, StandardCharsets.UTF_8);
                sb.append("## 第 ").append(c.getChapterNo()).append(" 章\n\n");
                sb.append(body.trim()).append("\n\n---\n\n");
            } catch (Exception e) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "读取章节失败: " + e.getMessage());
            }
        }
        return sb.toString();
    }

    /** 删除项目导出目录 {@code exportRoot/projectId}（若存在）。 */
    public void deleteProjectExportsQuietly(String projectId) {
        try {
            Path dir = exportRoot.resolve(projectId).normalize();
            if (!dir.startsWith(exportRoot) || !Files.isDirectory(dir)) {
                return;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                        // best-effort
                    }
                });
            }
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
