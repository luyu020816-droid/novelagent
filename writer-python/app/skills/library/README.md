# Writer Skill Library（丛书预设与疲劳配置）

本目录是 MythosForge **Writer** 侧的本地资源根：`app/skills/loader.py` 会扫描此处，组装 **丛书 Skill（Series Preset）**；另有 **`fatigue/`** 由疲劳扫描器单独读取，**不参与** Skill 列表。

修改本目录内容后需 **重启 Writer（uvicorn）** 才会重新加载。

---

## 在本项目里它们做什么

| 能力 | 代码入口 | 作用 |
|------|-----------|------|
| **丛书 Skill** | `loader.load_all_skills()` | 提供 `chapter_digest` 与可选 `init_*` 字段 |
| **列表 API** | `GET /api/writer/skills`（经 Java 可代理到前端） | 返回 `{ libraryDir, skills: [{ id, label }] }` |
| **初始化合并** | `preset_merge.merge_story_contract_with_preset()` | 若请求体带 `fan_series_preset`，把 Skill 的 `init_*` **并入** `StoryContract`（世界规则、禁忌、文风后缀等） |
| **章节流水线** | `token_budget_service.build_pack_items()` | 若状态里带 `fan_series_preset`，把 Skill 的 **`chapter_digest`** 作为 **`fan_series_digest`** 装入上下文包（受 Token 预算裁剪） |
| **Planner / Ghostwriter / Critic** | 各节点 prompt | 要求遵守 `fan_series_digest`、丛书规则维度等 |
| **疲劳配置** | `fatigue_scanner.load_fatigue_profile()` | 读取 `fatigue/<profile>.yaml`，**不**出现在 `/api/writer/skills` |

业务上：作品或初始化请求里保存的是 Skill 的 **`id`**（如 `hp-fanfic-romance`），须与加载得到的 `id` 完全一致（小写规范化后匹配）。

---

## 概念区分：Skill vs `fatigue/`

- **Skill（本 README 主体）**：必须能解析出带 **`chapter_digest`** 的 `SeriesPreset`，才会进入 `load_all_skills()`，并出现在 API 列表里。
- **`library/fatigue/*.yaml`**：结构为 `banned_substrings` / `caution_substrings` 等，**没有** `chapter_digest` 要求；供 **`fatigue_scanner`** 做套话/禁用短语扫描，**不要**指望它被当成丛书 Skill 加载。

---

## Skill 的三种放置方式

扫描逻辑见 **`app/skills/loader.py`**。

### 1）`library` 根目录下的单个 YAML

- 路径示例：`library/my_series.yaml`
- **id**：文件里可写 `id`；未写则用 **文件名（不含扩展名）**，再转小写、空格变 `_`。
- **须合法**：最终 `id` 必须匹配正则  
  `^[a-z0-9][a-z0-9_-]{0,62}$`

### 2）文件夹 + `skill.yaml` / `index.yaml`（任意嵌套深度）

- 在每个 skill **包目录** 下放 **`skill.yaml`** / **`skill.yml`** / **`index.yaml`** / **`index.yml`** 之一。
- **不会**把子目录里任意命名的 YAML（例如 `agents/openai.yaml`）当成主 Skill，避免误加载。
- **文件夹名**：若 YAML 内未写 `id`，则用 **文件夹名** 作为默认 id；文件夹名也必须满足上面的 **slug** 正则（小写、`a-z0-9`、`_`、`-`）。
- 路径示例：`library/acme/space-opera/skill.yaml`

### 3）文件夹 + `SKILL.md`（Cursor 风格）

- 路径示例：`library/harrypotter/hp-fanfic-romance/SKILL.md`
- 支持 YAML **frontmatter**（`---` … `---`）。
- **id**：优先 frontmatter 的 **`name`** 或 **`id`**；否则用 **文件夹名**（须合法 slug）。
- **label**：优先 **`display_name`** / **`title`**，否则由 `name` 等生成展示名。
- **chapter_digest**：由 **`description` + 正文 Markdown** 拼接后截断得到；**不提供** `init_world_rules` 等结构化字段（若要与初始化契约深度合并，请改用 YAML Skill）。
- **共存**：同一目录下若既有 YAML 入口又有 **`SKILL.md`**，**优先 YAML**。

### 加载顺序与去重

