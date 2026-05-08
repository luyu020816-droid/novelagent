import logging

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse, PlainTextResponse
from pydantic import BaseModel

from app.api import genre as genre_routes
from app.api import test_agent as test_agent_routes
from app.config import _WRITER_ENV, get_settings

logger = logging.getLogger("uvicorn.error")

app = FastAPI(title="MythosForge Writer")
app.include_router(test_agent_routes.router)
app.include_router(genre_routes.router)


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
