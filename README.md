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
