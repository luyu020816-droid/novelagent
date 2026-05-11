# MythosForge：事件驱动 SSE 流式协议（Writer ↔ Java ↔ 前端）

## 设计陈述（工程叙事）

设计并实现了一套基于 **事件驱动（Event-Driven）** 的 **SSE（Server-Sent Events）** 流式响应协议。通过在 **Java 网关层** 实现 **流式透传（Streaming Proxy）**，前端在多智能体长流程生成中能获得 **即时反馈**（节点边界、`llm_delta` 文本增量、错误与完成信号）。该协议将「编排形态」与传输层解耦：**底层编排**可保持单向线性流水线，也可在未来演进为 **LangGraph 状态图**，只要继续产出同一套事件语义，即可实现前后端逻辑的 **平滑迁移**。

## 传输与路径

- **浏览器 → Java**：`POST`，`Accept: text/event-stream`，正文仍为 JSON（业务入参）。
- **Java → Writer**：同源 SSE，`HttpClient` 读取流并按帧转发至 `SseEmitter`。
- **Writer → Java**：FastAPI `StreamingResponse`，`media_type=text/event-stream`。

开发环境下，前端继续通过 Vite 将 `/api` 代理到 Java（8080），**无需**浏览器直连 Writer（8000）。

## 事件类型（约定）

| event | data（JSON） | 说明 |
|-------|----------------|------|
| `pipeline_start` | `{ pipeline, projectId? }` | 流水线开始 |
| `node_start` | `{ node }` | 某个 Agent / 阶段开始 |
| `llm_delta` | `{ node, text }` | **OpenAI 流式**聚合出的增量片段（可能含不完整 JSON） |
| `node_end` | `{ node, ok, ... }` | 阶段结束 |
| `artifact` | `{ kind, data }` | **可落库或可渲染的最终结构化载荷**（如题材契约、Init 全包） |
| `done` | `{ ok }` | Writer 侧流水线结束 |
| `error` | `{ message }` | 失败说明 |
| `persisted` | （Java 追加） | 网关在完成 DB 写入后追加，如 `{ contractId, contract }` 或 init 的 id |

## Python 侧要点

- **`LLMGateway`**：所有 `chat.completions` 调用均使用 **`stream=True`** 消费增量；可选 `on_delta` 回调用于 SSE。
- **阻塞流水线**：仍可在同步函数内顺序调用 Agent；通过 **线程 + Queue**（`sse_queue_runner`）将增量事件泵入 `StreamingResponse`，避免阻塞 ASGI。
- **兼容**：若上游不支持 `stream_options.include_usage`，网关会自动 **降级重试**（去掉 `stream_options`）。

## Java 侧要点

- **`WriterSseProxyService`**：`POST` Writer SSE URL，解析 `event:` / `data:`，逐帧 `SseEmitter.send`。
- **持久化**：在收到 `artifact` 且 `kind` 匹配时，于 **`REQUIRES_NEW`** 事务中落库，并发送 **`persisted`** 事件（便于前端更新真实 id）。
- **同步 JSON API**：仍保留（如 `POST .../genre/recommend`），适用于脚本或非流式客户端。

## 前端要点

- 使用 **`fetch` + `ReadableStream`** 解析 SSE（`frontend/src/api/sse.ts`）。
- **题材推荐**、**初始化小说** 默认走 **流式接口**，界面展示增量日志 + 最终结果。

## 与 LangGraph 的关系（迁移提示）

后续若以 LangGraph 替换线性 `agent.run` 串联，建议：

- **保留本事件的语义层**（尤其是 `artifact` / `node_*` / `llm_delta` 的 payload 形状）。
- 将图的 **节点完成**、**状态快照**、**LLM token** 映射到上述事件即可；**无需**更换 Java 透传与前端解析框架。
