"""敏感词 AC 扫描单元测试（unittest，不依赖 pytest）。"""

from __future__ import annotations

import importlib.util
import unittest

from app.services.sensitive_scanner import scan_sensitive, should_fail_critic


def _has_pyahocorasick() -> bool:
    return importlib.util.find_spec("ahocorasick") is not None


class SensitiveScannerTest(unittest.TestCase):
    @unittest.skipUnless(_has_pyahocorasick(), "pyahocorasick not installed")
    def test_scan_finds_block_word(self) -> None:
        text = "本章剧情正常，突然出现测试合规拦截词A，然后结束。"
        report = scan_sensitive(text, "default")
        self.assertGreaterEqual(report["blockCount"], 1)
        self.assertEqual(report["disposition"], "block")
        self.assertTrue(should_fail_critic(report))

    def test_scan_clean_text(self) -> None:
        text = "这是一段没有任何占位拦截词的普通网文章节正文。"
        report = scan_sensitive(text, "default")
        self.assertEqual(report["blockCount"], 0)
        self.assertEqual(report["disposition"], "pass")
        self.assertFalse(should_fail_critic(report))


if __name__ == "__main__":
    unittest.main()
