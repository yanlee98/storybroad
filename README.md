# 影视剧本解析提示词使用说明

本文档说明三个提示词文档的用法，它们构成了从完整剧本到分镜的完整解析流程。

## 流程概览

```
完整剧本 → script_analysis.md → 剧集列表 + 资产列表
                ↓
        单个剧集 → series_analysis.md → 场景序列
                ↓
        单个场景 → scene_analysis.md → 分镜列表
```

## 1. script_analysis.md - 剧本解析提示词

### 用途

对完整剧本进行结构化解析，提取剧本基础信息、角色、场景、道具、生物，并将剧本拆分为多个剧集。

### 输入

- **完整剧本原文**（文本格式）

### 输出格式

```json
{
  "script_info": {
    "script_description": "剧本主题描述（30字以内）"
  },
  "characters": [
    {
      "character_id": "C1",
      "character_name": "角色名",
      "character_background": "角色背景（30字）",
      "character_appearance_prompt": "长相文生图提示词（60字以内）",
      "character_style_prompt": "造型文生图提示词（60字以内）"
    }
  ],
  "backdrops": [
    {
      "backdrop_id": "B1",
      "backdrop_name": "场景名",
      "backdrop_prompt": "文生图提示词（100字以内）"
    }
  ],
  "creatures": [
    {
      "creature_id": "CR1",
      "creature_name": "生物名",
      "creature_prompt": "文生图提示词（60字以内）"
    }
  ],
  "props": [
    {
      "prop_id": "P1",
      "prop_name": "道具名",
      "prop_prompt": "文生图提示词（60字以内）"
    }
  ],
  "episodes": [
    {
      "episode_id": "EP1",
      "episode_name": "第1集",
      "episode_description": "剧集简要描述（30字以内）",
      "episode_characters": ["C1", "C2"],
      "episode_backdrops": ["B1"],
      "episode_creatures": ["CR1"],
      "episode_props": ["P1"],
      "episode_content": "剧集剧本原文（完整内容）"
    }
  ]
}
```

### 输出说明

- **script_info**: 剧本基础信息（主题描述）
- **characters**: 所有角色列表（包含ID、名称、背景、外观提示词、造型提示词）
- **backdrops**: 所有场景列表（包含ID、名称、场景提示词）
- **creatures**: 所有生物列表（包含ID、名称、生物提示词）
- **props**: 所有道具列表（包含ID、名称、道具提示词）
- **episodes**: 剧集列表（每个剧集包含ID、名称、描述、关联的角色/场景/生物/道具、剧集原文）

---

## 2. series_analysis.md - 剧集场景序列解析提示词

### 用途

将单个剧集的剧本内容按照场景进行划分，生成场景序列，并解析每个场景关联的角色、生物、道具。

### 输入

**完整数据**（从 script_analysis.md 的输出中获取）：

- **backdrops**: 完整场景列表（script_analysis.md 输出的 backdrops）
- **characters**: 完整角色列表（script_analysis.md 输出的 characters）
- **creatures**: 完整生物列表（script_analysis.md 输出的 creatures）
- **props**: 完整道具列表（script_analysis.md 输出的 props）

**当前剧集数据**（从 script_analysis.md 输出的 episodes[] 中选择一个剧集，包含该剧集的所有字段）：

- **episode_id**: 剧集ID（episodes[].episode_id，如："EP1"）
- **episode_name**: 剧集名称（episodes[].episode_name，如："1.起初"）
- **episode_description**: 剧集描述（episodes[].episode_description）
- **episode_characters**: 当前剧集关联的角色ID数组（episodes[].episode_characters）
- **episode_backdrops**: 当前剧集关联的场景ID数组（episodes[].episode_backdrops）
- **episode_creatures**: 当前剧集关联的生物ID数组（episodes[].episode_creatures）
- **episode_props**: 当前剧集关联的道具ID数组（episodes[].episode_props）
- **episode_content**: 当前剧集的剧本原文（episodes[].episode_content）

### 输出格式

```json
{
  "episode_id": "EP1",
  "episode_backdrops_sequence": [
    {
      "episode_backdrop": ["B1"],
      "backdrop_related_characters": ["C1", "C2"],
      "backdrop_related_creatures": ["CR1"],
      "backdrop_related_props": ["P1", "P2"],
      "episode_backdrop_description": "场景简要描述（100字以内）",
      "backdrop_script_content": "场景对应的剧本原文内容（完整内容）"
    }
  ]
}
```

### 输出说明

- **episode_id**: 剧集ID
- **episode_backdrops_sequence**: 场景序列数组
  - **episode_backdrop**: 场景ID数组（数组里只能有一个id）
  - **backdrop_related_characters**: 本场景出现的角色ID数组
  - **backdrop_related_creatures**: 本场景出现的生物ID数组
  - **backdrop_related_props**: 本场景使用的道具ID数组
  - **episode_backdrop_description**: 场景简要描述（100字以内）
  - **backdrop_script_content**: 场景对应的完整剧本原文

---

## 3. scene_analysis.md - 场景分镜解析提示词

### 用途

