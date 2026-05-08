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
    llm_model: str = "gpt-4o-mini"


@lru_cache
def get_settings() -> Settings:
    return Settings()
