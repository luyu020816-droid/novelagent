# MythosForge 15 天执行计划 v2：可落地写小说系统版

> 文件用途：每天开始开发前，把本文件交给 Cursor 阅读，然后告诉它“今天是 Day X，请只执行 Day X 的任务，并遵守全局规则”。  
> v2 目标：在不推翻 Day 1–6 主干的前提下，从 Day 7 开始升级为一个真正能辅助长篇小说创作的系统：能连续生成、能审稿、能重写、能记忆、能召回、能导出，并逐步引入 LangGraph / RAG / GraphRAG / 异步任务 / Token ROI。  
> **Day 1–6**：✅ **已闭环完成**（里程碑摘要见 **§9**；流程与接口以 **§2.1、§3.1、§8** 及仓库实现为准）。  
> **流程现状**：题材与初始化已支持 **SSE 流式**、**工作区选题/多快照**、**题材方案单条详情/手动保存/删除**（详见 §2.1）；下文 Day 7+ 在此基础上增量实现。  
> 核心原则：不要为了技术而技术。所有架构升级都必须服务于“小说能持续写下去、设定不崩、作者能控制、结果能落地”。

---

## 0. Cursor 使用方式

### 0.1 每天给 Cursor 的固定开场

每天开始时，直接复制下面这段给 Cursor：

```text
请先完整阅读 docs/02_mythosforge_15day_mvp_execution_v2.md。

今天是 Day {X}。

重要前提：
1. Day 6 及之前 **已完成并冻结**：原则上不要重构、不要大改。
2. 如果发现 Day 1-6 有阻塞当前 Day 的 bug，只允许做最小修复。
3. 今天只执行 Day {X} 的任务，不要提前实现后面天数的功能。
4. 所有 LLM 调用必须经过 writer-python 的 LLM Gateway。
5. PostgreSQL 仍然是事实源。
6. Accepted Chapter Commit 才能更新 summary / Qdrant / Neo4j。
7. Rejected Chapter 只能记录失败版本，不能污染长期记忆。
8. 先保证小说生成闭环可用，再追求架构漂亮。

今天结束时必须给我：
1. 已完成的文件列表
2. 新增/修改的接口
3. 新增/修改的数据表或字段
4. LangGraph / Agent 流程变化
5. 如何本地启动
6. 如何验收 Day {X}
7. 还没完成的风险点
```

### 0.2 每天开始前让 Cursor 先检查

```text
请先检查：
1. docker-compose 是否能启动。
2. backend-java 是否能编译。
3. writer-python 是否能启动。
4. frontend 是否能启动。
5. PostgreSQL migration 是否成功。
6. Day 6 及之前的功能是否仍可用（含流式、工作区、题材单条维护）：
   - 创建项目 / 项目列表 / 项目详情
   - 题材：偏好 / 故事线 SSE、路径 B 采访、落库、列表与选题；**单条**详情 GET、手动 PUT、`DELETE` + 前端 Modal/删除确认
   - 初始化：SSE、`persisted` 落库、多快照、选中快照刷新可加载
   - 生成前 20 章 Chapter Contract（按快照绑定 `story_contract_id`）
   - 前端能查看章纲与大纲（初始化页）
7. 如果有阻塞，只做最小修复，不要顺手重构。
```

### 0.3 每天结束时 Cursor 必须输出的报告格式

```markdown
# Day X 完成报告

## 1. 今日完成
- ...

## 2. 新增文件
- ...

## 3. 修改文件
- ...

## 4. 新增 / 修改接口
- ...

## 5. 新增 / 修改数据表或字段
- ...

## 6. LangGraph / Agent 流程变化
- ...

## 7. 本地启动方式
```bash
...
```

## 8. Day X 验收步骤
1. ...
2. ...
3. ...

## 9. 未完成 / 风险
- ...

## 10. 明天建议
- ...
```

### 0.4 文档与实现同步（约定）

凡在**已有实现上的增量修改**（接口、表结构、前端流程、SSE 事件等），助手应在同一轮或紧随其后的提交中**同步更新本仓库相关 Markdown**，至少包含：

- **`15plan.md`**：§2.1 当前已实现、§3.1 验收清单、§7 表字段、§8 API 草案中与变更相关的段落。
- 若存在 **`README.md`**、`docs/*.md`（如 SSE 协议、讲师串讲）且内容受本次改动影响，一并修订，避免「代码已是新版本、文档仍描述旧流程」。

---

## 1. v2 项目总目标

MythosForge v2 的目标不是只做一个“AI 写一章”的 Demo，而是做一个真正可落地的长篇小说创作系统。

它必须解决：

```text
1. 能连续写：第 N 章能承接前文摘要、人物状态、伏笔和世界规则。
2. 不乱写：不能随便改设定、崩人设、乱用金手指、提前揭秘。
3. 可控制：作者能人工干预，要求重写、加强冲突、修改节奏。
4. 可追踪：每一版章节、每一次审稿、每一次重写都有记录。
5. 可记忆：Accepted 章节才能进入 summary / Qdrant / Neo4j。
6. 可召回：写新章节前能召回相关旧摘要、世界规则、人物关系、伏笔。
7. 可落地：最终能导出完整 Markdown，而不是只停留在聊天窗口。
8. 可监控：知道每个 Agent / Node 花了多少 token，重试浪费多少成本。
```

---

## 2. v2 核心创作闭环

最终系统应该形成下面的闭环：

```text
创建项目
  ↓
题材方案（二选一或并行生成多份，均落库）
  ├─ 路径 A：偏好表单 → SSE 流式题材流水线
  └─ 路径 B：一两句故事线 storyHook → 同一套 SSE 流水线（输出须与故事线对齐）
  ↓
在项目详情「已保存的题材方案」中选定一份 → projects.selected_genre_contract_id
  ↓
初始化小说（SSE）→ Novel Seed + Story Contract + 第一卷大纲 + 前 20 章 Chapter Contract（按 story_contract_id 分区存快照）
  ↓
在初始化页「已保存的初始化快照」中可选切换查看 → projects.selected_story_contract_id
  ↓
Novel Seed（快照）
  ↓
Story Contract（快照）
  ↓
前 20 章 Chapter Contract（绑定该次 story_contract_id）
  ↓
Context Pack / Graph State Hydration
  ↓
LangGraph 编排
  ↓
ContextCuratorNode
  ↓
BudgetNode
  ↓
PlannerNode
  ↓
GhostwriterNode
  ↓
CriticNode
  ↓
Fail → GhostwriterNode 重写
  ↓
Pass → StylistNode
  ↓
SummaryWriterNode
  ↓
Accepted Commit
  ↓
Markdown Export
  ↓
Qdrant Memory
  ↓
Neo4j Lore Keeper
  ↓
下一章继续生成
```

但注意：这个最终闭环不是 Day 7 一天完成，而是 Day 7-15 分阶段完成。

### 2.1 当前已实现（Day 1–6 ✅：流式 + 工作区 + 题材单条维护）

