# MythosForge（Day 1）

本地优先长篇小说创作引擎 — 当前为 Day 1 最小可运行骨架。

## README 约定

按计划推进时：**每一天新增或未完成的能力**，都在本文件 **追加对应 Day 的小节**（不删前面已完成天的说明），便于对照 `15plan.md` 与本地环境。

联调踩坑与面试可用的复盘写在 **[问题.md](问题.md)**；多次失败后归纳条目可使用 Cursor 技能 **`incident-log`**（[.cursor/skills/incident-log/SKILL.md](.cursor/skills/incident-log/SKILL.md)）。

Day 1–4 **讲师串讲稿**（架构分工、关键类、联调演示路径）：[docs/讲师串讲-Day1-4.md](docs/讲师串讲-Day1-4.md)。

## 前置条件

- Docker Desktop（或兼容的 Docker）
- JDK 17+
- Maven 3.9+
- Python 3.11+（建议 venv）
- Node.js 20+

## 启动顺序

### 1. 基础设施

```bash
docker compose up -d
```

确认 PostgreSQL / Redis / Qdrant / Neo4j 均为 running。

### 2. Java API（端口 8080）

```bash
cd backend-java
mvn spring-boot:run
```

### 3. Writer Python（端口 8000，Day 1 仅 health）

```bash
cd writer-python
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

### 4. 前端（端口 5173）

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173 — 列表与创建项目通过 Vite 代理访问 `/api/*` → Java。

## Day 1 验收

见下方「Day 1 完成报告」中的验收步骤。

---

## Day 2 说明

在 Day 1 可运行骨架之上，Day 2 补充：**PostgreSQL 基础表**、**Java 调用 Writer（FastAPI）**、**项目详情页与 Writer 连通性展示**。启动顺序仍与上文一致；更新后端代码后请重新执行 `mvn spring-boot:run`（需释放 8080 后再启动）。

### Day 2 数据库（Flyway）

- 已有：`projects`（V1）。
- 新增（V2）：`genre_decision_contracts`、`novel_seed_contracts`、`story_contracts`、`chapter_contracts`、`generation_jobs`、`chapter_versions`、`chapter_commits`、`memory_summaries`、`llm_usage_log`。

应用启动时会自动迁移；可用 `psql` 中 `\dt` 核对表是否存在。

### Day 2 Writer（Python）

- `GET /api/writer/health` — 健康检查（纯文本 `ok`）。
- `POST /api/writer/test` — 连通性测试（JSON，供 Java 探测）。

### Day 2 Java API 补充

- `GET /api/projects/{projectId}` — 单个项目。
- `GET /api/projects/{projectId}/detail` — 项目信息 + 对 Writer 的 health / test 探测结果（`writerEngine`）。

Writer 根地址配置：`backend-java/src/main/resources/application.yml` 中 `mythosforge.writer.base-url`（默认 `http://127.0.0.1:8000`）。

### Day 2 前端

- 项目列表中点击项目名称进入 **项目详情**。
- 详情页展示项目基本信息及 Writer 探测结果（health / test）；二者均成功时显示连接正常。

### Day 2 验收（简要）

1. 数据库中能看到 Day 2 新增的 9 张业务表（及 `flyway_schema_history` 版本 ≥ 2）。
2. 同时启动 Writer（8000）与 Java（8080）后，`GET /api/projects/{id}/detail` 中 `writerEngine.health.ok` 与 `writerEngine.test.ok` 为 `true`。
3. 浏览器打开 http://localhost:5173 ，进入某项目详情页，能看到项目字段与 Writer 状态。

### Day 2：重启 Java 后表仍未生成（排查）

Flyway **只在启动时**跑迁移；若库里已有 `flyway_schema_history` 且版本 ≥ 2，**不会**重复执行 V2。常见是「应用连上的库」和「你在客户端里看的库」不是同一个。

1. **先做一次干净构建再启动**（避免 `target/classes` 里仍是旧资源、未带上 `V2__….sql`）：

   ```bash
   cd backend-java
   mvn clean spring-boot:run
   ```

2. **看启动日志**（应出现 Flyway 校验/迁移一行，例如 `Successfully validated 2 migrations` 或 `Migrating ... to version "2 - init generation tables"`）。若没有 Flyway 日志，检查是否改动了 `spring.flyway.enabled`。

3. **确认 JDBC 与客户端一致**：`application.yml` 里默认库名为 **`mythosforge`**（`POSTGRES_DB` / URL 最后一段）。在 **同一个库** 里执行：

   ```sql
   SELECT version, success, script FROM flyway_schema_history ORDER BY installed_rank;
   \dt
   ```

   - 若只有 `version = 1`，说明运行时 classpath 里**没有**读到 V2 脚本 → 回到步骤 1，并确认存在文件  
     `backend-java/src/main/resources/db/migration/V2__init_generation_tables.sql`（注意 `V2` 与脚本名之间是 **两条下划线** `__`）。

4. **Windows 上多个 PostgreSQL**：若本机还装过 Postgres，`localhost:5432` 可能连到 **本机服务** 而非 Docker。可用 Docker 直接进库核对（容器名按 `docker ps` 调整，示例为 `novel-postgres-1`）：

   ```bash
   docker exec -it novel-postgres-1 psql -U mythosforge -d mythosforge -c "\dt"
   ```

5. **历史里已有 V2 但表被人删过**：Flyway 默认不会重跑。需由 DBA 处理（例如删除 `flyway_schema_history` 中对应失败/错误版本记录后 **谨慎** 再迁移），或在新库上重建；不要随意在生产库上删历史。

---

## Day 3 说明（Writer：LLM Gateway）

Writer 使用 **OpenAI 兼容 HTTP API**（`openai` Python SDK）。不接 Ollama 时，可把 **DeepSeek** 等服务商填进下列环境变量。

### API Key / Base URL / 模型填在哪

仓库里**不会**自带 `.env`（避免把密钥提交进 Git）。请在 **`writer-python` 目录** 复制模板再填写：

```bash
cd writer-python
# Windows:
copy .env.example .env
# macOS / Linux:
# cp .env.example .env
# 再编辑 .env，填入 OPENAI_API_KEY 等
```

`.env` 与 `uvicorn` 工作目录一致；`app/config.py` 通过 `pydantic-settings` 读取该文件。

```env
# DeepSeek（OpenAI 兼容）：密钥填在 OPENAI_API_KEY（变量名固定，供 SDK 使用）
OPENAI_API_KEY=你的_deepseek_api_key

# DeepSeek 官方兼容接口地址（以文档为准）
OPENAI_BASE_URL=https://api.deepseek.com

# 模型名以服务商控制台 / 文档为准（DeepSeek V4 Flash）
LLM_MODEL=deepseek-v4-flash
```

也可以在同一终端里 **导出环境变量**（不写 `.env`），效果相同。

**填哪一个 `.env`？** Writer 只读 **`writer-python/.env`**（已固定路径，与你在哪个目录执行 `uvicorn` 无关）。仓库根目录的 `.env.example` 是给 **整个项目备忘** 用的，**不会**自动被 Writer 读取；除非你把变量做成 **系统 / 终端环境变量**。

说明：`OPENAI_*` 只是 SDK 约定的名字；填的是 **DeepSeek 的 key**，不是必须 OpenAI 账号。若仍返回 `OPENAI_API_KEY is not set`，说明 **`writer-python/.env` 里没有这一行** 或 **键名写错**（必须是 `OPENAI_API_KEY=`）。`POST /api/writer/test-agent` 的请求体字段是 **`user_hint`**（可选），不是 `message`。

### Day 3 相关依赖与接口（摘要）

- 需安装 `writer-python/requirements.txt`（含 `openai`、`psycopg`、`tiktoken` 等）。
- 调用 LLM 会写入 PostgreSQL 表 **`llm_usage_log`**（需本地 Postgres 可用）。
- 主要接口：`POST /api/writer/test-agent`（详见 `15plan.md` Day 3）。

---

## Day 4 说明（题材推荐 Genre Decision）

第一版 **Genre Decision**：静态题材卡 + 平台 profile + 规则 YAML，经 **Genre Scout → Trope Strategist → Market Fit Scorer** 三轮 **LLM Gateway** 调用（prompt 均在 `writer-python/prompts/*.md`），产出结构化 **Genre Decision Contract**；Java 写入 **`genre_decision_contracts`**；前端在项目详情页可填偏好并展示 3 个候选。

### Day 4 数据文件（writer-python）

- `data/trope_cards/`：`urban_tech_system.json`、`fantasy_leveling.json`、`romance_rebirth.json`
- `data/platform_profiles/`：`fanqie.yaml`、`qidian.yaml`
- `data/genre_rules/`：`default.yaml`

### Day 4 接口

- **Python**：`POST /api/writer/genre/recommend` — Body 与 Java 一致（camelCase）：`targetPlatform`、`genderChannel`、`preferredGenres`、`avoid`、`writingStrength`、`riskPreference`，可选 `projectId`（Java 会自动带上）。
- **Java**：`POST /api/projects/{projectId}/genre/recommend` — 请求体同上；响应：`contractId` + `contract`（含 `selectedDirection`、`candidateRankings`（3 条）、`recommendedCoreHook`、`riskNotes`）。

### Day 4 前置条件

- PostgreSQL、**Writer**（8000）配置好 **`OPENAI_API_KEY`**（见 Day 3）。
- **Java**（8080）的 `mythosforge.writer.base-url` 指向 Writer。
- 题材推荐会写入 **`llm_usage_log`**（`agent_name`：`genre_scout`、`trope_strategist`、`market_fit_scorer`，以及可能的 `json_repair`）。

### Day 4 排查（422 / 502）

- **冒烟脚本**：[scripts/smoke-genre-recommend.ps1](scripts/smoke-genre-recommend.ps1) — A 段空 body 应对齐 Python `loc=["body"]`；B 直连 Writer；C 经 Java，502 时响应 JSON 中的 **`message`** 字段含 Writer 上游说明（需 `server.error.include-message: always`，见 `application.yml`）。
- **日志**：Writer 对 `POST /api/writer/genre/recommend` 打印 **client / content-length / content-type**；Java 对 **`WriterEngineClient`**、`**RestClient**` 开 **DEBUG** 可见出站 JSON 预览与长度。

---

## SSE 流式（题材 / 初始化小说）

- **协议说明**（事件类型、`artifact` / `persisted`、与 LangGraph 迁移提示）：[docs/sse-event-protocol.md](docs/sse-event-protocol.md)。
- **Python**：所有经 `LLMGateway` 的调用在 OpenAI SDK 层使用 **`stream=True`**；长流程另提供 SSE 路由，例如 `POST /api/writer/genre/recommend/stream`、`POST /api/writer/init-novel/stream`。
- **Java**：`WriterSseProxyService` 将 Writer SSE **透传**至浏览器，并在 `artifact` 后 **落库**、追加 **`persisted`** 事件（如 `POST /api/projects/{projectId}/genre/recommend/stream`、`POST .../story/init/stream`）。
- **前端**：项目详情「题材推荐」与「初始化小说」默认使用 **SSE**，界面展示 `llm_delta` 增量日志。
