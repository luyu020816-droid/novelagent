# MythosForge 15 天 MVP 执行计划（Cursor 每日执行版）

> 文件用途：每天开始开发前，把本文件交给 Cursor 阅读，然后告诉它“今天是 Day X，请只执行 Day X 的任务，并遵守全局规则”。  
> 项目目标：在 15 天内完成一个本地优先的长篇小说多 Agent 创作引擎 MVP。  
> 核心要求：不是追求商业级完整系统，而是必须跑通“能创建小说、初始化设定、逐章生成、审稿、提交、记忆、召回、导出”的闭环。

---

## 0. 使用方式

### 0.1 每天给 Cursor 的固定开场

每天开始时，直接复制下面这段给 Cursor：

```text
请先完整阅读 docs/02_mythosforge_15day_mvp_execution.md。

今天是 Day {X}。

请你只执行 Day {X} 的任务，不要提前实现后面天数的功能。
如果发现前面 Day 的任务缺失，可以先补齐最小必要部分，但不要扩展范围。
请遵守本计划中的全局架构、目录结构、命名规范、MVP 优先级和砍功能规则。

今天结束时必须给我：
1. 已完成的文件列表
2. 新增/修改的接口
3. 新增/修改的数据表
4. 如何本地启动
5. 如何验收 Day {X}
6. 还没完成的风险点
```

### 0.2 Cursor 必须遵守的开发原则

```text
1. 不要追求一次性完美。
2. 每天只做当天范围。
3. 能跑通闭环优先，漂亮架构其次。
4. 所有 LLM 调用必须经过 writer-python 的 LLM Gateway。
5. PostgreSQL 是事实源。
6. Story Contract 是写作事实源。
7. Chapter Contract 是章节目标事实源。
8. Accepted Chapter Commit 才能更新正式记忆。
9. Rejected Chapter 只能记录失败，不能污染 summary / Qdrant / Neo4j。
10. 每个 Agent 输出都必须有 Pydantic Schema。
11. 所有 Prompt 必须版本化。
12. 先同步调用，后异步任务。
13. Day 14 之前不强求 Redis 异步任务。
14. Day 12 之前不强求 Neo4j。
15. Day 10 之前不强求 Qdrant。
16. 任何复杂功能都允许先 mock，但接口和数据结构要稳定。
```

---

## 1. 项目总目标

MythosForge 是一个本地优先的长篇小说多 Agent 创作引擎。

它不是简单地让 AI 写一章，而是让系统持续管理整本小说：

```text
创建项目
  ↓
题材推荐
  ↓
Novel Seed
  ↓
Story Contract
  ↓
Chapter Contract
  ↓
Context Pack
  ↓
Chapter Planner
  ↓
Ghostwriter
  ↓
Critic
  ↓
Accepted / Rejected Commit
  ↓
Summary / Memory / Markdown / Token
  ↓
下一章继续生成
```

15 天结束时，系统至少应该能：

```text
1. 创建小说项目。
2. 输入题材偏好，生成 3 个候选题材。
3. 选择题材，生成 Novel Seed Contract。
4. 生成 Story Contract。
5. 生成前 20 章 Chapter Contract。
6. 点击生成第 N 章。
7. 自动组装 Context Pack。
8. 完成 Chapter Planner → Ghostwriter → Critic。
9. Critic 有 blocking issue 时自动重写。
10. Accepted 后保存章节正文 Markdown。
11. Accepted 后生成章节摘要。
12. 下一章召回前文摘要和人物状态。
13. Qdrant 能存储并召回章节摘要 / 人物档案 / 世界规则。
14. Neo4j 能记录人物、事件、伏笔、关系。
15. Redis 或任务状态机制能展示生成进度。
16. 记录每次 LLM 调用的 token、模型、耗时、状态。
17. 前端能展示项目、设定、大纲、章节、审稿、token。
18. 连续生成至少 5 章。
19. 导出小说 Markdown 合集。
```

---

## 2. 推荐目录结构

Cursor 创建项目时，优先按以下结构生成。

