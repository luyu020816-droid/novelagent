# MythosForge（Day 1）

本地优先长篇小说创作引擎 — 当前为 Day 1 最小可运行骨架。

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
