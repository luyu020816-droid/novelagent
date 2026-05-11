---
name: incident-log
description: >-
  After repeated failures or a long debug loop on this repo, append a concise
  incident entry to 问题.md (root). Use when the user asks to summarize bugs,
  fixes, lessons learned, or interview notes from troubleshooting MythosForge.
---

# Incident log → 问题.md

## When to use

- Same issue failed multiple times, or debugging spanned several attempts.
- User asks to「总结经验」「记下来」「面试能讲」「写到问题.md」.
- A fix landed and should be **recorded as institutional memory**, not only in chat.

## Rules

1. **Edit only** [`问题.md`](../../../问题.md) at the **repository root**. Do **not** duplicate long chat transcripts.
2. **One root cause per section.** If an existing section covers it, **merge** (add a short subsection or bullet) instead of creating duplicate headings.
3. Use the **template at the bottom of 问题.md**: 现象 → 原因 → 如何验证 → 解决方案 → 面试怎么说.
4. Keep **technical accuracy**: stack layers (HTTP vs FastAPI vs DB vs JVM), exact status codes, and file paths that exist in the repo.
5. **Interview bullet**: one or two sentences the candidate can say aloud (因果 + 你做了什么验证).
6. Update the **快速索引** table in `问题.md` when adding a major new topic.
7. Do **not** remove historical entries unless the user explicitly wants a rewrite; prefer additive updates.

## Workflow

1. Identify the **single primary root cause** (not every intermediate mistake).
2. Check **快速索引** and existing sections for duplicates.
3. Append a new `### N. …` block or extend the closest section.
4. If a **script or config** fixed the issue, link it (e.g. `scripts/smoke-genre-recommend.ps1`, `application.yml` keys).
5. Optionally mention **follow-ups** (e.g. timeouts, monitoring) as one line — avoid scope creep.

## Anti-patterns

- Pasting entire stack traces into `问题.md` without summarizing the **actionable** line.
- Blaming「环境问题」without saying **how you verified** which environment.
- Recording secrets (API keys, passwords); use placeholders only.