```text
mythosforge/
  README.md
  docker-compose.yml
  .env.example

  docs/
    01_mythosforge_product_architecture.md
    02_mythosforge_15day_mvp_execution.md

  backend-java/
    pom.xml
    src/main/java/com/mythosforge/
      MythosForgeApplication.java

      project/
        ProjectController.java
        ProjectService.java
        Project.java
        ProjectRepository.java
        dto/

      genre/
        GenreController.java
        GenreService.java
        dto/

      story/
        StoryController.java
        StoryService.java
        dto/

      chapter/
        ChapterController.java
        ChapterService.java
        ChapterVersion.java
        ChapterVersionRepository.java
        dto/

      job/
        GenerationJobController.java
        GenerationJobService.java
        GenerationJob.java
        GenerationJobRepository.java
        dto/

      commit/
        ChapterCommit.java
        ChapterCommitRepository.java
        ChapterCommitService.java

      token/
        TokenUsage.java
        TokenUsageRepository.java
        TokenUsageService.java

      writer/
        WriterEngineClient.java
        dto/

      common/
        ApiResponse.java
        ErrorResponse.java
        GlobalExceptionHandler.java

    src/main/resources/
      application.yml
      db/migration/
        V1__init_core_tables.sql

  writer-python/
    pyproject.toml
    requirements.txt
    app/
      main.py

      api/
        health.py
        genre.py
        init_novel.py
        chapter.py
        test_agent.py

      agents/
        genre_scout.py
        trope_strategist.py
        market_fit_scorer.py
        showrunner.py
        character_designer.py
        worldbuilder.py
        outline_architect.py
        initial_critic.py
        context_curator.py
        chapter_planner.py
        ghostwriter.py
        critic.py
        summary_writer.py
        lore_keeper.py

      schemas/
        common.py
        genre.py
        story.py
        chapter.py
        context_pack.py
        review.py
        commit.py
        memory.py
        token.py

      services/
        llm_gateway.py
        prompt_registry.py
        token_estimator.py
        json_repair.py
        postgres_store.py
        qdrant_store.py
        neo4j_store.py
        markdown_exporter.py
        context_pack_service.py
        chapter_generation_service.py
        summary_service.py

      prompts/
        genre_scout_v1.md
        trope_strategist_v1.md
        showrunner_v1.md
        character_designer_v1.md
        worldbuilder_v1.md
        outline_architect_v1.md
        chapter_planner_v1.md
        ghostwriter_v1.md
        critic_v1.md
        summary_writer_v1.md
        lore_keeper_v1.md

  frontend/
    package.json
    src/
      main.tsx
      App.tsx
      api/
        client.ts
        projects.ts
        genre.ts
        story.ts
        chapters.ts
      pages/
        ProjectListPage.tsx
        ProjectCreatePage.tsx
        ProjectDetailPage.tsx
        InitNovelPage.tsx
        StoryContractPage.tsx
        ChapterWorkspacePage.tsx
        GraphPage.tsx
        TokenPage.tsx
      components/
        Layout.tsx
        ProjectForm.tsx
        GenreCandidates.tsx
        StoryContractView.tsx
        ChapterContractList.tsx
        ChapterEditor.tsx
        CriticReportPanel.tsx
        TokenUsagePanel.tsx

  data/
    trope_cards/
      urban_tech_system.json
      fantasy_leveling.json
      romance_rebirth.json
    platform_profiles/
      fanqie.yaml
      qidian.yaml
    genre_rules/
      default.yaml
    manual_trend_notes/

  exports/
    projects/

  scripts/
    run_local.sh
    reset_local.sh
    seed_data.py
```

---

## 3. 服务职责边界

### 3.1 backend-java

负责：

```text
1. 用户入口 API。
2. 项目管理。
3. 数据库存储事实源。
4. 调用 writer-python。
5. 保存合同、章节、提交、token。
6. 未来负责任务调度、Redis、SSE、Token 看板。
```

MVP 中 Java 不负责复杂 Agent 逻辑。

### 3.2 writer-python

负责：

```text
1. Agent 编排。
2. Prompt 管理。
3. LLM Gateway。
4. JSON Schema 校验。
5. 题材推荐。
6. 小说初始化。
7. 章节生成。
8. 审稿。
9. 摘要。
10. Qdrant / Neo4j 写入和召回。
```

### 3.3 frontend

负责：

```text
1. 创建项目。
2. 输入题材偏好。
3. 展示题材推荐。
4. 展示 Story Contract。
5. 展示前 20 章 Chapter Contract。
6. 章节生成工作台。
7. 展示 Critic Report。
8. 展示 Token Usage。
9. 展示图谱简表。
```

---

## 4. 数据库核心表

Day 2 先建基础表。后续每天可以增量 migration。

### 4.1 projects

```sql
create table projects (
    id varchar(64) primary key,
    name varchar(255) not null,
    language varchar(32) default 'zh-CN',
    target_chapters int default 100,
    current_chapter int default 0,
    status varchar(32) default 'created',
    created_at timestamp default now(),
    updated_at timestamp default now()
);
```

### 4.2 genre_decision_contracts

```sql
create table genre_decision_contracts (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    selected_direction jsonb,
    candidate_rankings jsonb,
    risk_notes jsonb,
    raw_json jsonb not null,
    created_at timestamp default now()
);
```

### 4.3 novel_seed_contracts

```sql
create table novel_seed_contracts (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    raw_json jsonb not null,
    created_at timestamp default now()
);
```

### 4.4 story_contracts

```sql
create table story_contracts (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    version int not null default 1,
    raw_json jsonb not null,
    created_at timestamp default now()
);
```

### 4.5 chapter_contracts

```sql
create table chapter_contracts (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    chapter_no int not null,
    title_hint varchar(255),
    raw_json jsonb not null,
    created_at timestamp default now(),
    unique(project_id, chapter_no)
);
```

### 4.6 generation_jobs

```sql
create table generation_jobs (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    chapter_no int,
    job_type varchar(32) not null,
    status varchar(32) not null,
    current_stage varchar(64),
    progress int default 0,
    error_message text,
    created_at timestamp default now(),
    updated_at timestamp default now()
);
```

### 4.7 chapter_versions

```sql
create table chapter_versions (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    chapter_no int not null,
    version int not null,
    status varchar(32) not null,
    scene_plan_json jsonb,
    chapter_text text,
    critic_report_json jsonb,
    rewrite_instruction_json jsonb,
    created_at timestamp default now(),
    unique(project_id, chapter_no, version)
);
```

### 4.8 chapter_commits

```sql
create table chapter_commits (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    chapter_no int not null,
    version int not null,
    status varchar(32) not null,
    chapter_contract_id varchar(64),
    context_pack_hash varchar(128),
    final_text_path text,
    review_report_id varchar(64),
    token_usage_summary jsonb,
    rejection_reason text,
    created_at timestamp default now()
);
```

### 4.9 memory_summaries

```sql
create table memory_summaries (
    id varchar(64) primary key,
    project_id varchar(64) not null,
    chapter_no int not null,
    commit_id varchar(64) not null,
    title varchar(255),
    summary_text text not null,
    key_events jsonb,
    character_state_changes jsonb,
    new_foreshadowing jsonb,
    cliffhanger text,
    created_at timestamp default now(),
    unique(project_id, chapter_no)
);
```

