"""HTTP：narrative-metrics 契约（独立 FastAPI 子应用 + httpx AsyncClient ASGI）。"""

from __future__ import annotations

import asyncio
import unittest

import httpx
from fastapi import FastAPI
from httpx import ASGITransport
from pydantic import BaseModel, ConfigDict, Field

from app.services.narrative_metrics_heuristic import heuristic_metrics_no_llm


class NarrativeMetricsRequest(BaseModel):
    """字段与 app.api.chapters.NarrativeMetricsRequest 对齐。"""

    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_text: str = Field(alias="chapterText", min_length=1)


def _mini_app() -> FastAPI:
    app = FastAPI()

    @app.post("/api/writer/chapters/narrative-metrics")
    def chapters_narrative_metrics(body: NarrativeMetricsRequest) -> dict:
        h = heuristic_metrics_no_llm(body.chapter_text)
        return {
            "tensionScore": h["tensionScore"],
            "styleSimilarity": h["styleSimilarity"],
            "raw": h["raw"],
        }

    return app


class NarrativeMetricsHttpRouteTest(unittest.TestCase):
    async def _post(self, payload: dict) -> httpx.Response:
        app = _mini_app()
        transport = ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            return await client.post("/api/writer/chapters/narrative-metrics", json=payload)

    def test_post_narrative_metrics_returns_scores(self) -> None:
        r = asyncio.run(
            self._post({"projectId": "p1", "chapterNo": 1, "chapterText": ("正文片段。" * 200)})
        )
        self.assertEqual(r.status_code, 200, r.text)
        body = r.json()
        self.assertIn("tensionScore", body)
        self.assertIn("styleSimilarity", body)
        self.assertIsInstance(body["tensionScore"], (int, float))
        self.assertIsInstance(body["styleSimilarity"], (int, float))

    def test_post_narrative_metrics_validation_422(self) -> None:
        r = asyncio.run(self._post({"projectId": "", "chapterNo": 0, "chapterText": ""}))
        self.assertEqual(r.status_code, 422)


if __name__ == "__main__":
    unittest.main()
