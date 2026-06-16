import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import BaseModel

from app.api import audit as audit_routes
from app.api import chapters as chapters_routes
from app.api import knowledge as knowledge_routes
from app.api import lore as lore_routes
from app.api import genre as genre_routes
from app.api import init_novel as init_novel_routes
from app.api import copilot as copilot_routes
from app.api import dag as dag_routes
from app.api import setup as setup_routes
from app.api import writer_skills as writer_skills_routes
from app.api import test_agent as test_agent_routes
from app.api import writer_tools as writer_tools_routes
from app.config import _WRITER_ENV, get_settings

logger = logging.getLogger("uvicorn.error")

app = FastAPI(title="MythosForge Writer")
app.include_router(test_agent_routes.router)
app.include_router(genre_routes.router)
app.include_router(init_novel_routes.router)
app.include_router(writer_skills_routes.router)
app.include_router(copilot_routes.router)
app.include_router(setup_routes.router)
app.include_router(chapters_routes.router)
app.include_router(dag_routes.router)
app.include_router(audit_routes.router)
app.include_router(knowledge_routes.router)
app.include_router(lore_routes.router)
app.include_router(writer_tools_routes.router)


@app.middleware("http")
async def _log_genre_recommend_inbound(request: Request, call_next):
    """记录 genre/recommend 入站元数据（不读 body，避免影响校验）。"""
    if request.method == "POST" and request.url.path == "/api/writer/genre/recommend":
        host = request.client.host if request.client else "?"
        port = request.client.port if request.client else "?"
        cl = request.headers.get("content-length", "?")
        ct = request.headers.get("content-type", "?")
        logger.info(
            "genre/recommend inbound client=%s:%s content-length=%s content-type=%s",
            host,
            port,
            cl,
            ct,
        )
    return await call_next(request)


@app.exception_handler(RequestValidationError)
async def _validation_422(request: Request, exc: RequestValidationError) -> JSONResponse:
    """422 时打出校验明细，便于对齐 Java/前端字段名。"""
    logger.warning("RequestValidationError path=%s errors=%s", request.url.path, exc.errors())
    return JSONResponse(status_code=422, content={"detail": exc.errors()})


@app.on_event("startup")
def _log_env_hint() -> None:
    s = get_settings()
    if s.openai_api_key:
        logger.info("Writer: OPENAI_API_KEY loaded (LLM_MODEL=%s)", s.llm_model)
    else:
        logger.warning(
            "Writer: OPENAI_API_KEY missing (chat/init will fail). Create %s — see .env.example.",
            _WRITER_ENV,
        )

    if s.embedding_api_key:
        logger.info(
            "Writer: EMBEDDING_API_KEY loaded (EMBEDDING_MODEL=%s, EMBEDDING_OPENAI_BASE_URL=%s)",
            s.embedding_model,
            s.embedding_openai_base_url or "https://api.openai.com/v1",
        )
    elif s.openai_api_key:
        logger.info(
            "Writer: embeddings reuse OPENAI_API_KEY (+ OPENAI_BASE_URL if set); "
            "若对话走 DeepSeek，请另设 EMBEDDING_API_KEY + EMBEDDING_OPENAI_BASE_URL=https://api.openai.com/v1"
        )
    elif s.vector_sync_enabled:
        logger.warning(
            "Writer: VECTOR_SYNC_ENABLED but no EMBEDDING_API_KEY / OPENAI_API_KEY — Qdrant 同步与检索不可用"
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
