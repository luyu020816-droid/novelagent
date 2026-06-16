# MythosForge vs PlotPilot 差距对照

> PlotPilot 代码参考：`PlotPilot-master/`（单体 Python + SQLite + 可选 Tauri）。  
> MythosForge：`novel/`（Java API + PostgreSQL + React + Writer LangGraph）。

## 一句话

| | PlotPilot | MythosForge |
|---|-----------|-------------|
| 定位 | **重型创作引擎 + 可视化 DAG + 提示词广场** | **产品化分层 + Setup 人工闸门 + Java 治理** |
| 优势 | 节拍/记忆/定稿后管线/评测/节点生态 | 多租户 PG、版本定稿、Setup 提案、前后端分离易扩展 |

---

## 架构与产品

| 维度 | PlotPilot | MythosForge | 差距 |
|------|-----------|-------------|------|
| 运行时 | 单体 Python，SQLite 主库 | Java 8080 + Writer 8000 + PG | MF 运维复杂，PP 部署简单 |
| 前端 | Tauri 桌面 + 内嵌 DAG 画布 | React Web + Setup 向导 + **Workflow DAG 画布**（`/projects/:id/workflow`） |
| 初始化 | 工作流内嵌 / Bible | **Setup**：题材→故事(init-novel 20 章)→结构 | MF 人工 apply 更清晰；PP 自动化更深 |
| 章节状态机 | 章节实体 + 多状态 | `chapter_versions` + accept/reject + commits | MF 审计/回滚友好 |
| 提示词 | **CPMS 广场**，节点级 `cpms_node_key` | `prompts/*.md` + Skill 库 | PP 可运营提示词；MF 偏研发改文件 |

---

## 写章流水线（Harness）

| 维度 | PlotPilot | MythosForge |
|------|-----------|-------------|
| 编排 | **36+ 可选 DAG 节点**（context / exec / val / gateway / anti-ai / planning…） | **PlotPilot 风格 DAG ~25 实例节点**（LangGraph 配置化编译）；默认链含 review_* + gw_circuit 硬门禁 |
| 节拍 | **exec_beat / planning_beat_sheet**，按节拍流式拼章 | 无 beat sheet，scene_director 轻量场景规划 |
| 成稿 | exec_writer + Anti-AI 多层协议（行为协议、状态锁、白名单） | ghostwriter + stylist；`anti_ai` 重写模式较简 |
| 审查 | 多 val_* 节点 + 六维 + 专用 anti_ai 链 | **critic JSON + 规则审阅**（timeline/storyline/consistency）并入 **gw_circuit** 硬门禁 |
| 重试 | gateway 节点族 | decision_gate 规则 + retry≤3 |
| Token | **T0–T3 分级预算** + MemoryEngine 注入槽 | tiktoken **Priority 裁剪** + story_canon |

---

## 记忆与检索

| 维度 | PlotPilot | MythosForge |
|------|-----------|-------------|
| 滚动记忆 | **MemoryEngine**：FACT_LOCK、COMPLETED_BEATS、REVEALED_CLUES | `chapter_commits.summary` → `historySummaries` |
| 向量 | ChromaDB 等，与叙事同步一体 | **Qdrant**，accept 后异步 sync；Curator **Top3 + cosine 阈值** |
| 图谱 | SQLite 三元组 + KG 推断 + 因果边/叙事债 | **Neo4j** lore + PG 叙事结构；伏笔回收 pass |
| 读路径 | 统一 ContextAssembler | Curator 组装；`memory_summaries` 仅双写报表 |

---

## 定稿后（Aftermath）

| 维度 | PlotPilot | MythosForge |
|------|-----------|-------------|
| 入口 | `ChapterAftermathPipeline` **一条管线** | **`AftermathPipelineService`**：Writer sync aftermath + Java 异步向量/叙事/履约 |
| 叙事落库 | 摘要/事件/埋线/三元组/伏笔/因果/人物突变/债务 等 | summary JSON + Neo4j lore bundle + narrative checkpoint |
| 文风 | chapter_style_scores | 无独立文风评分表 |
| 评测 | 工作流级 eval、审计脚本丰富 | `eval_chapter_qa.py` 规则抽检 + `smoke_stack` |

---

## 质量与合规

| 能力 | PlotPilot | MythosForge |
|------|-----------|-------------|
| 敏感词 | 依提示词/审查节点 | **AC（pyahocorasick）** + `content_compliance` 维度 |
| 套话疲劳 | Anti-AI + val 节点 | `fatigue_scanner` + Skill 配置 |
| 内容治理 | 协议化 P1–P5 | Story Canon + authorIntent + nonNegotiables |
| 批处理审计 | 成熟 | `batch_sensitive_audit.py`（Pandas） |

---

## 测试与工程化

| | PlotPilot | MythosForge |
|---|-----------|-------------|
| 单测 | **大量** domain/application/infrastructure | Python DAG/网关/Golden + Java WebMvc；`smoke_stack` |
| E2E | 工作流级 | [manual-e2e-checklist.md](manual-e2e-checklist.md) 手测清单 |
| CI | 项目内 pytest 体系 | **java-tests + python-tests + golden eval + frontend-build + smoke-offline** |

---

## 建议的 P2 追赶顺序（按面试/产品价值）

1. **Beat / 场景节拍**：planner 输出 beats → ghostwriter 分段生成（对齐 PP `exec_beat`）。
2. **定稿 aftermath 扩展**：因果边、叙事债、文风分（对齐 PP pipeline 后半段，可仍走 Java 编排）。
3. **MemoryEngine -lite**：从 summary 抽 FACT_LOCK / 已完成节拍注入 Curator（不必一次做完 PP V8）。
4. **评测集**：Golden 8 章样例 + `eval_critic_dimensions --min-dimension`（CI 门禁）。
5. **可选**：CPMS 式提示词版本表（PG）；**DAG 画布已落地**。

---

## 面试怎么说

- **同**：都是「生成 + 多层 QA + 记忆 + 定稿闭环」；都强调人工 accept 与结构化契约。  
- **异**：PlotPilot 强在 **引擎厚度与节点生态**；MythosForge 强在 **Java 治理、Setup 闸门、PG 版本、AC 合规、前后端分离**。  
- **诚实缺口**：beat sheet 深度、定稿后叙事债/文风分、CPMS 提示词广场——P2 已列优先级；**DAG/规则门禁/Golden CI 已闭环**。
