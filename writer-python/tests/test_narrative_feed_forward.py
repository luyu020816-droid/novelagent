from app.services.narrative_feed_forward import build_previously_on


def test_previously_on_last_three_chapters() -> None:
    hist = [
        {"chapterNo": 1, "summary": {"oneLiner": "开局"}},
        {"chapterNo": 2, "summary": {"oneLiner": "发展"}},
        {"chapterNo": 3, "summary": {"oneLiner": "转折"}},
        {"chapterNo": 4, "summary": {"oneLiner": "高潮前"}},
    ]
    text = build_previously_on(hist, chapter_no=5, max_chapters=3)
    assert "第2章" in text
    assert "第3章" in text
    assert "第4章" in text
    assert "开局" not in text or "第1章" not in text
