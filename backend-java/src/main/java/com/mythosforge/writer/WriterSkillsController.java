package com.mythosforge.writer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 代理 Writer：Skill 列表、Copilot 对话等 JSON 入口。 */
@RestController
@RequestMapping("/api/writer")
public class WriterSkillsController {

    private final WriterHttpService writerHttpService;

    public WriterSkillsController(WriterHttpService writerHttpService) {
        this.writerHttpService = writerHttpService;
    }

    @GetMapping("/skills")
    public JsonNode listSkills() {
        return writerHttpService.getJson("/api/writer/skills");
    }

    /** 初始化向导 / 卷纲参谋 / 章节教练 → writer-python `POST /api/writer/copilot/chat`。 */
    @PostMapping("/copilot/chat")
    public JsonNode copilotChat(@RequestBody JsonNode body) {
        return writerHttpService.postJson("/api/writer/copilot/chat", body);
    }

    /** 从样本正文生成风格约束 Markdown（代理 Writer）。 */
    @PostMapping("/style/analyze")
    public JsonNode styleAnalyze(@RequestBody JsonNode body) {
        return writerHttpService.postJson("/api/writer/style/analyze", body);
    }

    /** 自然语言意图 → 建议操作列表（代理 Writer）。 */
    @PostMapping("/agent/intent-preview")
    public JsonNode agentIntentPreview(@RequestBody JsonNode body) {
        return writerHttpService.postJson("/api/writer/agent/intent-preview", body);
    }
}
