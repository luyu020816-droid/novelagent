# MythosForge

本地优先的 **AI 辅助长篇小说创作平台**：从题材决策与故事初始化，到章纲与单章 **LangGraph** 流水线生成、人工审核定稿、全书导出与用量统计，全流程可在自有环境跑通。数据按 **作品（Project）** 隔离，支持多部书并行。

---

## 核心能力

- **题材与故事初始化**：题材推荐 / 访谈 / 故事钩子等；阻塞或 **SSE 流式**；生成并持久化 `novel_seed`、`story_contract`、多章 `chapter_contracts`。
- **章节写作**：**LangGraph** 多阶段编排（上下文策展、规划、Token 预算、成稿、审查、风格等）；**SSE 同步生成** 与 **RabbitMQ + Worker 异步排队** 双模式。
- **上下文与记忆**：**Neo4j** 世界观图谱召回、**Qdrant** 向量检索历史正文片段；Tiktoken 预算与 **Priority** 裁剪，优先保留故事/章纲契约。
- **质量与治理**：结构化 Critic、疲劳/套话扫描、`anti_ai` 重写模式；**作者意图 / 不可违背项 / 风格指纹** 注入 Story Canon；丛书 Skill（`writer-python/app/skills/library`）。
- **产品与运维**：章节多版本（待审核 → 接受/退回）、已定稿全书 **Markdown 导出**、按章 **LLM 用量与 ROI**、**双书对照** 进度页、全书实体字符串替换等。
- **编排层**：Java 负责 REST、持久化、SSE/RabbitMQ 桥接；Python **FastAPI** 负责 Agent 与 LangGraph；前端 **React + Vite** 通过代理访问 `/api`。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | React、TypeScript、Vite、React Router；SSE 消费长流程 |
| 后端 API | Java 17、Spring Boot、Spring Data JPA、**Flyway**、RestClient |
| 数据库 / 中间件 | **PostgreSQL**（JSONB）、**Redis**、**RabbitMQ** |
| AI 引擎 | Python **FastAPI**、**LangGraph**（`langgraph`）、OpenAI 兼容 **LLMGateway**（流式 + usage）、**tiktoken** |
| 检索与图谱 | **Neo4j**、**Qdrant**（向量）、Embedding 可配置 |
| 容器 | Docker Compose 拉起 Postgres / Redis / Qdrant / Neo4j / RabbitMQ |

---

## 仓库结构（摘要）

```
novel/
├── backend-java/          # Spring Boot：项目、题材、故事、章节、评审、异步 Job、Writer 代理
├── frontend/              # Vite 前端
├── writer-python/         # FastAPI：Genre / Init-novel / Chapters LangGraph / Lore / Skills / Worker
├── docker-compose.yml     # 本地依赖服务
├── run_local.sh           # Linux/macOS：一键起 Docker + Java + Writer + Worker + 前端
├── start.sh               # 备忘片段（见下文「启动」）
├── docs/                  # SSE 协议说明、串讲稿等
├── 15plan.md              # 迭代计划（历史天数说明）
└── 问题.md                # 踩坑与复盘（可选）
```

写作流水线与 Skill 说明：**[writer-python/app/skills/library/README.md](writer-python/app/skills/library/README.md)**。

---

## 环境要求

- Docker Desktop（或兼容 Docker）
- JDK 17+、Maven 3.9+
- Python 3.11+（建议使用 `venv`）
- Node.js 20+

---

## 启动方式

### 方式 A：`run_local.sh`（Linux / macOS）

在项目根目录：

```bash
chmod +x run_local.sh
./run_local.sh
```

会拉起 Compose 中的 **postgres、redis、qdrant、neo4j、rabbitmq**，并在后台启动 **Java :8080**、**Writer :8000**、**`worker.py`（章节队列消费者）**、**前端 :5173**。  
需配置 **`writer-python/.env`**（见下文）及 Java 中与 Worker 一致的 **`MYTHOSFORGE_INTERNAL_TOKEN`**（脚本默认 `dev-internal-token`，须与 `application.yml` 一致）。

### 方式 B：手动（Windows / 通用）

1. **基础设施**

   ```bash
   docker compose up -d
   ```

   确认 Postgres、Redis、Qdrant、Neo4j、RabbitMQ 就绪（异步章节需 RabbitMQ；Worker 需单独进程）。

2. **Java API — `http://localhost:8080`**

   ```bash
   cd backend-java
   mvn spring-boot:run
   ```

3. **Writer — `http://localhost:8000`**

   ```bash
   cd writer-python
   python -m venv .venv
   # Windows: .\.venv\Scripts\activate
   # Linux/macOS: source .venv/bin/activate
   pip install -r requirements.txt
   uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```

4. **异步章节 Worker（可选，与 RabbitMQ 配套）**

   ```bash
   cd writer-python
   python worker.py
   ```

5. **前端 — `http://localhost:5173`**

   ```bash
   cd frontend
   npm install
   npm run dev
   ```

浏览器打开前端地址；`/api` 由 Vite 代理到 Java。

---

## 配置要点

### Writer（`writer-python/.env`）

从模板复制后填写（**不要**把真实密钥提交到 Git）：

```bash
cd writer-python
cp .env.example .env   # Windows 可用 copy .env.example .env
```

至少需要配置 OpenAI 兼容的 **`OPENAI_API_KEY`**，以及按需设置 **`OPENAI_BASE_URL`**、`LLM_MODEL`、Embedding / Qdrant / Neo4j 等（见 `.env.example` 注释）。使用 DeepSeek 等兼容网关时，通常将密钥写入 **`OPENAI_API_KEY`**，将 **`OPENAI_BASE_URL`** 设为服务商文档地址，**`LLM_MODEL`** 设为控制台中的模型名。

### Java 调用 Writer

`backend-java/src/main/resources/application.yml` 中 **`mythosforge.writer.base-url`**（默认 `http://127.0.0.1:8000`）。

### RabbitMQ

Compose 默认用户/密码：`mythosforge` / `mythosforge`；管理界面 **http://localhost:15672**。

---

## 常用文档

| 文档 | 说明 |
|------|------|
| [docs/sse-event-protocol.md](docs/sse-event-protocol.md) | SSE 事件、`artifact` / `persisted` 约定 |
| [docs/讲师串讲-Day1-4.md](docs/讲师串讲-Day1-4.md) | 早期架构与联调串讲 |
| [15plan.md](15plan.md) | 按天迭代计划与接口演进记录 |
| [问题.md](问题.md) | 踩坑与复盘（若存在） |
| [writer-python/app/skills/library/README.md](writer-python/app/skills/library/README.md) | 丛书 Skill 与 `fatigue/` 配置说明 |

---

## 数据库迁移（Flyway）

迁移脚本位于 `backend-java/src/main/resources/db/migration/`。**仅在应用启动时执行**；若表缺失但历史版本已高，请核对是否连错库、或需 `mvn clean spring-boot:run` 与 Docker 内实际库名 **`mythosforge`** 一致。详细排查思路见旧版 README 中 Flyway 小节已并入日常运维经验；也可检索仓库内 `flyway_schema_history`。

---

## 说明

- 迭代过程中的 **Day N** 细节与历史接口说明仍以 **[15plan.md](15plan.md)** 为准。
- 多次联调失败后的条目归纳可使用 Cursor 技能 **incident-log**（[.cursor/skills/incident-log/SKILL.md](.cursor/skills/incident-log/SKILL.md)），并同步到 **问题.md**。