### 4.10 llm_usage_log

```sql
create table llm_usage_log (
    id bigserial primary key,
    job_id varchar(64),
    project_id varchar(64),
    chapter_no int,
    agent_name varchar(64),
    node_name varchar(64),
    provider varchar(32),
    model varchar(64),

    estimated_input_tokens int,
    estimated_output_tokens int,
    estimated_total_tokens int,

    actual_input_tokens int,
    actual_output_tokens int,
    actual_total_tokens int,

    latency_ms int,
    status varchar(32),
    error_message text,
    created_at timestamp default now()
);
```

---

## 5. 核心 API 草案

### 5.1 Java API

```http
GET /api/health
POST /api/projects
GET /api/projects
GET /api/projects/{projectId}
POST /api/projects/{projectId}/genre/recommend
POST /api/projects/{projectId}/init
GET /api/projects/{projectId}/story-contract
GET /api/projects/{projectId}/chapter-contracts
POST /api/projects/{projectId}/chapters/{chapterNo}/generate
GET /api/projects/{projectId}/chapters/{chapterNo}
POST /api/projects/{projectId}/chapters/{chapterNo}/rewrite
POST /api/projects/{projectId}/chapters/{chapterNo}/accept
GET /api/projects/{projectId}/token-usage
GET /api/projects/{projectId}/export/markdown
```

### 5.2 Python API

```http
GET /health
GET /api/writer/health
POST /api/writer/test-agent
POST /api/writer/genre/recommend
POST /api/writer/init-novel
POST /api/writer/chapters/generate
POST /api/writer/chapters/rewrite
POST /api/writer/summary/write
```

---

## 6. 核心对象 JSON Schema 方向

### 6.1 Genre Decision Contract

```json
{
  "selected_direction": {
    "channel": "男频",
    "genre": "都市科技爽文",
    "sub_tags": ["系统", "创业", "技术逆袭", "打脸"],
    "reason": "爽点密度高，适合中长篇连载"
  },
  "candidate_rankings": [
    {
      "genre": "都市科技爽文",
      "heat_score": 8.2,
      "competition_score": 6.5,
      "payoff_density": 8.8,
      "serialization_score": 8.0,
      "originality_space": 7.6,
      "token_cost_level": "medium",
      "final_score": 8.0
    }
  ],
  "recommended_core_hook": "底层维修工获得失落文明工程协议，用技术反转资源垄断。",
  "risk_notes": [
    "避免传统神豪无脑撒钱",
    "系统能力需要明确边界"
  ]
}
```

### 6.2 Story Contract

```json
{
  "positioning": {
    "title_candidates": [],
    "genre": "",
    "target_reader": "",
    "core_hook": "",
    "tone": ""
  },
  "protagonist": {
    "name": "",
    "desire": "",
    "weakness": "",
    "secret": "",
    "growth_arc": ""
  },
  "characters": [],
  "world_rules": [],
  "ability_rules": [],
  "forbidden_moves": [],
  "style_guide": {},
  "volume_outline": []
}
```

### 6.3 Chapter Contract

```json
{
  "chapter_no": 1,
  "title_hint": "",
  "chapter_goal": "",
  "must_cover": [],
  "forbidden_moves": [],
  "payoff": "",
  "cliffhanger": ""
}
```

### 6.4 Context Pack

```json
{
  "project_id": "",
  "chapter_no": 1,
  "chapter_contract": {},
  "must_follow_facts": [],
  "forbidden_moves": [],
  "recent_summaries": [],
  "character_states": [],
  "open_foreshadowing": [],
  "relationship_graph": [],
  "world_rules": [],
  "trope_constraints": [],
  "token_budget": {
    "max_context_tokens": 5000,
    "max_output_tokens": 3500
  }
}
```

### 6.5 Critic Report

```json
{
  "pass": false,
  "score": 76,
  "blocking": true,
  "issues": [
    {
      "type": "character_consistency",
      "severity": "high",
      "blocking": true,
      "evidence": "主角主动公开系统能力",
      "problem": "违反低调设定",
      "fix": "改为暗中救场，其他人只看到结果"
    }
  ],
  "rewrite_instruction": {
    "rollback_to": "ghostwriter",
    "must_fix": [
      "主角不能公开系统",
      "保留身份危机"
    ]
  }
}
```

---

## 7. Prompt 版本规则

所有 Prompt 放在：

```text
writer-python/app/prompts/
```

命名必须类似：

```text
genre_scout_v1.md
showrunner_v1.md
chapter_planner_v1.md
ghostwriter_v1.md
critic_v1.md
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

Ghostwriter 必须强调：

```text
你只能根据 Context Pack 和 Scene Plan 写正文。
不得新增重大设定。
不得改变人物底层性格。
不得提前回收伏笔。
不得公开 forbidden_moves 中禁止公开的信息。
正文必须符合 style_guide。
```

Critic 必须强调：

```text
你不是普通点评者，而是章节验收器。
你必须检查：
- 是否违反 Story Contract
- 是否覆盖 Chapter Contract
- 是否违反 Forbidden Moves
- 是否存在人设冲突
- 是否存在世界观冲突
- 是否存在提前揭秘
- 是否缺少爽点
- 是否不能承接下一章
```

---

## 8. 15 天每日计划

---

# Day 1：项目骨架与本地基础设施

## 目标

把项目跑起来，不写复杂业务逻辑。

## 今日必须完成

```text
1. 创建 monorepo：
   - backend-java
   - writer-python
   - frontend
   - data
   - exports
   - docs
   - scripts