以下为截至本次迭代后**真实线上流程**，后续 Day 7+ 任务应在此基准上增量迭代。

**端到端流程**

1. **前端**：项目列表 → 项目详情页 →（可选）Story 初始化页；Vite 将 `/api` 代理到 Java。
2. **题材**：详情页路径 A「偏好推荐」（SSE）；路径 B 默认 **多轮互动采访**（`POST .../genre/interview`，JSON 往返），可选「一句话 SSE」。路径 B 采访完成后将 Novel Seed 形 JSON 写入 `novel_seed_contracts`；用户可用确认摘要再跑 `from-story/stream` 生成题材合同。
3. **Java 网关**：`WriterSseProxyService` 用 `RestClient` 消费 Writer 的 `text/event-stream`，转发到 `SseEmitter`；控制器在返回 `SseEmitter` 后由线程池异步执行透传，避免响应被缓冲到最后才刷新。
4. **Writer**：`LLMGateway` 统一 `stream=True`，经队列线程向 `StreamingResponse` 产出 SSE；题材请求体可含可选字段 **`storyHook`**（camelCase），注入 Scout / Strategist / Scorer 的用户消息块。
5. **持久化与选择**：每条题材方案写入 `genre_decision_contracts`（含 `source`：`preference` | `story_hook`，及 `story_hook_text`）；项目 **`selected_genre_contract_id`** 决定初始化时使用哪份题材（未选时默认自动跟进首次生成的那份，仍可改选）。
6. **题材方案单条维护（Day 1–6 冻结区内已落地）**：`GET/PUT/DELETE .../genre/{contractId}`（完整 `raw_json`、手动覆盖 `rawJson`/`selectedDirection`；删除时若曾为选定方案则清空 `selected_genre_contract_id`）；前端列表「详情/编辑」Modal + 「删除」二次确认后刷新 `workspace`。
7. **初始化**：仅允许在已选定（或可解析到的）题材下进行；SSE 流水线产出完整包后 Java **persist**，并写入 **`story_contracts.novel_seed_contract_id`、`first_volume_outline`**；章纲写入 **`chapter_contracts.story_contract_id`**（同一项目可保留多次初始化快照，不再用「删光再写」覆盖旧快照）。
8. **刷新可恢复**：`GET /api/projects/{id}/workspace` 返回题材列表与初始化快照列表及当前选中 ID；`GET .../story/selected-bundle` 用于初始化页加载当前选中快照的正文与章纲。

**关键接口（已实现，草案 §8 中有部份为未来预留）**

- `GET /api/projects/{projectId}/workspace`
- `POST .../genre/recommend/stream`、`POST .../genre/recommend/from-story/stream`、`POST .../genre/interview`（路径 B 采访）
- `PUT .../genre/selected-contract`
- `GET .../genre/{contractId}`、`PUT .../genre/{contractId}`、`DELETE .../genre/{contractId}`（单条题材方案 CRUD 补齐）
- `POST .../story/init/stream`、`GET .../story/selected-bundle`、`PUT .../story/selected-bundle`
- Writer：`POST /api/writer/genre/interview`、`POST /api/writer/genre/recommend/stream`、`POST /api/writer/init-novel/stream`（及非流式兼容路径仍存在）

---

## 3. 不可动原则：Day 1–6 已完成（冻结区）

Day 1–6 **已全部交付**，下列为主干摘要（详细验收见 §3.1；表结构 §7；接口 §8）。**禁止**无任务重写；只允许 §3.2 所述最小修复。

```text
✅ Day 1：项目骨架与本地基础设施（前后端、Java、Writer、Docker/DB 启动链）
✅ Day 2：数据库基础表与 Java / Python 通信（Flyway、JPA、HTTP 调用 Writer）
✅ Day 3：LLM Gateway、Prompt Registry、Schema 校验（流式统一出口）
✅ Day 4：题材 Genre Decision（SSE 偏好/故事线、路径 B `/genre/interview`、落库 source/story_hook_text、选题；单条 GET/PUT/DELETE + 前端 Modal）
✅ Day 5：Novel Seed 与 Story Contract（初始化 SSE、`novel_seed_contract_id`、first_volume_outline）
✅ Day 6：前 20 章 Chapter Contract（chapter_contracts.story_contract_id、多套快照、workspace / selected-bundle）
```

从 Day 7 开始只在上述冻结成果上增量升级。

### 3.1 Day 1-6 已完成能力必须继续可用

```text
1. 创建项目、查看项目列表与详情（含 Writer 探测块）。
2. 题材方案：
   - 偏好表单流式生成并落库；
   - 故事线（storyHook）流式生成并落库；
   - 路径 B 多轮采访（`POST .../genre/interview`）与采访完成后 Novel Seed 快照；
   - 多份方案列表、选题（写入 selected_genre_contract_id）后再进入初始化；
   - 列表行：**详情/编辑**（Modal，PUT 保存 raw_json）、**删除**（DELETE + 确认，`workspace` 刷新）。
3. 初始化流水线（SSE）：Novel Seed + Story Contract + 第一卷大纲 + 前 20 章 Chapter Contract，落库并更新 selected_story_contract_id。
4. 工作区接口：workspace / 选中题材 / 选中初始化快照 / selected-bundle 加载。
5. 前端查看章纲（Chapter Contract 列表组件）及 Story Contract 主要字段；大纲文本来自持久化的 first_volume_outline。
6.（若章纲 JSON 内含）查看每章：
   - chapter_goal
   - must_cover
   - forbidden_moves
   - payoff
   - cliffhanger
```

### 3.2 Day 1-6 只能做最小修复

允许：

```text
1. 修 import 错误。
2. 修 schema 对不上。
3. 修字段缺失导致后续功能无法接入。
4. 给已有表增加必要字段。
5. 给已有 service 增加新方法。
```

禁止：

```text
1. 重写 Day 1-6 的核心架构（Java 网关 + Writer + PostgreSQL 事实源）。
2. 无故删除或改名已在 §2.1 / §8 中列出的已实现 API（新增向后兼容端点可以）。
3. 不经评估地大改项目详情 / 初始化页导致工作区选题与 SSE 验收路径断裂。
4. 重做 Story Contract / Chapter Contract 的 Writer 语义（除非 Day 7+ 明确任务要求且做好迁移）。
```

---

## 4. 全局架构边界

### 4.1 backend-java

负责：

```text
1. 用户入口 API。
2. 项目管理与工作区（workspace、题材/初始化快照选中）。
3. PostgreSQL 事实源。
4. 调用 writer-python（含阻塞 JSON 与 SSE 透传）。
5. SSE：`SseEmitter` + 异步线程透传 Writer 流（题材 / 初始化）。
6. 保存 chapter_versions。
7. 保存 chapter_commits。
8. 保存 memory_summaries。
9. 保存 generation_jobs。
10. 保存 llm_usage_log 查询聚合。
11. 后续负责异步任务、进度查询、Token ROI API。
```

