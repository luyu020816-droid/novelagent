# 敏感词词表（合规扫描）

章节 **Critic 审计节点** 在 LLM 评审前/后调用 `scan_sensitive()`，使用 **Aho-Corasick 自动机**（`pyahocorasick`）对正文做 **O(N)** 多模式匹配。

## 目录结构

```
sensitive_lexicon/
  default/
    manifest.yaml    # 分类、severity（block | review | log）
    politics.txt     # 每行一词，UTF-8
    porn.txt
    caution.txt
```

## severity 与分流

| severity | Critic 行为 |
|----------|-------------|
| `block`  | 计入 `blockCount`，默认 **pass=false**，需重写 |
| `review` | 计入 `reviewCount`，打标待人工复核，可不拦 |
| `log`    | 仅写入 `sensitive_scan` 报告 |

## 生产环境

- **勿将真实涉政/色情词表提交 Git**；在部署目录覆盖 `default/*.txt` 或通过环境变量 `SENSITIVE_LEXICON_ROOT` 指向外部词库。
- 本仓库仅含 **测试占位词**，用于跑通流水线。

## 离线大批量

`writer-python/scripts/batch_sensitive_audit.py`：Pandas `chunksize` 分批读 JSONL/CSV，控制内存，输出命中统计。
