import logging

from fastapi import FastAPI
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

from app.api import test_agent as test_agent_routes
from app.config import _WRITER_ENV, get_settings

logger = logging.getLogger("uvicorn.error")

app = FastAPI(title="MythosForge Writer")
app.include_router(test_agent_routes.router)


@app.on_event("startup")
def _log_env_hint() -> None:
    s = get_settings()
    if s.openai_api_key:
        logger.info("Writer: OPENAI_API_KEY loaded (LLM_MODEL=%s)", s.llm_model)
    else:
        logger.warning(
            "Writer: OPENAI_API_KEY missing. Create %s from .env.example (do not put secrets in .env.example).",
            _WRITER_ENV,
        )


class WriterTestResponse(BaseModel):
    ok: bool = True
    message: str = "writer-test-ok"


@app.get("/health", response_class=PlainTextResponse)
def health() -> str:
    return "ok"


@app.get("/api/writer/health", response_class=PlainTextResponse)
def writer_health() -> str:
    return "ok"


@app.post("/api/writer/test")
def writer_test() -> WriterTestResponse:
    return WriterTestResponse()