Java 不负责复杂 Agent 推理。

### 4.2 writer-python

负责：

```text
1. Agent / Node 执行与编排（Day 7+ 引入 LangGraph；当前题材与初始化仍为 Python 同步流水线 + SSE）。
2. Prompt 管理。
3. LLM Gateway（统一 stream=True，可向 SSE 透出 llm_delta）。
4. JSON Schema 校验与 repair。
5. 题材与初始化：StreamingResponse（text/event-stream）及事件协议。
6.（Day 7+）Context Pack 组装。
7.（Day 7+）Token Budget 裁剪。
8.（Day 7+）Qdrant / Neo4j 写入和召回。
9.（Day 7+）Summary / Lore Keeper。
10.（Day 7+）Python Worker 异步消费队列。
```

### 4.3 frontend

负责：

```text
1. 展示项目列表、创建项目、项目详情（题材双路径 SSE 日志、方案列表与选题）。
2. Story 初始化页：运行初始化 SSE、快照列表与切换、`selected-bundle` 恢复展示。
3. 展示 Story Contract / Novel Seed / 大纲 / Chapter Contract。
4.（Day 7+）点击生成章节。
5.（Day 7+）展示 Context Pack。
6.（Day 7+）展示 Scene Plan。
7.（Day 7+）展示正文。
8.（Day 7+）展示 Critic Report。
9.（Day 7+）展示版本列表。
10.（Day 7+）输入人工重写指令。
11.（Day 7+）展示任务进度。
12.（Day 7+）展示 Token ROI。
13.（Day 7+）导出 Markdown。
```

---

## 5. 核心目录结构增量

Day 7 后建议新增：

```text
writer-python/app/
  graph/
    chapter_graph.py
    graph_runner.py
    checkpoints.py

  nodes/
    context_curator_node.py
    budget_node.py
    planner_node.py
    ghostwriter_node.py
    critic_node.py
    stylist_node.py
    summary_writer_node.py
    lore_keeper_node.py

  schemas/
    graph_state.py
    context_pack.py
    review.py
    memory.py
    graph.py

  services/
    chapter_generation_service.py
    chapter_rewrite_service.py
    summary_service.py
    token_budget_service.py
    qdrant_store.py
    neo4j_store.py
    graph_callback_client.py
    queue_worker.py

backend-java/src/main/java/com/mythosforge/
  chapter/
    ChapterController.java
    ChapterService.java
    ChapterVersion.java
    ChapterVersionRepository.java

  commit/
    ChapterCommit.java
    ChapterCommitRepository.java
    ChapterCommitService.java

  memory/
    MemorySummary.java
    MemorySummaryRepository.java
    MemorySummaryService.java

  job/
    GenerationJob.java
    GenerationJobController.java
    GenerationJobService.java
    GenerationJobRepository.java

  token/
    TokenUsageController.java
    TokenUsageService.java
    dto/

  graph/
    GraphController.java
    dto/
```

---

## 6. 核心状态对象：ChapterGenerationState

从 Day 7 开始，章节生成统一围绕 `ChapterGenerationState` 流动。

建议文件：

```text
writer-python/app/schemas/graph_state.py
```

建议字段：

```python
class ChapterGenerationState(BaseModel):
    project_id: str
    chapter_no: int
    job_id: str | None = None

    story_contract: dict
    chapter_contract: dict

    context_pack: dict = Field(default_factory=dict)
    scene_plan: dict | None = None
    chapter_text: str | None = None
    critic_report: dict | None = None
    styled_text: str | None = None
    summary: dict | None = None

    accepted: bool = False
    rejected: bool = False

    retry_count: int = 0
    max_retries: int = 1
    critic_feedback: list[dict] = Field(default_factory=list)
    human_instruction: str | None = None

    current_stage: str | None = None
    errors: list[str] = Field(default_factory=list)

    token_budget: dict = Field(default_factory=dict)
    token_usage: list[dict] = Field(default_factory=list)

    qdrant_recall: list[dict] = Field(default_factory=list)
    graph_recall: list[dict] = Field(default_factory=list)
```

### 6.1 State 设计原则

```text
1. State 是节点之间传递的唯一上下文。
2. Node 只能读取 State，返回增量更新。
3. 不要在 Node 内直接读写太多全局变量。
4. 每个 Node 的输入输出都必须有 Pydantic Schema。
5. 所有 LLM 调用必须通过 LLM Gateway。
6. 每个 Node 完成后尽量记录 current_stage。
7. accepted 后才能触发 Summary / Qdrant / Neo4j。
```

---

## 7. 数据库增量原则

原有核心表继续使用：

```text
projects
genre_decision_contracts
novel_seed_contracts
story_contracts
chapter_contracts
generation_jobs
chapter_versions
chapter_commits
memory_summaries
llm_usage_log
```

**已在 Flyway V5 落地的字段（工作区与多快照）**

```text
projects.selected_genre_contract_id
projects.selected_story_contract_id
genre_decision_contracts.source
genre_decision_contracts.story_hook_text
story_contracts.novel_seed_contract_id
story_contracts.first_volume_outline
chapter_contracts.story_contract_id   -- 章纲归属某次初始化快照；唯一约束 (story_contract_id, chapter_no)
```

Day 7 之后可以继续在上述表或其它表逐步增加字段。

### 7.1 chapter_versions 建议补充字段

```sql
alter table chapter_versions
add column if not exists context_pack_json jsonb,
add column if not exists graph_state_json jsonb,
add column if not exists node_trace_json jsonb,
add column if not exists human_instruction text;
```

### 7.2 chapter_commits 建议补充字段

```sql
alter table chapter_commits
add column if not exists summary_id varchar(64),
add column if not exists qdrant_written boolean default false,
add column if not exists neo4j_written boolean default false;
```

### 7.3 generation_jobs 建议补充字段

```sql
alter table generation_jobs
add column if not exists node_trace_json jsonb,
add column if not exists retry_count int default 0,
add column if not exists total_tokens int default 0;
```

---

## 8. API 草案 v2

### 8.1 Java API

