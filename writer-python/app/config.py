from functools import lru_cache
from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict

# 始终读 writer-python/.env，与从哪一级目录启动 uvicorn 无关
_WRITER_ROOT = Path(__file__).resolve().parent.parent
_WRITER_ENV = _WRITER_ROOT / ".env"


class Settings(BaseSettings):
    # 固定指向 writer-python/.env；文件不存在时 pydantic-settings 会跳过，不报错
    model_config = SettingsConfigDict(
        env_file=_WRITER_ENV,
        env_file_encoding="utf-8",
        extra="ignore",
    )

    postgres_host: str = "localhost"
    postgres_port: int = 5432
    postgres_db: str = "mythosforge"
    postgres_user: str = "mythosforge"
    postgres_password: str = "mythosforge"

    openai_api_key: str | None = None
    openai_base_url: str | None = None
    #: 对话模型 id（OpenAI 兼容）；DeepSeek 官方示例为 deepseek-v4-flash；纯 OpenAI 可在 .env 写 LLM_MODEL=gpt-4o-mini
    llm_model: str = "deepseek-v4-flash"
    #: 可选：按节点覆盖模型（不填则回落到 llm_model）。环境变量示例：LLM_MODEL_CRITIC、LLM_MODEL_GHOSTWRITER 等
    llm_model_planner: str | None = None
    llm_model_ghostwriter: str | None = None
    llm_model_critic: str | None = None
    llm_model_stylist: str | None = None

    # Day 10：Qdrant + 向量检索（可与对话模型不同基址，便于 DeepSeek 对话 + OpenAI 向量）
    qdrant_url: str = "http://localhost:6333"
    qdrant_api_key: str | None = None
    qdrant_collection: str = "mythos_knowledge"
    # 云端向量模型：OpenAI text-embedding-3-small（性价比高，1536 维）；也可换成服务商支持的模型名
    embedding_model: str = "text-embedding-3-small"
    embedding_dimensions: int = 1536
    #: 仅用于 embeddings 的 Key；不填则回退用 OPENAI_API_KEY（对话走 DeepSeek 时请单独填此项指向 OpenAI 等）
    embedding_api_key: str | None = None
    embedding_openai_base_url: str | None = None
    vector_sync_enabled: bool = True

    # Day 12：Neo4j 世界观图谱（docker-compose 默认 neo4j / mythosforge）
    neo4j_enabled: bool = True
    lore_graph_enabled: bool = True
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_user: str = "neo4j"
    neo4j_password: str = "mythosforge"

    # Day 11：上下文窗口预算（tiktoken 估算；Ghostwriter 输入约占窗口比例）
    llm_context_window_tokens: int = 128000
    llm_context_input_fraction: float = 0.8

    def embedding_auth_configured(self) -> bool:
        return bool((self.embedding_api_key or self.openai_api_key or "").strip())

    def resolve_llm_model(self, node_name: str | None) -> str:
        """章节流水线按节点选用模型（未配置则使用 llm_model）。"""
        if not node_name:
            return self.llm_model
        key = node_name.replace("-", "_")
        override = {
            "planner": self.llm_model_planner,
            "context_curator_intent": self.llm_model_planner,
            "ghostwriter": self.llm_model_ghostwriter,
            "critic": self.llm_model_critic,
            "stylist": self.llm_model_stylist,
        }.get(key)
        return override if (override and str(override).strip()) else self.llm_model


@lru_cache
def get_settings() -> Settings:
    return Settings()
