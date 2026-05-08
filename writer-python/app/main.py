from fastapi import FastAPI
from fastapi.responses import PlainTextResponse

app = FastAPI(title="MythosForge Writer")


@app.get("/health", response_class=PlainTextResponse)
def health() -> str:
    return "ok"


@app.get("/api/writer/health", response_class=PlainTextResponse)
def writer_health() -> str:
    return "ok"