```http
GET  /api/health

POST /api/projects
GET  /api/projects
GET  /api/projects/{projectId}
GET  /api/projects/{projectId}/detail
GET  /api/projects/{projectId}/workspace

POST /api/projects/{projectId}/genre/recommend
POST /api/projects/{projectId}/genre/recommend/stream          # SSE，Accept: text/event-stream
POST /api/projects/{projectId}/genre/recommend/from-story/stream
POST /api/projects/{projectId}/genre/interview                # 路径 B：chat_history JSON；complete 时落库 novel_seed_contracts
PUT  /api/projects/{projectId}/genre/selected-contract          # body: { "genreContractId": "..." }
GET    /api/projects/{projectId}/genre/{contractId}           # 单条详情（含完整 raw_json）
PUT    /api/projects/{projectId}/genre/{contractId}             # body: rawJson / selectedDirection（至少其一）；手动覆盖方案 JSON
DELETE /api/projects/{projectId}/genre/{contractId}           # 删除方案；若曾为选定方案则清空 project.selected_genre_contract_id

POST /api/projects/{projectId}/story/init
POST /api/projects/{projectId}/story/init/stream               # SSE
GET  /api/projects/{projectId}/story/selected-bundle           # 404 表示当前未选或未落库
PUT  /api/projects/{projectId}/story/selected-bundle           # body: { "storyContractId": "..." }

POST /api/projects/{projectId}/chapters/{chapterNo}/generate # SSE（Day 7 LangGraph）；body 无；依赖当前选题快照与章纲

# 以下为 Day 7+ 草案，尚未实现或与路径不一致处以实现为准
GET  /api/projects/{projectId}/story-contract
GET  /api/projects/{projectId}/chapter-contracts

GET  /api/projects/{projectId}/chapters/{chapterNo}
GET  /api/projects/{projectId}/chapters/{chapterNo}/versions
POST /api/projects/{projectId}/chapters/{chapterNo}/rewrite
POST /api/projects/{projectId}/chapters/{chapterNo}/accept

GET  /api/projects/{projectId}/memory-summaries
GET  /api/projects/{projectId}/graph
GET  /api/projects/{projectId}/token-usage
GET  /api/projects/{projectId}/token-roi
GET  /api/projects/{projectId}/export/markdown

GET  /api/jobs/{jobId}
```

### 8.2 Python API

```http
GET  /health
GET  /api/writer/health

POST /api/writer/test-agent
POST /api/writer/genre/recommend
POST /api/writer/genre/recommend/stream          # SSE；请求体可含可选 storyHook（camelCase）
POST /api/writer/genre/interview                # 路径 B：互动采访 JSON（InterviewerResponse）
POST /api/writer/init-novel
POST /api/writer/init-novel/stream               # SSE

POST /api/writer/chapters/generate               # SSE（Day 7 LangGraph）；body：projectId、chapterNo、storyContract、chapterContract、recentSummaries

POST /api/writer/chapters/rewrite

POST /api/writer/summary/write
POST /api/writer/callback/node-stage
```

---

# 9. Day 1–6：✅ 已完成（压缩备忘）

本节替代「逐日展开」：**Day 1–6 不再作为待办日历**，仅作与代码对齐的验收备忘。若与实现冲突，以仓库为准。

| 阶段 | 已完成交付（与当前代码一致） |
|------|------------------------------|
| **Day 1** | Monorepo 骨架：`frontend`（Vite/React）、`backend-java`、`writer-python`；本地联调；`/api` 代理 Java |
| **Day 2** | PostgreSQL + Flyway（V1–V5）；`projects` 及生成链路核心表；Java `RestClient`/SSE 调用 Writer |
| **Day 3** | Writer：`LLMGateway`、registry、`stream=True`、题材/初始化 JSON Schema 与校验 |
| **Day 4** | 题材：`/genre/recommend/stream`、`/from-story/stream`、`/genre/interview`；`genre_decision_contracts` + `source`/`story_hook_text`；`PUT .../selected-contract`；**`GET/PUT/DELETE .../genre/{contractId}`**；前端列表 Modal 手动编辑 |
| **Day 5** | `POST .../story/init/stream`（SSE）；`novel_seed_contracts`、`story_contracts`（含 `novel_seed_contract_id`、`first_volume_outline`） |
| **Day 6** | `chapter_contracts.story_contract_id`（Flyway V5）；同一项目多套初始化快照；`GET .../workspace`、`GET/PUT .../story/selected-bundle`；初始化页展示大纲与章纲 |

**Day 7+ 开工前**：快速跑通 §3.1；**不要**无任务重写 Day 1–6 主干。

---

# Day 7：LangGraph 章节生成最小闭环

## 目标

在不改变 Day 6 及之前成果的基础上，引入 LangGraph，把“单章生成流程”改造成可追踪、可扩展的 Graph。

Day 7 只做最小闭环，不做自动重写、不做 Summary、不做 Qdrant、不做 Neo4j、不做异步队列。

## 今日必须完成

```text
1. writer-python 引入 langgraph。
2. 新增 schemas/graph_state.py，定义 ChapterGenerationState。
3. 将原章节生成逻辑改造成 Node 函数：
   - ContextCuratorNode
   - PlannerNode
   - GhostwriterNode
   - CriticNode
   - DecisionGateNode

4. 组装 StateGraph：
   START
     → ContextCuratorNode
     → PlannerNode
     → GhostwriterNode
     → CriticNode
     → DecisionGateNode
     → END

5. DecisionGateNode 判断：
   - Critic pass = true → accepted = true
   - Critic pass = false → rejected = true
   Day 7 暂时不要 Fail 回 Ghostwriter。

6. Python 端提供：
   POST /api/writer/chapters/generate

7. Java 端：
   - POST /api/projects/{projectId}/chapters/{chapterNo}/generate
   - 加载 project / story_contract / chapter_contract / recent_summaries
   - 调用 Python Graph
   - 保存 chapter_versions
   - accepted 时保存 chapter_commits
   - accepted 时导出单章 Markdown
   - rejected 时只保存失败版本，不更新 summary

8. 前端：
   - ChapterWorkspacePage 增加“生成本章”按钮
   - 展示 Context Pack
   - 展示 Scene Plan
   - 展示正文
   - 展示 Critic Report
   - 展示 Accepted / Rejected
```

## Day 7 暂时不要做

```text
1. 不要做 Critic Fail 自动回 Ghostwriter。
2. 不要做 StylistNode。
3. 不要做 SummaryWriterNode。
4. 不要做 Qdrant。
5. 不要做 Neo4j。
6. 不要做 RabbitMQ / Redis 异步。
7. 不要重构 Day 1-6。
```

## 建议 Cursor 执行顺序

```text
1. 安装 langgraph 依赖。
2. 新增 schemas/graph_state.py。
3. 新增 nodes/context_curator_node.py。
4. 新增 nodes/planner_node.py。
5. 新增 nodes/ghostwriter_node.py。
6. 新增 nodes/critic_node.py。
7. 新增 nodes/decision_gate_node.py。
8. 新增 graph/chapter_graph.py。
9. 新增 graph/graph_runner.py。
10. 修改 services/chapter_generation_service.py 使用 graph_runner。
11. 修改 api/chapter.py 暴露 /api/writer/chapters/generate。
12. Java ChapterController 调 Python。
13. Java 保存 chapter_versions / chapter_commits。
14. 前端增加生成按钮和结果展示。
15. 本地测试生成第 1 章。
```

## Day 7 验收

```text
点击“生成第 1 章”后：
1. Python 能执行 LangGraph。
2. 能看到 Context Pack。
3. 能看到 Scene Plan。
4. 能看到正文。
5. 能看到 Critic Report。
6. 能判断 accepted / rejected。
7. accepted 后能保存 chapter_commit。
8. accepted 后能导出单章 Markdown。
9. rejected 后不会写 summary / Qdrant / Neo4j。
10. Day 6 前 20 章章纲仍然可查看。
```

