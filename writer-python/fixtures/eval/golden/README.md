# Golden 评测工件（离线 eval）

从真实生成导出 JSON，字段建议：

- `chapterText` / `chapter_text`
- `scenePlan` / `scene_plan`（含 `beats` ≥4）
- `criticReport` / `critic_report`（含 `dimensions[]`）

## 运行

```bash
cd writer-python
python scripts/eval_critic_dimensions.py --dir fixtures/eval/golden
python scripts/eval_harness_report.py --artifact fixtures/eval/golden/sample_ch01_pass.json
```

改 prompt 前后各跑一遍，对比 `beat_coverage` 等维度的 pass_rate。
