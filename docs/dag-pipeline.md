# PlotPilot 风格 DAG 流水线

## 是什么

**可配置 DAG + LangGraph 运行时**：与 PlotPilot 相同，DAG 描述「有哪些节点、怎么连」；真正执行仍由 **LangGraph** 编译运行。可以理解为 **LangGraph 的配置化升级版**——拓扑可改，不必每次改 Python。

## 默认流程（22 个实例节点 / 30 种类型）

```
上下文链(7) → 行为协议 → 规划(3) → Token预算 → 节拍放大 → 剧情引擎
  → 合规/篇幅/反AI/张力 → 叙事审查(Critic) → 熔断网关
       ├─ 通过 → 润色 → 终稿 Anti-AI
       └─ 不通过 → 重写(≤3) → 剧情引擎
```

## 手动添加节点

注册表共 **30** 种 `node_type`；默认 DAG 未启用的可插入自定义 DAG，例如：

- `world_bible_all` / `world_characters`
- `review_timeline` / `review_storyline`
- `generic_llm`（简短描述生成配置）
- `val_style` / `val_foreshadow` / `gw_review` 等

## 通用节点工厂

```python
from app.dag.node_factory import scaffold_node_from_description
from app.dag.models import NodeCategory

node, meta = scaffold_node_from_description(
    "检查本章师徒关系是否与设定矛盾",
    instance_id="val_master_disciple",
    category=NodeCategory.VALIDATION,
)
```

## API / 代码入口

- 默认 DAG：`app.dag.defaults.get_default_dag()`
- 编译：`app.dag.compiler.compile_dag_to_langgraph(dag, gateway=...)`
- 章节生成：`app.graph.chapter_graph.build_chapter_graph(gateway)`；请求体可带 `dagDefinition`
- **PG 持久化**：`project_dag_versions`（Flyway V22）；Java `GET/PUT /api/projects/{id}/dag/active`
- **画布 UI**：`/projects/{id}/workflow`（React Flow）
- **节点工厂**：`POST /api/writer/dag/scaffold-node` 或 Java `POST .../dag/scaffold-node`

## 测试

```bash
python -m unittest tests.test_dag_registry tests.test_dag_compiler tests.test_dag_default -v
```