2. 写 docker-compose：
   - PostgreSQL
   - Redis
   - Qdrant
   - Neo4j

3. Spring Boot 初始化：
   - /api/health
   - /api/projects
   - Project Entity
   - Project Repository
   - Flyway migration

4. FastAPI 初始化：
   - /health
   - /api/writer/health

5. 前端初始化：
   - 项目列表页
   - 创建项目页

6. 写 .env.example。
```

## 建议 Cursor 执行顺序

```text
1. 先创建目录结构。
2. 生成 docker-compose.yml。
3. 生成 backend-java Spring Boot 项目。
4. 生成 writer-python FastAPI 项目。
5. 生成 frontend Vite React 项目。
6. 配置 Java 连接 PostgreSQL。
7. 创建 projects 表 migration。
8. 实现 Project CRUD 最小版。
9. 前端调用 Java API 创建项目。
10. 最后统一写 README 的 Day 1 启动说明。
```

## Day 1 验收

```text
docker compose up 能启动基础服务。
localhost:8080/api/health 返回 ok。
localhost:8000/health 返回 ok。
前端能打开项目列表页。
前端能创建 project。
project 能写入 PostgreSQL。
```

## Day 1 不要做

```text
不要接入 LLM。
不要实现 Agent。
不要写 Qdrant 逻辑。
不要写 Neo4j 逻辑。
不要写 Redis Stream 逻辑。
不要写复杂前端 UI。
```

---

# Day 2：数据库基础表与 Java / Python 通信

## 目标

Java 能调用 Python，核心数据表准备好。

## 今日必须完成

```text
1. PostgreSQL 建基础表：
   - projects
   - genre_decision_contracts
   - novel_seed_contracts
   - story_contracts
   - chapter_contracts
   - generation_jobs
   - chapter_versions
   - chapter_commits
   - memory_summaries
   - llm_usage_log

2. Spring Boot 实现：
   - WriterEngineClient
   - ProjectController 完善
   - ProjectDetail API

3. Java 调 FastAPI：
   - GET /api/writer/health
   - POST /api/writer/test

4. 前端能：
   - 创建项目
   - 查看项目列表
   - 进入项目详情页
```

## 建议 Cursor 执行顺序

```text
1. 新增 Flyway migration V2__init_generation_tables.sql。
2. 生成 Java entity / repository。
3. 实现 WriterEngineClient。
4. Python 增加 /api/writer/test。
5. Java 增加 /api/projects/{id}/writer-health 测试调用。
6. 前端项目详情页展示 project 和 writer health。
```

## Day 2 验收

```text
前端创建项目成功。
Java 写入 PostgreSQL 成功。
Java 能成功调用 Python health / test。
数据库中能看到核心表。
```

## Day 2 不要做

```text
不要实现真正 LLM。
不要写复杂 Agent。
不要实现章节生成。
```

---

# Day 3：LLM Gateway、Prompt Registry、Schema 校验

## 目标

后续所有 Agent 都通过统一模型入口调用 LLM。

## 今日必须完成

```text
1. writer-python 实现 LLM Gateway。
2. 支持 Ollama Provider。
3. 可选支持 OpenAI Provider。
4. 实现 Token Estimator。
5. 实现 llm_usage_log 写入。
6. 实现 Prompt Registry。
7. 引入 Pydantic Schema 校验。
8. 实现 JSON repair。
9. 做 test_agent 调用模型，输出结构化 JSON。
```

## 必做接口

```http
POST /api/writer/test-agent
```

## 建议 Cursor 执行顺序

```text
1. 建 schemas/common.py。
2. 建 services/prompt_registry.py。
3. 建 services/token_estimator.py。
4. 建 services/json_repair.py。
5. 建 services/llm_gateway.py。
6. 写 prompts/test_agent_v1.md。
7. 写 api/test_agent.py。
8. test-agent 输出一个固定 schema，例如：
   {
     "ok": true,
     "message": "...",
     "items": []
   }