---

# Day 8：摘要记忆与状态注水 State Hydration

## 目标

从“能生成一章”升级为“能连续生成章节”。

Accepted 章节后生成摘要；下一章生成前把最近 3 章摘要注入 `ChapterGenerationState.context_pack`。

## 今日必须完成

```text
1. 新增 SummaryWriterNode。
2. SummaryWriterNode 只在 accepted = true 后执行。
3. 生成本章摘要，必须包含：
   - chapter_no
   - title
   - summary_text
   - key_events
   - character_state_changes
   - new_foreshadowing
   - cliffhanger

4. 摘要写入 PostgreSQL memory_summaries。
5. 修改 ContextCuratorNode：
   - 生成前查询当前章之前最近 3 章 accepted summary
   - 塞入 ChapterGenerationState.context_pack.recent_summaries

6. Graph 调整：
   START
     → ContextCuratorNode
     → PlannerNode
     → GhostwriterNode
     → CriticNode
     → DecisionGateNode
     → 如果 accepted：SummaryWriterNode
     → END

7. Java 保存 memory_summaries。
8. 前端展示最近摘要。
```

## 关键规则

```text
1. Accepted 才能写 summary。
2. Rejected 不能写 summary。
3. Summary 是后续记忆的基础，字段必须稳定。
4. 最近 3 章摘要优先进入 Context Pack。
5. 第一章 recent_summaries 为空是正常情况。
```

## 建议 Cursor 执行顺序

```text
1. 新增 schemas/memory.py。
2. 新增 prompts/summary_writer_v1.md。
3. 新增 nodes/summary_writer_node.py。
4. 新增 services/summary_service.py。
5. 修改 graph/chapter_graph.py，accepted 后进入 SummaryWriterNode。
6. 修改 Java MemorySummary entity / repository / service。
7. 修改 Java 保存 Python 返回的 summary。
8. 修改 ContextCuratorNode 读取最近 3 章摘要。
9. 前端展示 recent_summaries。
10. 手动测试连续生成第 1-3 章。
```

## Day 8 验收

```text
1. 第 1 章 accepted 后能生成 summary。
2. summary 写入 memory_summaries。
3. 第 2 章 context_pack 中包含第 1 章 summary。
4. 第 3 章 context_pack 中包含前 2 章 summary。
5. rejected chapter 不写 summary。
6. 连续生成 1-3 章，剧情能基本承接。
```

---

# Day 9：Critic 自动重写与 Human-in-the-loop

## 目标

质量控制闭环成型：Critic 失败时可自动重写；作者不满意时可人工输入意见重写。

## 今日必须完成

```text
1. 启用 ChapterGenerationState.retry_count。
2. 启用 ChapterGenerationState.critic_feedback。
3. CriticReport 必须输出：
   - pass
   - score
   - blocking
   - issues
   - rewrite_instruction

4. 实现条件边：
   - Critic pass = true → Stylist 或 Summary
   - Critic pass = false 且 blocking = true 且 retry_count < max_retries → 回到 GhostwriterNode
   - Critic pass = false 且 retry_count >= max_retries → rejected

5. 每次 Ghostwriter 输出都保存 chapter_version。
6. 每次 Critic Report 都和对应 version 绑定。
7. Java 增加人工重写接口：
   POST /api/projects/{projectId}/chapters/{chapterNo}/rewrite

8. Python 增加：
   POST /api/writer/chapters/rewrite

9. 人工重写逻辑：
   - 读取之前失败或用户不满意的 chapter state / version
   - 将 human_instruction 注入 context_pack
   - 将执行入口强制拨回 GhostwriterNode
   - 生成新版本
   - 再进入 CriticNode
```

## Day 9 是否做 Stylist？

Day 9 可以只预留 StylistNode，不强制启用。  
如果时间紧，Pass 后直接 SummaryWriterNode。  
StylistNode 放 Day 10 或 Day 11 也可以。

## 建议 Cursor 执行顺序

```text
1. 扩展 schemas/review.py 的 CriticReport。
2. 修改 prompts/critic_v1.md，要求输出 rewrite_instruction。
3. 修改 critic_node.py，写入 critic_feedback。
4. 修改 graph/chapter_graph.py：
   - add_conditional_edges
   - fail 回 GhostwriterNode
   - pass 继续 accepted 路径
5. 修改 ghostwriter_node.py，读取 critic_feedback / human_instruction。
6. 修改 Java ChapterVersion 保存多版本。
7. 增加 /chapters/{chapterNo}/rewrite。
8. 增加 Python rewrite API。
9. 前端增加人工重写输入框。
10. 前端展示版本列表 v1 / v2 / v3。
```

## Day 9 验收

```text
1. Critic 发现 blocking issue 时不会直接 accepted。
2. Graph 能自动回到 GhostwriterNode 重写。
3. retry_count 会增加。
4. 超过 max_retries 后标记 rejected。
5. 每次重写都保存新 chapter_version。
6. 用户能输入 human_instruction 触发人工重写。
7. accepted 后才写 summary。
8. rejected 不污染 memory_summaries。
```

---

# Day 10：Qdrant 向量检索 RAG 第一版

## 目标

从只依赖 PostgreSQL 最近摘要，升级为语义召回旧章节摘要、人物档案、世界规则。

## 今日必须完成

```text
1. docker-compose 启动 Qdrant 容器。
2. writer-python 接入 Qdrant。
3. 创建 collections：
   - chapter_summaries
   - character_profiles
   - world_rules
   - trope_cards

4. 实现 embedding provider：
   - 优先使用真实 embedding
   - 如果暂时没有模型，允许 mock vector / hash vector 跑通链路

5. Accepted summary 写入 Qdrant chapter_summaries。
6. Story Contract 中的人物档案写入 Qdrant character_profiles。
7. Story Contract 中的 world_rules 写入 Qdrant world_rules。
8. ContextCuratorNode 在 Graph 起点前做 Qdrant recall。
9. 将召回结果注入 context_pack.recalled_memories。
```

## 关键规则

```text
1. PostgreSQL 仍然是事实源。
2. Qdrant 只是辅助召回。
3. Accepted 才能写入 Qdrant。
4. Rejected 不能写入 Qdrant。
5. 召回内容必须带 source_type / source_id / chapter_no / score。
```

## 建议 Cursor 执行顺序

```text
1. 确认 docker-compose 中 Qdrant 可启动。
2. 新增 services/qdrant_store.py。
3. 新增 services/embedding_provider.py。
4. 写 collection 初始化逻辑。
5. Story Contract 初始化后写入 character_profiles / world_rules。
6. SummaryWriterNode accepted 后写入 chapter_summaries。
7. ContextCuratorNode 根据当前 Chapter Contract 查询 Qdrant。
8. context_pack 增加 recalled_memories。
9. 前端展示 Qdrant 召回结果。
10. 测试生成第 4-5 章。
```