对单个场景的剧本内容进行解析，生成该场景下的多个分镜，以及每个分镜的静态提示词（用于生成分镜图）和动态提示词（用于分镜图生视频）。

### 输入

**完整资产列表**（从 script_analysis.md 的输出中获取）：

- **characters**: 完整角色列表（script_analysis.md 输出的 characters）
- **backdrops**: 完整场景列表（script_analysis.md 输出的 backdrops）
- **props**: 完整道具列表（script_analysis.md 输出的 props）
- **creatures**: 完整生物列表（script_analysis.md 输出的 creatures）

**当前场景数据**（从 series_analysis.md 输出中获取，并从 episode_backdrops_sequence[] 中选择一个场景，包含该场景的所有字段）：

- **episode_id**: "EP1",
- **episode_backdrops_sequence**: [
- **episode_backdrop**: 场景ID数
- **episode_backdrop**: 场景ID数组（episode_backdrops_sequence[].episode_backdrop）
- **backdrop_related_characters**: 场景关联的角色ID数组（episode_backdrops_sequence[].backdrop_related_characters）
- **backdrop_related_creatures**: 场景关联的生物ID数组（episode_backdrops_sequence[].backdrop_related_creatures）
- **backdrop_related_props**: 场景关联的道具ID数组（episode_backdrops_sequence[].backdrop_related_props）
- **episode_backdrop_description**: 场景描述（episode_backdrops_sequence[].episode_backdrop_description）
- **backdrop_script_content**: 场景剧本原文（episode_backdrops_sequence[].backdrop_script_content）
  ]

### 输出格式

```json
{
  "scene_backdrop": ["B1"],
  "shotlist": [
    {
      "shot_id": "SH1",
      "shot_name": "分镜1",
      "shot_related_characters": ["C1"],
      "shot_related_creatures": ["CR3"],
      "shot_related_props": ["P5"],
      "shot_static_prompt": "静态提示词（1000字以内，用于生成分镜图）",
      "shot_dynamic_prompt": "动态提示词（1000字以内，用于分镜图生视频，人物对话内容必须完整保留）"
    }
  ]
}
```

### 输出说明

- **scene_backdrop**: 场景ID数组
- **shotlist**: 分镜列表
  - **shot_id**: 镜头ID（字符串格式，如："SH1", "SH2"）
  - **shot_name**: 分镜名称（如："分镜1", "分镜2"）
  - **shot_related_characters**: 本镜头出现的角色ID数组
  - **shot_related_creatures**: 本镜头出现的生物ID数组
  - **shot_related_props**: 本镜头使用的道具ID数组
  - **shot_static_prompt**: 静态提示词
  - **shot_dynamic_prompt**: 动态提示词

---

## 使用流程示例

### 第一步：解析完整剧本

```
输入：完整剧本.txt
提示词：script_analysis.md
输出：{
  script_info: {...},
  characters: [...],
  backdrops: [...],
  creatures: [...],
  props: [...],
  episodes: [
    {
      episode_id: "EP1",
      episode_content: "第1集的完整剧本原文",
      ...
    }
  ]
}
```

### 第二步：解析单个剧集的场景序列

```
输入：
完整数据：
  - backdrops: [...]
  - characters: [...]
  - creatures: [...]
  - props: [...]
当前剧集数据（从第一步输出的episodes[]中选择一个）：
  - episode_id: "EP1"
  - episode_name: "1.起初"
  - episode_description: "剧集描述"
  - episode_content: "第1集的完整剧本原文"
  - episode_backdrops: ["B1", "B2"]
  - episode_characters: ["C1", "C2", ...]
  - episode_creatures: [...]
  - episode_props: ["P1", "P2", ...]
提示词：series_analysis.md
输出：{
  episode_id: "EP1",
  episode_backdrops_sequence: [
    {
      episode_backdrop: ["B1"],
      backdrop_script_content: "场景1的完整剧本原文",
      ...
    }
  ]
}
```

### 第三步：解析单个场景的分镜

```
输入：
完整资产列表：
  - characters: [...]
  - backdrops: [...]
  - props: [...]
  - creatures: [...]
当前场景数据（从第二步输出的episode_backdrops_sequence[]中选择一个）：
  - episode_backdrop: ["B1"]
  - backdrop_related_characters: ["C1", "C2"]
  - backdrop_related_creatures: [...]
  - backdrop_related_props: ["P1", "P2"]
  - episode_backdrop_description: "场景描述"
  - backdrop_script_content: "场景1的完整剧本原文"
提示词：scene_analysis.md
输出：{
  scene_backdrop: ["B1"],
  shotlist: [
    {
      shot_id: "SH1",
      shot_static_prompt: "静态提示词",
      shot_dynamic_prompt: "动态提示词（包含完整对话）",
      ...
    }
  ]
}
```

---

## 注意事项

1. **数据流转**：三个提示词是顺序执行的，前一个的输出作为后一个的输入
2. **ID一致性**：所有ID（character_id、backdrop_id、prop_id、creature_id、episode_id、shot_id）必须保持前后一致
3. **输出格式**：所有输出必须严格遵循JSON格式，无任何解释文字
4. **Token限制**：所有提示词的最大输出Token限制为32,000 tokens（模型最大输出长度限制）