9. 将 LLM 调用记录写入 llm_usage_log。
```

## Day 3 验收

```text
调用 /api/writer/test-agent 能返回合法 JSON。
llm_usage_log 能记录 provider、model、estimated tokens、actual tokens、latency。
模型输出 JSON 不合法时，能触发一次 repair。
```

## Day 3 不要做

```text
不要实现题材推荐完整链路。
不要实现章节生成。
不要写复杂 token 计费。
```

---

# Day 4：题材推荐 Genre Decision 第一版

## 目标

能根据用户偏好推荐题材。

## 今日必须完成

```text
1. 创建 trope_cards JSON。
2. 创建 platform_profiles YAML。
3. 创建 genre_rules YAML。
4. 实现 Genre Scout。
5. 实现 Trope Strategist。
6. 实现 Market Fit Scorer。
7. 生成 Genre Decision Contract。
8. Java 侧保存 Genre Decision Contract。
9. 前端展示 3 个候选题材。
```

## 输入示例

```json
{
  "target_platform": "番茄",
  "gender_channel": "男频",
  "preferred_genres": [],
  "avoid": ["强虐", "纯后宫"],
  "writing_strength": ["爽点", "反转"],
  "risk_preference": "medium"
}
```

## 输出必须包含

```text
1. 3 个候选题材。
2. 每个题材的推荐理由。
3. 每个题材的风险。
4. selected_direction。
5. recommended_core_hook。
6. candidate_rankings。
```

## 建议 Cursor 执行顺序

```text
1. 写 data/trope_cards/*.json。
2. 写 data/platform_profiles/*.yaml。
3. 写 schemas/genre.py。
4. 写 agents/genre_scout.py。
5. 写 agents/trope_strategist.py。
6. 写 agents/market_fit_scorer.py。
7. 写 api/genre.py。
8. Java GenreController 调 Python。
9. Java 保存 raw_json 到 genre_decision_contracts。
10. 前端展示候选题材列表。
```

## Day 4 验收

```text
用户输入题材偏好后：
- 能得到 3 个候选题材
- 能看到推荐理由
- 能看到风险说明
- 能保存 selected_direction
```

## Day 4 不要做

```text
不要做真实网络热度抓取。
不要做商业收益预测。
不要做复杂评分算法。
```

---

# Day 5：Novel Seed 与 Story Contract 第一版

## 目标

从题材变成小说核心设定。

## 今日必须完成

```text
1. 实现 Showrunner。
2. 实现 Character Designer 简化版。
3. 实现 Worldbuilder 简化版。
4. 生成 Novel Seed Contract。
5. 生成 Story Contract 第一版。
6. 保存 Novel Seed Contract。
7. 保存 Story Contract。
8. 前端展示：
   - 核心卖点
   - 主角设定
   - 金手指
   - 世界规则
   - 能力边界
   - 禁区设定
```

## Novel Seed 必须包含

```text
1. 标题候选。
2. 目标读者。
3. 核心卖点。
4. 主角原型。
5. 金手指。
6. 商业爽点。
7. 开篇冲突。
```

## Story Contract 必须包含

```text
1. positioning。
2. protagonist。
3. characters。
4. world_rules。
5. ability_rules。
6. forbidden_moves。
7. style_guide。
8. first_volume_direction。
```

## 建议 Cursor 执行顺序

```text
1. 写 schemas/story.py。
2. 写 showrunner_v1.md。
3. 写 character_designer_v1.md。
4. 写 worldbuilder_v1.md。
5. 写 agents/showrunner.py。
6. 写 agents/character_designer.py。
7. 写 agents/worldbuilder.py。
8. 写 api/init_novel.py。
9. Java StoryController 调 Python。
10. Java 保存 novel_seed_contracts 和 story_contracts。
11. 前端 StoryContractView 展示结果。
```

## Day 5 验收

```text
选择一个题材后，系统能初始化小说。
能看到：
- 主角
- 核心配角
- 世界规则
- 能力规则
- Forbidden Moves
- Style Guide
```

## Day 5 不要做

```text
不要生成 20 章章纲。
不要生成正文。
不要写 Qdrant / Neo4j。
```

---

# Day 6：前 20 章 Chapter Contract

## 目标

初始化章节大纲。

## 今日必须完成

```text
1. 实现 Outline Architect。
2. 实现 Initial Critic 简化版。
3. 生成第一卷大纲。
4. 生成前 20 章 Chapter Contract。
5. Chapter Contract 落库。
6. 前端展示前 20 章章纲。
7. 用户可以查看每章：
   - chapter_goal
   - must_cover
   - forbidden_moves
   - payoff
   - cliffhanger
```

## Chapter Contract 必须包含

```text
1. chapter_no
2. title_hint
3. chapter_goal
4. must_cover
5. forbidden_moves
6. payoff
7. cliffhanger
```

## 建议 Cursor 执行顺序

```text
1. 写 schemas/chapter.py 中 ChapterContract。
2. 写 prompts/outline_architect_v1.md。
3. 写 prompts/initial_critic_v1.md。
4. 写 agents/outline_architect.py。
5. 写 agents/initial_critic.py。
6. 扩展 /api/writer/init-novel，让它返回 chapter_contracts。
7. Java 保存 chapter_contracts。
8. 前端 ChapterContractList 展示 20 章。
```

## Day 6 验收

```text
创建项目后，能完整初始化：
- Story Contract
- 第一卷大纲
- 前 20 章 Chapter Contract

用户可以在前端看到并保存这些内容。
```

## Day 6 不要做

```text
不要生成正文。
不要做复杂大纲编辑器。
不要做拖拽。
```

---

# Day 7：章节生成最小闭环

## 目标

真正生成第 1 章。

## 今日必须完成

```text
1. 实现 Context Curator 简化版：
   - Story Contract
   - 当前 Chapter Contract
   - Forbidden Moves
   - Character State
   - 最近摘要，第一章为空

2. 实现 Chapter Planner。
3. 实现 Ghostwriter。
4. 实现 Critic。
5. 实现 Decision Gate。
6. 实现 Chapter Version 保存。
7. 实现 Markdown 导出单章。
8. 实现 Accepted / Rejected Commit。
```

## 章节生成流程

```text
POST /api/projects/{projectId}/chapters/{chapterNo}/generate
  ↓
Java 加载 project / story_contract / chapter_contract / recent_summaries
  ↓
Java 调 Python /api/writer/chapters/generate
  ↓
Python 组装 Context Pack
  ↓
Chapter Planner 生成 Scene Plan
  ↓
Ghostwriter 生成正文
  ↓
Critic 审稿
  ↓
Decision Gate 判断 accepted / rejected
  ↓
Java 保存 chapter_version
  ↓
如果 accepted，保存 chapter_commit 和 markdown
```

## 建议 Cursor 执行顺序

```text
1. 写 schemas/context_pack.py。
2. 写 schemas/review.py。
3. 写 prompts/chapter_planner_v1.md。
4. 写 prompts/ghostwriter_v1.md。
5. 写 prompts/critic_v1.md。
6. 写 agents/context_curator.py。
7. 写 agents/chapter_planner.py。
8. 写 agents/ghostwriter.py。
9. 写 agents/critic.py。
10. 写 services/chapter_generation_service.py。
11. 写 services/markdown_exporter.py。
12. 写 api/chapter.py。
13. Java ChapterController 调 Python。
14. Java 保存 chapter_versions / chapter_commits。
15. 前端 ChapterWorkspacePage 增加“生成第 N 章”按钮。
```

## Day 7 验收

```text
点击“生成第 1 章”后：
- 能生成 Context Pack
- 能生成 Scene Plan
- 能生成正文
- 能生成 Critic Report
- 能判断 accepted / rejected
- accepted 后能保存 Markdown
- rejected 后不会更新摘要
```

## Day 7 不要做

```text
不要做自动重写。
不要做 Qdrant。
不要做 Neo4j。
不要做 Redis 异步任务。
```

---

# Day 8：摘要记忆与连续章节

## 目标

从能写一章变成能连续写。

## 今日必须完成

```text
1. 实现 Summary Writer。
2. Accepted 后生成章节摘要。
3. 摘要写入 PostgreSQL。
4. 摘要包含：
   - chapter_no
   - title
   - key_events
   - character_state_changes
   - new_foreshadowing
   - cliffhanger
   - summary_text

5. 下一章 Context Pack 自动召回上一章摘要。
6. 连续生成第 1-3 章。
```

## 建议 Cursor 执行顺序

```text
1. 写 schemas/memory.py。
2. 写 prompts/summary_writer_v1.md。
3. 写 agents/summary_writer.py。
4. 写 services/summary_service.py。
5. Accepted Commit 后调用 summary_writer。
6. Java 保存 memory_summaries。
7. Context Curator 读取最近 3 章 summary。
8. 前端展示最近摘要。
9. 手动测试连续生成 1-3 章。
```

## Day 8 验收

```text
系统能连续生成 3 章。
第 2 章能承接第 1 章结尾。
第 3 章能承接前两章摘要。
Accepted chapter 才会生成摘要。
Rejected chapter 不污染记忆。
```

## Day 8 不要做

```text
不要做 Qdrant。
不要做 Neo4j。
不要做复杂人物状态编辑。
```

---

# Day 9：Critic 自动重写与人工重写

## 目标

质量控制闭环成型。

## 今日必须完成

```text
1. Critic 输出结构化 blocking issue。
2. 实现 rewrite_instruction。
3. 实现 max_retries_per_node。
4. blocking = true 时自动回滚到 Ghostwriter。
5. 自动重写一次或多次。
6. 实现人工重写接口。
7. 保存每次重写版本。
8. 前端展示：
   - v1
   - v2
   - Critic Report
   - Accepted / Rejected 状态
```

## 自动重写逻辑

```text
生成 v1
  ↓
Critic 检查
  ↓
如果 pass = true：
    accepted
否则如果 blocking = true 且 retry_count < max_retries：
    根据 rewrite_instruction 重新调用 Ghostwriter
    生成 v2
    再 Critic
否则：
    rejected
```

## 建议 Cursor 执行顺序

```text
1. 扩展 CriticReport schema。
2. 扩展 chapter_generation_service.py 支持 retry loop。
3. 保存每个 version。
4. Java 增加 POST /chapters/{chapterNo}/rewrite。
5. Python 增加 /api/writer/chapters/rewrite。
6. 前端增加人工 rewrite instruction 输入框。
7. 前端展示版本列表和每版 Critic Report。
```

## Day 9 验收

```text
Critic 发现 blocking issue 时：
- 不会 accepted
- 能生成 rewrite_instruction
- 能重写
- 能保存多个 chapter version
- 最终 accepted 后才写 summary / markdown
```

## Day 9 不要做

```text
不要做复杂局部重写。
不要做多分支剧情。
不要做 Stylist。
```

---

# Day 10：Qdrant RAG 第一版

## 目标

从 PostgreSQL 摘要召回升级到向量记忆。

## 今日必须完成

```text
1. 接入 Qdrant。
2. 创建 collections：
   - chapter_summaries
   - character_profiles
   - world_rules
   - trope_cards

3. 写入初始世界规则。
4. 写入初始人物档案。
5. 写入 trope_cards。
6. Accepted 后将章节摘要写入 Qdrant。
7. Context Curator 支持 Qdrant 召回。
```

## MVP 简化规则

```text
1. embedding 可以先用本地简单 embedding provider。
2. 如果没有 embedding 模型，可以先用 Qdrant + mock vector 或 hash vector 跑通接口。
3. 第一版重点是写入和召回链路，不追求召回质量完美。
4. Qdrant 只作为辅助记忆，PostgreSQL 仍是事实源。
```

## 建议 Cursor 执行顺序

```text
1. 写 services/qdrant_store.py。
2. 启动时创建 collection。
3. 写入 trope_cards。
4. Story Contract accepted 后写入 character_profiles / world_rules。
5. Chapter accepted 后写入 chapter_summaries。
6. Context Curator 增加 qdrant_recall。
7. Context Pack 中展示 recalled_memories。
8. 前端展示 Context Pack 中的 Qdrant 召回内容。
```

## Day 10 验收

```text
生成第 4-5 章时：
- Context Pack 中能看到 Qdrant 召回内容
- 人物档案能被召回
- 过去章节摘要能被召回
- 世界规则能被召回
```

## Day 10 不要做

```text
不要做复杂向量 reranker。
不要做 hybrid search。
不要做跨项目检索。
```

---

# Day 11：Context Pack 裁剪与简单 Rerank

## 目标

解决上下文越来越长的问题。

## 今日必须完成

```text
1. 实现 Context Pack 优先级排序：
   1. 当前 Chapter Contract
   2. Forbidden Moves
   3. Story Contract 核心规则
   4. Character Current State
   5. Open Foreshadowing
   6. 上一章摘要
   7. 最近 3 章摘要
   8. Qdrant 相似摘要
   9. Trope Card
   10. 更远历史摘要

2. 实现 token budget 裁剪。
3. 实现 max_context_tokens。
4. 低优先级内容自动丢弃。
5. 保证 Forbidden Moves 永远不被裁掉。
6. Context Pack 保存入库或至少保存到 chapter_version。
```

## 建议 Cursor 执行顺序

```text
1. 给 ContextPackItem 增加 priority 字段。
2. 给 ContextPackItem 增加 estimated_tokens 字段。
3. 写 token_budget_trim 函数。
4. 写 rerank_context_items 函数。
5. 修改 context_curator 使用统一排序和裁剪。
6. 写测试：构造超长 Context Pack，验证低优先级被裁。
7. 前端显示最终 Context Pack 和被裁剪项目数量。
```

## Day 11 验收

```text
当召回内容过多时：
- Context Pack 不会无限变长
- 关键设定不会被裁掉
- 最近摘要优先保留
- Forbidden Moves 必须保留
```

## Day 11 不要做

```text
不要做复杂机器学习 reranker。
不要做高级 BM25。
不要做图算法。
```

---

# Day 12：Neo4j 图谱与 Lore Keeper 第一版

## 目标

让人物、事件、伏笔结构化。

## 今日必须完成

```text
1. 接入 Neo4j。
2. 初始化 Knowledge Graph Seed。
3. 实现 Lore Keeper。
4. 从 accepted chapter 中抽取：
   - Character
   - Event
   - Location
   - Ability
   - Foreshadowing
   - Relationship Update

5. 生成 KG Update Proposal。
6. Accepted Commit 后写入 Neo4j。
7. 每条边带：
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

## 建议 Cursor 执行顺序

```text
1. 写 services/neo4j_store.py。
2. 写 schemas/memory.py 中 KGUpdateProposal。
3. 写 prompts/lore_keeper_v1.md。
4. 写 agents/lore_keeper.py。
5. Accepted Commit 后调用 lore_keeper。
6. lore_keeper 输出 proposal。
7. graph_writer 将 proposal 写入 Neo4j。
8. Neo4j 节点和边必须带 project_id。
```

## Day 12 验收

```text
生成第 5 章后：
- Neo4j 中能看到主角、配角、事件、伏笔
- 人物之间有 TRUSTS / SUSPECTS / ENEMY_OF 等关系
- 能看到某个伏笔在哪章出现
```

## Day 12 不要做

```text
不要做复杂图算法。
不要做漂亮图谱可视化。
不要让 rejected chapter 写入 Neo4j。
```

---

# Day 13：Neo4j 图谱召回与简单图谱页

## 目标

图谱不只是展示，而是参与下一章生成。

## 今日必须完成

```text
1. Context Curator 从 Neo4j 读取：
   - 当前人物关系
   - open foreshadowing
   - ability usage
   - recent events

2. 图谱召回结果加入 Context Pack。
3. 前端做简单图谱查看页。
4. 不追求复杂可视化，先用列表 / 表格：
   - 人物
   - 关系
   - 事件
   - 伏笔
   - 出现章节
```

## 建议 Cursor 执行顺序

```text
1. neo4j_store 增加 query_relationships。
2. neo4j_store 增加 query_open_foreshadowing。
3. neo4j_store 增加 query_recent_events。
4. context_curator 增加 graph_context。
5. Java 增加 GraphController。
6. 前端 GraphPage 用表格展示。
7. ChapterWorkspacePage 展示本章使用的 graph_context。
```

## Day 13 验收

```text
下一章 Context Pack 能读取图谱关系。
前端能看到：
- Character
- Event
- Foreshadowing
- Relationship
```

## Day 13 不要做

```text
不要做 D3 复杂图谱。
不要做图算法推荐剧情。
不要做跨项目图谱。
```

---

# Day 14：Redis 异步任务、进度推送、Token 看板

## 目标

从同步调用变成工程化生成流程。

## 今日必须完成

```text
1. Spring Boot 创建 generation_job。
2. Redis Streams 发送 job.created。
3. Python Worker 消费任务。
4. Python 写 job.progress。
5. Spring Boot 查询 job 状态。
6. 前端展示当前阶段：
   - context_curator
   - chapter_planner
   - ghostwriter
   - critic
   - summary_writer
   - lore_keeper
   - projection_writers

7. 完善 Token Budget Service。
8. 前端展示：
   - 本章 token
   - 各 Agent token
   - retry waste
   - budget status
```

## 可接受简化

```text
1. 如果 SSE / WebSocket 来不及，前端用轮询。
2. 如果 Redis Streams 来不及，先用 generation_jobs 表轮询。
3. 如果 Token Dashboard 来不及，先展示 llm_usage_log 聚合。
```

## 建议 Cursor 执行顺序

```text
1. Java GenerationJobService 创建 job。
2. Java 发 Redis Stream job.created。
3. Python worker 消费 generation_jobs。
4. Python 每个阶段更新 job.progress。
5. Java 查询 job 状态。
6. 前端轮询 /api/jobs/{jobId}。
7. TokenUsageService 聚合 llm_usage_log。
8. 前端 TokenUsagePanel 展示每个 agent token。
```

## Day 14 验收

```text
点击生成章节后，前端能看到任务状态。
刷新页面后还能看到任务状态。
Token 看板能显示本章各 Agent 用量。
超预算时能显示 warning。
```

## Day 14 不要做

```text
不要做复杂任务取消，除非有时间。
不要做复杂队列重试策略。
不要做商业级 dashboard。
```

---

# Day 15：完整压测、打磨、交付

## 目标

让系统真正能演示，而不是只有接口。

## 今日必须完成

```text
1. 创建一部测试小说。
2. 初始化完整 Story Contract。
3. 连续生成 5-10 章。
4. 检查每章：
   - 是否承接上一章
   - 是否违反主角设定
   - 是否违反能力规则
   - 是否提前回收伏笔
   - 是否有明显重复桥段
   - 是否产生合理 cliffhanger

5. 修复最高频失败点。
6. 固化 prompt。
7. 写 README。
8. 写一键启动脚本。
9. 写演示脚本。
10. 导出 Markdown 合集。
```

## 最终验收清单

```text
1. 创建项目。
2. 推荐题材。
3. 初始化小说。
4. 查看 Story Contract。
5. 查看前 20 章大纲。
6. 点击生成章节。
7. 看到 Agent 进度。
8. 看到审稿报告。
9. 看到 Accepted Commit。
10. 看到 Markdown 正文。
11. 看到 Qdrant 召回摘要。
12. 看到 Neo4j 人物关系。
13. 看到 Token 成本。
14. 连续生成至少 5 章。
15. 导出小说 Markdown。
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

## Day 15 不要做

```text
不要临时加大功能。
不要重构大架构。
不要追求 UI 漂亮。
只修阻塞演示的问题。
```

---

## 9. 15 天优先级

### 9.1 第一优先级：不能砍

```text
Story Contract
Chapter Contract
Context Pack
Chapter Planner
Ghostwriter
Critic
Accepted / Rejected Commit
Summary Memory
Markdown Export
Token Usage Log
```

### 9.2 第二优先级：尽量做

```text
Qdrant RAG
Context Pack token 裁剪
Critic 自动重写
人工重写
Chapter Version
```

### 9.3 第三优先级：15 天内能做就做

```text
Neo4j
Lore Keeper
图谱召回
简单图谱页
Redis Streams
任务进度
Token 看板
```

### 9.4 第四优先级：15 天后再做

```text
Stylist
Originality Guard 完整版
Branch Generator
复杂图谱可视化
复杂 Dashboard
多模型自动路由
OpenTelemetry / LangSmith
EPUB 导出
封面 prompt
人物卡片编辑器
大纲拖拽调整
```

---

## 10. 中途时间不够时的砍功能顺序

```text
1. 砍 Stylist。
2. 砍 Branch Generator。
3. 砍复杂 Originality Guard。
4. 砍复杂图谱前端。
5. 砍 SSE / WebSocket，改成轮询。
6. 砍任务取消。
7. 砍复杂 Token Dashboard，只保留 usage log。
8. Neo4j 只写入，不做复杂图算法。
9. Qdrant 只做 chapter_summaries / character_profiles。
10. 不砍 Story Contract。
11. 不砍 Chapter Contract。
12. 不砍 Critic。
13. 不砍 Commit。
14. 不砍 Summary Memory。
15. 不砍 Markdown Export。
```

---

## 11. 每天结束时 Cursor 必须输出的报告格式

每天结束时，让 Cursor 按这个格式回复：

```markdown
# Day X 完成报告

## 1. 今日完成
- ...

## 2. 新增文件
- ...

## 3. 修改文件
- ...

## 4. 新增接口
- ...

## 5. 新增数据表 / 字段
- ...

## 6. 本地启动方式
```bash
...
```

## 7. Day X 验收步骤
1. ...
2. ...
3. ...

## 8. 未完成 / 风险
- ...

## 9. 明天建议
- ...
```

---

## 12. 每天开始时 Cursor 必须先检查

```text
1. 当前目录结构是否符合计划。
2. docker-compose 是否能启动。
3. backend-java 是否能编译。
4. writer-python 是否能启动。
5. frontend 是否能启动。
6. 数据库 migration 是否成功。
7. 前一天验收项是否真的能跑。
```

如果前一天有阻塞，Cursor 应该先修最小阻塞，再进入当天任务。

---

## 13. 最终演示脚本

Day 15 要能按以下顺序演示：

```text
1. 启动 docker compose。
2. 启动 backend-java。
3. 启动 writer-python。
4. 启动 frontend。
5. 打开前端。
6. 创建项目《旧城工程师》。
7. 输入题材偏好：
   - 平台：番茄
   - 男频
   - 爽点
   - 反转
   - 避免强虐、后宫

8. 系统输出 3 个题材候选。
9. 选择都市科技爽文。
10. 初始化小说。
11. 查看 Story Contract。
12. 查看前 20 章 Chapter Contract。
13. 生成第 1 章。
14. 查看 Context Pack。
15. 查看 Scene Plan。
16. 查看正文。
17. 查看 Critic Report。
18. 查看 Accepted Commit。
19. 查看 Markdown 文件。
20. 连续生成第 2-5 章。
21. 查看 summary memory。
22. 查看 Qdrant 召回内容。
23. 查看 Neo4j 人物关系。
24. 查看 Token Usage。
25. 导出 Markdown 合集。
```

---

## 14. 最重要提醒

15 天内不要把目标变成“做完所有东西”。

真正的胜利标准是：

```text
系统能连续生成 5 章，并且：
- 主角人设没有明显崩坏
- 核心能力规则没有被随意改写
- 上一章 cliffhanger 能影响下一章
- 章节摘要能被召回
- rejected chapter 不污染长期记忆
- accepted chapter 才会更新摘要、Qdrant、Neo4j
- 每次 LLM 调用有 token usage 记录
- 能导出 Markdown
```

只要这个闭环跑通，MythosForge 本地版 MVP 就成立。