## Day 10 验收

```text
1. Qdrant 容器能启动。
2. character_profiles 能写入 Qdrant。
3. world_rules 能写入 Qdrant。
4. accepted chapter summary 能写入 Qdrant。
5. 新章节生成前 context_pack 中能看到 recalled_memories。
6. rejected chapter 不写 Qdrant。
```

---

# Day 11：Token Budget 控制与 StylistNode

## 目标

防止上下文越来越长导致 LLM 上下文溢出，同时增加可选 StylistNode 提升正文可读性。

## 今日必须完成

```text
1. 引入 tiktoken。
2. 新增 BudgetNode。
3. BudgetNode 放在 GhostwriterNode 之前。
4. 统计 context_pack 的 token 数。
5. 若超限，按优先级裁剪。
6. 保证以下内容永远不裁：
   - 当前 Chapter Contract
   - forbidden_moves
   - Story Contract 核心规则
   - human_instruction

7. 新增 StylistNode：
   - 只在 Critic pass 后执行
   - 不允许改变剧情事实
   - 不允许新增重大设定
   - 只做语言润色、节奏增强、段落优化

8. Graph 调整：
   ContextCuratorNode
     → BudgetNode
     → PlannerNode
     → GhostwriterNode
     → CriticNode
     → pass → StylistNode
     → SummaryWriterNode
```

## Context Pack 裁剪优先级

从高到低：

```text
1. 当前 Chapter Contract
2. Forbidden Moves
3. Human Instruction
4. Story Contract 核心规则
5. Character Current State
6. Open Foreshadowing
7. 上一章摘要
8. 最近 3 章摘要
9. Qdrant 相似摘要
10. Trope Card
11. 更远历史摘要
```

## StylistNode 硬约束

```text
1. 不得改变剧情事件。
2. 不得改变人物决定。
3. 不得新增人物关系。
4. 不得提前回收伏笔。
5. 不得公开 forbidden_moves 中禁止公开的信息。
6. 输出必须保留原正文事实，只优化表达。
```

## 建议 Cursor 执行顺序

```text
1. 安装 tiktoken。
2. 新增 services/token_budget_service.py。
3. 新增 nodes/budget_node.py。
4. 给 ContextPackItem 增加 priority / estimated_tokens。
5. 修改 ContextCuratorNode 输出统一 item 格式。
6. 修改 graph/chapter_graph.py，在 Planner 或 Ghostwriter 前加入 BudgetNode。
7. 新增 prompts/stylist_v1.md。
8. 新增 nodes/stylist_node.py。
9. pass 后进入 StylistNode。
10. SummaryWriterNode 使用 styled_text 优先，没有则用 chapter_text。
11. 前端展示 token budget 状态和被裁剪数量。
```

## Day 11 验收

```text
1. context_pack 超长时不会直接爆上下文。
2. BudgetNode 能输出 estimated_tokens。
3. 低优先级内容会被裁剪。
4. forbidden_moves 永远保留。
5. human_instruction 永远保留。
6. Critic pass 后能进入 StylistNode。
7. StylistNode 不改变剧情事实。
8. 最终 Markdown 使用 styled_text。
```

---

# Day 12：Neo4j 图谱抽取与 Lore Keeper

## 目标

让系统不仅记住摘要，还能结构化记录人物、事件、伏笔、关系变化。

## 今日必须完成

```text
1. docker-compose 启动 Neo4j。
2. writer-python 接入 Neo4j。
3. 新增 LoreKeeperNode。
4. LoreKeeperNode 只在最终 accepted 后触发。
5. 大模型阅读最终正文，抽取：
   - Character
   - Event
   - Location
   - Item
   - Ability
   - Foreshadowing
   - Relationship Update

6. 输出 KGUpdateProposal。
7. 通过 Cypher 写入 Neo4j。
8. 每个节点和边必须带：
   - project_id
   - chapter_no
   - commit_id
   - evidence
   - confidence
```

## Neo4j 节点类型

```text
Project
Chapter
Character
Organization
Location
Item
Ability
Event
Foreshadowing
Rule
Trope
```

## Neo4j 边类型

```text
APPEARS_IN
KNOWS
TRUSTS
SUSPECTS
ENEMY_OF
PROTECTS
BETRAYS
MEMBER_OF
OWNS
USES
LOCATED_IN
CAUSES
FORESHADOWS
RESOLVED_IN
CONFLICTS_WITH
```

## 关键规则

```text
1. Accepted 才能写 Neo4j。
2. Rejected 不能写 Neo4j。
3. Lore Keeper 只做提取和结构化，不改正文。
4. Cypher 写入要幂等，避免重复节点爆炸。
5. project_id 必须作为隔离字段。
```

## 建议 Cursor 执行顺序

```text
1. 确认 docker-compose 中 Neo4j 可启动。
2. 新增 services/neo4j_store.py。
3. 新增 schemas/graph.py 中 KGUpdateProposal。
4. 新增 prompts/lore_keeper_v1.md。
5. 新增 nodes/lore_keeper_node.py。
6. accepted 后 SummaryWriterNode 之后触发 LoreKeeperNode。
7. graph_writer 将 proposal 写入 Neo4j。
8. Java 增加 GraphController 简单查询接口。
9. 前端 GraphPage 先用表格展示。
10. 生成第 5 章后检查 Neo4j 数据。
```

## Day 12 验收

```text
1. Neo4j 容器能启动。
2. accepted chapter 后 LoreKeeperNode 会执行。
3. Neo4j 中能看到主角、配角、事件、伏笔。
4. 人物之间能看到 TRUSTS / SUSPECTS / ENEMY_OF 等关系。
5. 伏笔能记录出现章节。
6. rejected chapter 不写 Neo4j。
```

---

# Day 13：GraphRAG 图谱增强上下文

## 目标

让 Neo4j 不只是展示，而是真正参与下一章生成，防止人物关系、伏笔、能力使用出现逻辑断裂。

## 今日必须完成

```text
1. 升级 ContextCuratorNode。
2. 在 Qdrant recall 之外，增加 Neo4j recall。
3. 根据当前 Chapter Contract 识别本章可能出场人物。
4. 查询 Neo4j：
   - 当前人物关系
   - hidden hostility / suspect / enemy
   - open foreshadowing
   - unresolved events
   - ability usage history
   - recent events

5. 将图谱召回结果注入：
   - context_pack.relationship_graph
   - context_pack.open_foreshadowing
   - context_pack.recent_events
   - context_pack.ability_history

6. 前端展示本章使用的 graph_context。
```

## GraphRAG 原则

```text
1. Qdrant 负责语义相似。
2. Neo4j 负责关系和状态一致性。
3. PostgreSQL 仍然是事实源。
4. GraphRAG 召回结果必须进入 Context Pack，不能直接替代 Story Contract。
5. 如果 Neo4j 没有结果，不应阻塞章节生成。
```

