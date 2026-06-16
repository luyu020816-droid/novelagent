# 章节质量回归 Runbook（PlotPilot 借鉴落地）

## 1. 规则层单测（确定性，PR 必跑）

```bash
cd novel/writer-python
python -m unittest discover -s tests -p "test_*.py" -v
```

```bash
cd novel/backend-java
mvn test
```

CI：`.github/workflows/ci.yml`（`java-tests` + `python-tests` + `smoke-offline`）。

## 2. 烟囱

```powershell
cd novel
.\scripts\smoke_stack.ps1
```

## 3. 离线 Golden + Critic 维度通过率

```bash
cd writer-python
python scripts/eval_critic_dimensions.py --dir fixtures/eval/golden
python scripts/eval_chapter_qa.py --text path/to/chapter.txt
python scripts/eval_harness_report.py --artifact fixtures/eval/golden/sample_ch01_pass.json
```

改 prompt 前后各跑 `eval_critic_dimensions`，对比 `beat_coverage` 等列 pass_rate。

## 4. CPMS 版本（PG）

```bash
# Flyway V21 后
cd writer-python
python scripts/seed_cpms_prompts.py
```

LLM 调用写入 `llm_usage_log.prompt_version`，便于对比两版 prompt。

## 5. 手测闭环

见 [manual-e2e-checklist.md](manual-e2e-checklist.md)。

## 6. 定稿 Aftermath 可观测

见 [aftermath-pipeline.md](aftermath-pipeline.md)（同步/异步步骤与 `vector_sync_status`）。
