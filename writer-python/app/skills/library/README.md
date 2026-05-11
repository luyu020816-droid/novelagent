# Writer Skill Library

本目录由 **`app/skills/loader.py`** 扫描加载；修改后需 **重启 Writer（uvicorn）** 生效。

前端/Java 通过 **`GET /api/writer/skills`** 列出 `{ id, label }`；作品侧保存的是 Skill **`id`**，须与这里加载结果一致。

---

## 快速对照

| 能力 | 说明 |
|------|------|
| **丛书预设（Skill）** | 注入 `chapter_digest` 与可选 `init_*` 字段，参与初始化与世界观合并 |
| **`fatigue/`** | **不走 Skill 加载器**：供 **`fatigue_scanner`** 单独读取的配置（禁用词等），不会出现在 `/api/writer/skills` |

---

## 三种放置方式（Skill）

### 1）`library` 根目录单个 YAML

示例：`library/my_series.yaml`

- 未写 `id` 时，使用 **文件名（不含扩展名）** 作为 `id`。
- 须满足 **`chapter_digest` 必填**（见下方字段表）。

### 2）文件夹 + 入口文件（任意嵌套）

在每个「Skill 包」目录下放 **其一**：

- `skill.yaml` / `skill.yml`
- `index.yaml` / `index.yml`

示例：`library/hp_fan/skill.yaml`

- **仅识别上述文件名**，不会把 `agents/openai.yaml` 等误当成主 Skill。
- 未写 `id` 时，使用 **文件夹名** 作为默认 `id`。文件夹名须匹配：`^[a-z0-9][a-z0-9_-]{0,62}$`（小写、数字、`_`、`-`，最长 63）。

### 3）文件夹 + `SKILL.md`（Cursor 风格）

示例：`library/harrypotter/hp-fanfic-romance/SKILL.md`

- 支持 YAML **frontmatter**（`---` … `---`）。
- **`id`**：优先 frontmatter 的 `name` 或 `id`；否则用 **文件夹名**（同样须合法 slug）。
- **`label`**：优先 `display_name` / `title`，否则由 `name` 推导展示名。
- **`chapter_digest`**：由 `description` + 正文 Markdown 拼接后截断注入流水线；**无结构化 `init_*`**（若要与初始化契约深度合并，请改用 YAML Skill）。

**优先级**：同一目录下若 **同时存在** YAML 入口与 `SKILL.md`，**以 YAML 为准**。

---

## YAML 主文件字段

| 字段 | 必填 | 说明 |
|------|:----:|------|
| `id` | 否 | 省略则用文件名或文件夹名（规则见上） |
| `label` | 否 | 前端展示名 |
| `chapter_digest` | **是** | 每章短文约束（注入流水线） |
| `init_world_rules` | 否 | 初始化合并进契约 |
| `init_ability_rules` | 否 | 同上 |
| `init_forbidden_moves` | 否 | 同上 |
| `init_must_retain_facts` | 否 | 同上 |
| `init_style_voice_suffix` | 否 | 文风后缀提示 |
| `init_taboo_topics` | 否 | 禁忌话题 |

---

## 本仓库内示例

| 路径 | 类型 |
|------|------|
| `hp_fan/skill.yaml` | 文件夹 + YAML |
| `harrypotter/hp-fanfic-romance/SKILL.md` | Cursor 风格 Markdown |
| `fatigue/default.yaml` | **非 Skill**：疲劳/套话配置，由扫描器独立读取 |

---

## 调试提示

- 加载失败会在 Writer 日志里出现 **`[skills] skip ...`**，多为缺少 `chapter_digest`、`id` 非法或文件夹名不符合 slug。
- 重复 `id` 时后加载的条目会被跳过。