## 建议 Cursor 执行顺序

```text
1. neo4j_store 增加 query_relationships。
2. neo4j_store 增加 query_open_foreshadowing。
3. neo4j_store 增加 query_recent_events。
4. neo4j_store 增加 query_ability_history。
5. ContextCuratorNode 先查 PostgreSQL，再查 Qdrant，再查 Neo4j。
6. BudgetNode 对 graph_context 设置较高优先级。
7. 前端 ChapterWorkspacePage 展示 graph_context。
8. GraphPage 展示 Characters / Events / Foreshadowing / Relationships。
9. 连续生成第 6-8 章，检查人物关系是否被引用。
```

## Day 13 验收

```text
1. 下一章 context_pack 能包含 relationship_graph。
2. context_pack 能包含 open_foreshadowing。
3. context_pack 能包含 recent_events。
4. context_pack 能包含 ability_history。
5. 章节生成时能引用这些图谱上下文。
6. Neo4j 为空时系统仍能生成。
```

---

# Day 14：异步任务队列与生成进度

## 目标

从同步生成升级为工程化异步生成，前端能看到生成进度。

## 队列选择

v2 推荐使用 RabbitMQ，因为它更适合明确的任务队列语义。  
但如果当前项目已经稳定使用 Redis，也允许继续使用 Redis Streams。  
本 Day 只允许二选一，不要两个都做。

### 选择 A：RabbitMQ

```text
Java：
1. 引入 Spring AMQP。
2. 点击生成章节时：
   - 创建 generation_jobs，status = pending
   - 发送包含 job_id / project_id / chapter_no 的消息到 RabbitMQ
   - 立即返回 job_id

Python：
1. 编写长时间驻留 Worker。
2. 监听 RabbitMQ 队列。
3. 收到消息后启动 LangGraph。
4. 每流转一个 Node，回调 Java 更新 current_stage。
```

### 选择 B：Redis Streams

```text
Java：
1. 创建 generation_jobs。
2. 发送 job.created 到 Redis Stream。
3. 立即返回 job_id。

Python：
1. Worker 消费 Redis Stream。
2. 启动 LangGraph。
3. 每个 Node 更新 job progress。
```

## 今日必须完成

```text
1. Java POST /chapters/{chapterNo}/generate 改为异步。
2. 创建 generation_jobs：
   - pending
   - running
   - succeeded
   - failed

3. Python Worker 消费任务。
4. LangGraph 每个 Node 完成后回调 Java：
   - current_stage
   - progress
   - retry_count
   - error_message

5. Java 提供：
   GET /api/jobs/{jobId}

6. 前端：
   - 点击生成后立即拿到 job_id
   - 轮询 job 状态
   - 展示生成进度：
     - 上下文整理中
     - Token 预算检查中
     - 规划中
     - 主笔生成中
     - 审稿中
     - 打回重试中
     - 润色中
     - 摘要记忆中
     - 图谱写入中
     - 完成
```

## 建议 Cursor 执行顺序

```text
1. 选择 RabbitMQ 或 Redis Streams。
2. 修改 docker-compose 增加对应服务。
3. Java 增加队列依赖。
4. Java GenerationJobService 创建 job。
5. Java 发送队列消息。
6. Python 新增 worker.py。
7. Python Worker 调用 graph_runner。
8. 新增 graph_callback_client.py。
9. Java 增加 job progress update endpoint，可仅限内部调用。
10. 前端轮询 GET /api/jobs/{jobId}。
11. 刷新页面后仍能看到 job 状态。
```

## Day 14 验收

```text
1. 点击生成章节后 API 立即返回 job_id。
2. 后端 generation_jobs 中有 pending 记录。
3. Python Worker 能消费任务。
4. job 状态从 pending → running → succeeded / failed。
5. current_stage 会随着 Node 变化。
6. 前端能轮询展示进度。
7. 生成成功后能查看章节结果。
8. 刷新页面后 job 状态不丢。
```

---

# Day 15：Token ROI 监控、压测与交付

## 目标

让系统真正可演示、可评估、可交付。

Day 15 不再加大功能，重点修阻塞、压测、导出和看板。

## 今日必须完成

```text
1. Java 编写 Token 聚合 API：
   - 每章总 token
   - 每个 Node token
   - retry waste
   - critic 打回成本
   - input / output token 占比

2. 前端 Token ROI 看板：
   - 每章 token 成本
   - 各 Node 成本占比
   - Critic 打回导致的沉没成本
   - 超预算 warning

3. 连续生成前 3-5 章。
4. 检查 GraphRAG 是否起效。
5. 检查 rejected 不污染 memory / Qdrant / Neo4j。
6. 导出完整 Markdown 纯文本。
7. 写 README。
8. 写演示脚本。
9. 写 run_local.sh / reset_local.sh。
```

## Token ROI 指标

```text
1. total_tokens_by_chapter
2. total_tokens_by_node
3. retry_tokens
4. retry_waste_ratio
5. critic_fail_count
6. accepted_version_no
7. avg_tokens_per_accepted_chapter
8. context_tokens_before_trim
9. context_tokens_after_trim
10. trimmed_items_count
```

## 最终验收目标

```text
1. 启动 docker compose。
2. 启动 backend-java。
3. 启动 writer-python。
4. 启动 Python worker。
5. 启动 frontend。
6. 创建项目。
7. 输入题材偏好。
8. 获取 3 个题材候选。
9. 选择题材。
10. 初始化 Story Contract。
11. 查看前 20 章 Chapter Contract。
12. 点击生成第 1 章。
13. 看到异步生成进度。
14. 看到 Context Pack。
15. 看到 Scene Plan。
16. 看到正文。
17. 看到 Critic Report。
18. 如失败，能自动重写。
19. 如不满意，能人工重写。
20. Accepted 后能生成 Summary。
21. Accepted 后能写 Qdrant。
22. Accepted 后能写 Neo4j。
23. 第 2-5 章能召回前文摘要和图谱上下文。
24. Token ROI 看板能显示成本。
25. 能导出完整 Markdown。
```

## Day 15 不要做

```text
1. 不要再新增大模块。
2. 不要重构 Graph。
3. 不要重构数据库。
4. 不要做复杂 UI 美化。
5. 不要做 EPUB。
6. 不要做复杂图谱可视化。
7. 不要做多模型路由。
```

## Day 15 交付文件

```text
1. README.md
2. .env.example
3. docker-compose.yml
4. scripts/run_local.sh
5. scripts/reset_local.sh
6. docs/demo_script.md
7. exports/projects/{project_id}/full_novel.md
```

---

## 10. v2 优先级

### 10.1 第一优先级：绝对不能砍

```text
Story Contract
Chapter Contract
Context Pack
LangGraph 最小闭环
Ghostwriter
Critic
Accepted / Rejected
Chapter Version
Summary Memory
Human Rewrite
Markdown Export
Token Usage Log
```

