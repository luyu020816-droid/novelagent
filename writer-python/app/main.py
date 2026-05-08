from fastapi import FastAPI
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel

app = FastAPI(title="MythosForge Writer")


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