1. 先处理 **`library/*.yaml` / `*.yml`**（仅根目录一层）。
2. 再递归查找「含合法入口文件」的目录；每个包至多贡献一个 Skill。
3. **重复 `id`**：后遇到的条目会被 **跳过** 并打日志 `[skills] duplicate id ...`。

---

## YAML Skill 字段说明（`SeriesPreset`）

以下字段在加载时映射为内部结构；**`chapter_digest` 在 YAML 主文件中必填**（`SKILL.md` 路径则由正文推导，见上）。

| 字段 | 必填 | 说明 |
|------|:----:|------|
| `id` | 否 | 省略则用文件名或文件夹名（规则见上） |
| `label` | 否 | 列表 API 与 UI 展示名 |
| `chapter_digest` | **是**（YAML） | 丛书级「每章短约束」，进入上下文包中的 **`fan_series_digest`** |
| `init_world_rules` | 否 | 字符串列表；初始化合并进 `StoryContract.world_rules` |
| `init_ability_rules` | 否 | 合并进 `ability_rules` |
| `init_forbidden_moves` | 否 | 合并进 `forbidden_moves` |
| `init_must_retain_facts` | 否 | 合并进 `must_retain_facts` |
| `init_style_voice_suffix` | 否 | 追加到 `style_guide.narrative_voice`（带 `[丛书口吻]` 前缀） |
| `init_taboo_topics` | 否 | 合并进 `style_guide.taboo_topics` |

合并实现：**`app/skills/preset_merge.py`**（列表项去重保序）。

---

## 最小 YAML 示例（可复制新建）

```yaml
id: my_series
label: 我的丛书预设
chapter_digest: |
  每章必须保持：①叙事视角一致 ②不引入未铺垫的超纲设定。
init_world_rules:
  - 默认发生在近未来地球轨道站。
init_forbidden_moves:
  - 禁止无脑机械降神解决核心矛盾。
init_style_voice_suffix: 克制书面语，减少说教旁白。
```

保存为 **`library/my_series.yaml`** 或 **`library/my_series/skill.yaml`**（文件夹名需为 `my_series` 等与 id 一致或可再在 YAML 里写 `id`）。

---

## 本仓库当前示例（随仓库变动）

| 路径 | 类型 | 说明 |
|------|------|------|
| `harrypotter/hp-fanfic-romance/SKILL.md` | SKILL.md | `id`: `hp-fanfic-romance`（frontmatter `name`）；长文 digest |
| `harrypotter/hp-fanfic-romance/references/*.md` | 参考资料 | **不参与** loader；仅供人或 Copilot 阅读 |
| `harrypotter/hp-fanfic-romance/agents/openai.yaml` | 辅助配置 | **不参与** Skill 入口 |
| `fatigue/default.yaml` | 疲劳配置 | **`default`** profile；可选 `fatigue/<id>.yaml` 扩展 |

---

## 疲劳配置（`fatigue/`）

- 文件：`library/fatigue/<profile_id>.yaml`，缺省时回退 **`default.yaml`**。
- 典型字段：`banned_substrings`、`caution_substrings`（见现有 `default.yaml`）。
- 与 **Skill id** 无关；不由 `fan_series_preset` 切换。当前 **`critic_node`** 调用扫描时使用固定 profile **`default`**（即 `fatigue/default.yaml`）；若要按作品切换 profile，需在代码里扩展。

---

## 调试与排错

- Writer 日志中出现 **`[skills] skip ...`**：常见原因包括——缺 **`chapter_digest`**、YAML 根不是 mapping、`id` 或文件夹名 **不符合 slug**、与已有 **id 重复**。
- 初始化报错 **`未知 Skill id`**：`preset_merge` 在校验 `fan_series_preset`；请确认该 id 已在 **`GET /api/writer/skills`** 结果中。
- 修改 Skill 后列表未变：**必须重启** Writer 进程。

---

## 相关代码索引

| 模块 | 路径 |
|------|------|
| 扫描加载 | `app/skills/loader.py` |
| 初始化合并 | `app/skills/preset_merge.py` |
| 兼容 re-export | `app/skills/series_presets.py` |
| HTTP 列表 | `app/api/writer_skills.py` |
| 上下文装配 | `app/services/token_budget_service.py`（`fan_series_digest`） |
| 疲劳扫描 | `app/services/fatigue_scanner.py` |