### 10.2 第二优先级：尽量完成

```text
Critic 自动重写
BudgetNode
Qdrant Summary Recall
StylistNode
Generation Job 状态
Token ROI 基础看板
```

### 10.3 第三优先级：能做就做

```text
Neo4j Lore Keeper
GraphRAG
RabbitMQ / Redis Streams Worker
前端 GraphPage
复杂进度条
```

### 10.4 时间不够时砍功能顺序

```text
1. 砍复杂 UI 美化。
2. 砍复杂图谱可视化，保留表格。
3. 砍 RabbitMQ，退回同步或 DB 轮询。
4. 砍 StylistNode。
5. 砍 Neo4j GraphRAG，只保留 Qdrant。
6. 砍 Qdrant 高质量 embedding，保留 mock vector 链路。
7. 砍 Token ROI 高级图表，保留 llm_usage_log 聚合。
8. 不砍 Summary Memory。
9. 不砍 Human Rewrite。
10. 不砍 Critic。
11. 不砍 Chapter Version。
12. 不砍 Markdown Export。
```

---

## 11. 质量验收标准：判断系统是否真的能写小说

不要只看接口是否成功，要看小说连续性。

### 11.1 连续生成验收

```text
至少连续生成 5 章，并检查：
1. 第 2 章是否承接第 1 章 cliffhanger。
2. 第 3 章是否引用前 2 章关键事件。
3. 主角核心欲望是否保持一致。
4. 主角能力规则是否没有被随便改写。
5. 配角关系是否没有突然反转且无解释。
6. 世界规则是否没有冲突。
7. 新伏笔是否被记录。
8. 旧伏笔是否没有被提前乱收。
```

### 11.2 Critic 验收

```text
Critic 必须检查：
1. 是否覆盖 Chapter Contract。
2. 是否违反 Story Contract。
3. 是否违反 forbidden_moves。
4. 是否人设冲突。
5. 是否世界观冲突。
6. 是否提前揭秘。
7. 是否缺少爽点或冲突。
8. 是否不能承接下一章。
```

### 11.3 Human Rewrite 验收

```text
输入：
这一版太平，增强冲突和压迫感，但不要改变主角底层性格。

系统应该：
1. 保留原 Chapter Contract。
2. 将 human_instruction 注入 context_pack。
3. 重新生成正文。
4. 再次 Critic。
5. 保存新版本。
6. 不覆盖旧版本。
```

### 11.4 记忆污染验收

```text
1. Rejected chapter 不写 memory_summaries。
2. Rejected chapter 不写 Qdrant。
3. Rejected chapter 不写 Neo4j。
4. Accepted chapter 才写长期记忆。
5. 人工重写未 accepted 前不能污染长期记忆。
```

---

## 12. Prompt 规范

所有 Prompt 放在：

```text
writer-python/app/prompts/
```

命名：

```text
chapter_planner_v1.md
ghostwriter_v1.md
critic_v1.md
stylist_v1.md
summary_writer_v1.md
lore_keeper_v1.md
```

每个 Prompt 必须包含：

```text
1. 角色
2. 输入
3. 任务
4. 硬约束
5. 禁止事项
6. 输出 JSON Schema
7. 失败时如何返回
```

### 12.1 Ghostwriter Prompt 必须强调

```text
你只能根据 Context Pack 和 Scene Plan 写正文。
不得新增重大设定。
不得改变人物底层性格。
不得提前回收伏笔。
不得公开 forbidden_moves 中禁止公开的信息。
必须遵守 Chapter Contract。
必须承接 recent_summaries。
必须符合 style_guide。
如果存在 human_instruction，优先满足 human_instruction，但不能违反 Story Contract。
```

### 12.2 Critic Prompt 必须强调

```text
你不是普通点评者，而是章节验收器。
你必须检查：
1. 是否违反 Story Contract。
2. 是否覆盖 Chapter Contract。
3. 是否违反 Forbidden Moves。
4. 是否存在人设冲突。
5. 是否存在世界观冲突。
6. 是否提前揭秘。
7. 是否缺少爽点。
8. 是否不能承接下一章。
9. 是否忽略 human_instruction。

如果存在 blocking issue，必须输出 rewrite_instruction。
```

### 12.3 Stylist Prompt 必须强调

```text
你只能润色，不得改剧情。
你不得新增事实。
你不得改变人物关系。
你不得改变事件结果。
你不得提前揭秘。
你不得违反 forbidden_moves。
```

### 12.4 Summary Writer Prompt 必须强调

```text
你不是写读后感，而是为下一章生成可检索记忆。
必须提取：
1. 关键事件。
2. 人物状态变化。
3. 新伏笔。
4. 未解决问题。
5. cliffhanger。
6. 对下一章有用的信息。
```

### 12.5 Lore Keeper Prompt 必须强调

```text
你是小说世界观管理员。
你只抽取正文中明确出现或强烈暗示的信息。
不要脑补。
每个节点和边都必须有 evidence。
confidence 低于 0.6 的关系不要写入正式图谱。
```

---

## 13. 最终演示脚本

```text
1. 启动 docker compose。
2. 启动 backend-java。
3. 启动 writer-python。
4. 启动 Python worker。
5. 启动 frontend。
6. 打开前端。
7. 创建项目《旧城工程师》。
8. 输入题材偏好：
   - 平台：番茄
   - 男频
   - 爽点
   - 反转
   - 避免强虐、后宫

9. 系统输出 3 个题材候选。
10. 选择都市科技爽文。
11. 初始化小说。
12. 查看 Story Contract。
13. 查看前 20 章 Chapter Contract。
14. 生成第 1 章。
15. 查看异步任务进度。
16. 查看 Context Pack。
17. 查看 Scene Plan。
18. 查看正文。
19. 查看 Critic Report。
20. 如果 Critic Fail，查看自动重写版本。
21. 输入人工重写意见，生成新版本。
22. Accept 最终版本。
23. 查看 Summary Memory。
24. 生成第 2-5 章。
25. 查看 Qdrant 召回内容。
26. 查看 Neo4j 人物关系。
27. 查看 GraphRAG 注入上下文。
28. 查看 Token ROI。
29. 导出 Markdown 合集。
```

---

## 14. 最重要提醒

v2 的目标不是炫技，而是做一个真正有用的小说创作系统。

判断系统成功的标准不是“接了多少技术”，而是：

```text
1. 作者能控制。
2. 系统记得住。
3. 章节接得上。
4. 设定不崩坏。
5. 审稿能打回。
6. 重写能变好。
7. 失败不污染记忆。
8. 成功能沉淀记忆。
9. 成本能看见。
10. 最后能导出。
```

如果时间不够，优先保证：

```text
LangGraph 最小闭环
+ Critic
+ Human Rewrite
+ Summary Memory
+ Markdown Export
+ Token Usage
```

只要这条链路稳定，MythosForge v2 就已经具备真正落地的价值。
