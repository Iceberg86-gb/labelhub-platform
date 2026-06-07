# Schema Designer 字段组件能力核实 RESEARCH

日期: 2026-06-07

范围: 仅核实仓库现状, 不设计实现方案, 不估 LOC。结论以 OpenAPI 契约、schema designer 前端代码、Formily 运行时组件、AI field assist 接口/服务端代码为证据。

## 结论摘要

- OpenAPI `SchemaFieldType` 与前端 `SCHEMA_FIELD_TYPES` 完全一致, 均为 12 个 type: `text`, `number`, `single_select`, `multi_select`, `date`, `file_upload`, `rich_text`, `json_editor`, `llm_interaction`, `show_item`, `nested_object`, `tab_container`。证据: `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/entities/schema/schemaTypes.ts:16-29`。
- Designer 根层添加入口可添加上述 12 个 type; 字段编辑器 switch 也覆盖全部 12 个 type。证据: `apps/web/src/features/schema-design/FieldTypePicker.tsx:10-21`, `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:119-130`, `apps/web/src/features/schema-design/designerDragModel.ts:104-107`, `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx:30-59`。
- 契约有但前端无: 未发现。前端有但契约无: 未发现。
- 放置限制存在, 但不是 type 差异: `nested_object` 子字段不能再放 `nested_object` 或 `tab_container`; `tab_container` 的 tab 内不能再放 `tab_container`。证据: `apps/web/src/features/schema-design/designerDragModel.ts:108-111`, `apps/web/src/features/schema-design/field-editors/NestedObjectFieldEditor.tsx:39-43`, `apps/web/src/features/schema-design/field-editors/TabContainerFieldEditor.tsx:51-110`。
- `preference_compare` 常见标注组件中, 展示项、单选、多选/标签选择、单行输入、JSON 编辑器、文件上传、字段级 LLM 辅助均有原生 type; 多行文本、富文本编辑器、图片上传属于部分支持或语义受限, 见能力矩阵。
- 字段联动 DSL 已在 designer 中配置 `visibleWhen`/`requiredWhen`, 支持可视化条件与 JSON 高级编辑; 运行时 Formily 会根据 DSL 控制显隐和必填。证据: `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:13-37`, `apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx:78-100`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100`。
- 字段级 AI 辅助通过 `llm_interaction` type 和 `aiPrompt` 配置进入 schema; 运行时调用 `createFieldAssistCall` 对应的 `/ai-review/field-assist`。证据: `apps/web/src/features/schema-design/field-editors/LlmInteractionFieldEditor.tsx:12-26`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:34-43`, `packages/contracts/openapi/labelhub.yaml:1540-1556`。

## 字段类型全集

### OpenAPI 契约枚举

| 来源 | type 值 | 证据 |
| --- | --- | --- |
| `SchemaFieldType` enum | `text` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `number` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `single_select` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `multi_select` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `date` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `file_upload` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `rich_text` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `json_editor` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `llm_interaction` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `show_item` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `nested_object` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |
| `SchemaFieldType` enum | `tab_container` | `packages/contracts/openapi/labelhub.yaml:2882-2884` |

`SchemaField` 契约还包含与组件能力相关的属性: `content`, `sourcePath`, `aiPrompt`, `acceptedFileTypes`, `maxFileSizeMb`, `validation`, `options`, `children`, `tabs`, `visibleWhen`, `requiredWhen`。证据: `packages/contracts/openapi/labelhub.yaml:2955-3000`。

### 前端 designer 可添加类型

| 前端证据点 | 结论 | 证据 |
| --- | --- | --- |
| 前端类型常量 | `SCHEMA_FIELD_TYPES` 包含 12 个 type, 与 OpenAPI enum 一致 | `apps/web/src/entities/schema/schemaTypes.ts:16-29` |
| 添加按钮 | `AddFieldButton` 使用 `FieldTypePicker` 暴露添加入口 | `apps/web/src/features/schema-design/AddFieldButton.tsx:12-18` |
| 类型选择器 | `FieldTypePicker` 从 `SCHEMA_FIELD_TYPES` 过滤并渲染按钮 | `apps/web/src/features/schema-design/FieldTypePicker.tsx:10-21` |
| 根层放置 | `canPlaceFieldTypeInDesignerTarget` 对 root 返回 `true` | `apps/web/src/features/schema-design/designerDragModel.ts:104-107` |
| 创建字段 | `handleAddField` 调 `createField(type)` 后插入目标位置 | `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:119-130` |
| 字段工厂 | `createField(type)` switch 覆盖 12 个 type 并写入对应默认属性 | `apps/web/src/entities/schema/fieldFactory.ts:10-47` |
| 字段编辑器 | `FieldEditor` switch 覆盖 12 个 type | `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx:30-59` |
| palette 分组 | palette 将 12 个 type 分入只读、选择约束、内容输入、容器/高级 | `apps/web/src/features/schema-design/DesignerFieldBuilder.tsx:50-82`, `apps/web/src/features/schema-design/DesignerFieldBuilder.tsx:190-239` |

### 契约与前端差异对照

| type | OpenAPI enum | 前端常量 | 根层可添加 | 字段编辑器 | 差异 |
| --- | --- | --- | --- | --- | --- |
| `text` | 有 | 有 | 有 | 有 | 无 |
| `number` | 有 | 有 | 有 | 有 | 无 |
| `single_select` | 有 | 有 | 有 | 有 | 无 |
| `multi_select` | 有 | 有 | 有 | 有 | 无 |
| `date` | 有 | 有 | 有 | 有 | 无 |
| `file_upload` | 有 | 有 | 有 | 有 | 无 |
| `rich_text` | 有 | 有 | 有 | 有 | 无 |
| `json_editor` | 有 | 有 | 有 | 有 | 无 |
| `llm_interaction` | 有 | 有 | 有 | 有 | 无 |
| `show_item` | 有 | 有 | 有 | 有 | 无 |
| `nested_object` | 有 | 有 | 有 | 有 | 无; 但 nested 子级不能再放 `nested_object`/`tab_container` |
| `tab_container` | 有 | 有 | 有 | 有 | 无; 但 tab 内不能再放 `tab_container` |

## 能力矩阵

| 标注要求组件 | 状态 | 对应 type | 证据 | 限制/结论 |
| --- | --- | --- | --- | --- |
| 展示项(只读渲染) | 原生支持 | `show_item` | `apps/web/src/features/schema-design/field-editors/ShowItemFieldEditor.tsx:12-20`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:48-55`, `apps/web/src/features/labeling/formily/components/LabelHubShowItem.tsx:1-12` | Designer 可配置 `content` 与 `sourcePath`; 运行时按 void/display-only 渲染, 不进入必填输入。`sourcePath` 从 dataset item payload 取值, 证据: `apps/web/src/features/labeling/formily/showItemSource.ts:3-12`。 |
| 单选 | 原生支持 | `single_select` | `apps/web/src/features/schema-design/field-editors/SelectFieldEditor.tsx:30-76`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:19-39` | Designer 可配置选项; 运行时使用 Semi `Select`, 非 multiple。 |
| 多选/标签选择 | 原生支持 | `multi_select` | `apps/web/src/features/schema-design/field-editors/SelectFieldEditor.tsx:30-76`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:27-45`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:19-39`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:63-70` | `multi_select` 通过 `mode: multiple` 和 `Select multiple` 实现; 只读态按 Tag 展示。 |
| 单行输入 | 原生支持 | `text` | `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:12-59`, `apps/web/src/features/labeling/formily/components/LabelHubTextField.tsx:1-29` | 运行时组件为 Semi `Input`, 即单行输入。 |
| 多行文本 | 部分支持 | 无独立 plain textarea type; 可用 `rich_text` 或特定语义的 `json_editor`/`llm_interaction` | `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/entities/schema/schemaTypes.ts:16-29`, `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23`, `apps/web/src/features/labeling/formily/components/LabelHubJsonField.tsx:14-31`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:51-57` | 仓库未发现 `textarea`/`long_text` 类型。`rich_text` 可输入多行 HTML 内容; `json_editor` 与 `llm_interaction` 使用 TextArea 但语义分别是 JSON 和 AI 交互输入。 |
| 富文本编辑器 | 部分支持 | `rich_text` | `apps/web/src/features/schema-design/field-editors/RichTextFieldEditor.tsx:9-24`, `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:1-25` | 有 `rich_text` type, designer 可配置默认内容 HTML, 运行时为 `contentEditable` HTML 区域; 仓库未发现加粗/列表/链接等工具栏按钮证据, 因此按轻量富文本而非完整富文本编辑器判定。 |
| JSON 编辑器 | 原生支持 | `json_editor` | `apps/web/src/features/schema-design/field-editors/JsonFieldEditor.tsx:12-22`, `apps/web/src/features/labeling/formily/components/LabelHubJsonField.tsx:1-34` | 运行时 TextArea 输入并解析 JSON, invalid JSON 时给出 warning。未发现 Monaco/树形 JSON 编辑器证据, 但组件语义和 type 原生存在。 |
| 图片上传 | 部分支持 | `file_upload` | `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:29-40`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:24-39`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:54-63` | 无独立 `image_upload` type; 可通过 `file_upload.acceptedFileTypes` 配置 image MIME/扩展名。未发现图片预览、裁剪、尺寸校验等图片专用能力。 |
| 文件上传 | 原生支持 | `file_upload` | `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:29-40`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:24-39`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:54-68` | Designer 可配置 accepted types 和 max size; 运行时隐藏 file input 并上传到 `/sessions/{sessionId}/attachments`。 |
| LLM 交互(字段级 AI 辅助) | 原生支持 | `llm_interaction` | `apps/web/src/features/schema-design/field-editors/LlmInteractionFieldEditor.tsx:12-26`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:34-43`, `packages/contracts/openapi/labelhub.yaml:1540-1556`, `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:73-79` | Designer 通过 `aiPrompt` 配置提示词; 运行时把 `sessionId`, `fieldPath`, `prompt`, `text` 发到 field assist。预览态若无 `sessionId` 会禁用, 证据: `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:58-60`。 |

## 字段联动能力

Designer 支持 `visibleWhen` 与 `requiredWhen` 两类联动条件。OpenAPI 定义了 linkage 条件运算符 `eq`, `neq`, `in`, `notIn`, `gt`, `gte`, `lt`, `lte`, `empty`, `notEmpty`, 以及 atomic/group 条件结构。证据: `packages/contracts/openapi/labelhub.yaml:2925-2954`。

前端 designer 在每个字段编辑器下追加联动 UI 与 JSON 高级编辑:

| 能力 | 证据 | 结论 |
| --- | --- | --- |
| 每个字段编辑器下挂联动配置 | `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx:16-27` | `LinkageConditionBuilder` 与 `LinkageJsonEditor` 随字段编辑器一起渲染。 |
| `visibleWhen`/`requiredWhen` 目标 | `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:13-37` | UI 明确支持显示条件和必填条件。 |
| atomic 条件 | `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:232-282`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:361-379` | 可配置字段、运算符和值; `in`/`notIn` 以逗号拆数组, number 字段转 Number。 |
| group 条件 | `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:285-357`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:382-388` | 支持 `allOf` 与 `anyOf` 条件组。 |
| 候选字段收集 | `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:63-81`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:391-408` | 候选字段来自当前 schema 字段树, 会展开 nested object 和 tab fields, 并排除当前字段。 |
| JSON 高级编辑 | `apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx:78-100`, `apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx:112-130` | 可直接编辑 `visibleWhen`/`requiredWhen` JSON; 该 UI 只检查 JSON 语法, 字段引用/运算符/值类型发布时检查。 |
| 发布校验 | `apps/web/src/entities/schema/schemaValidation.ts:149-202`, `apps/web/src/entities/schema/schemaValidation.ts:204-248`, `apps/web/src/entities/schema/schemaValidation.ts:263-317` | 校验 linkage shape、缺失引用、自引用、值类型、number 运算符和循环依赖。 |
| 运行时显隐/必填 | `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100`, `apps/web/src/entities/labeling/linkageEvaluator.ts:29-45`, `apps/web/src/entities/labeling/linkageEvaluator.ts:47-85` | Formily reaction 根据 DSL 设置 field visible/display/required; evaluator 支持 atomic/group 运算。 |

对 `safety_flag` 这类条件显隐的核实结论: 如果 `safety_flag` 是 schema 中的一个字段 stableId, designer 可把其他字段的 `visibleWhen` 或 `requiredWhen` 指向它, 例如通过 atomic 条件选择字段 `safety_flag`、运算符 `eq`、值 `unsafe`; 如果要组合多个条件, 可用 `allOf`/`anyOf`。这是 schema 字段值之间的联动; 仓库证据显示候选字段来自 schema 字段树, 未发现直接用 dataset item payload 字段作为联动源的 designer 配置入口。证据: `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:391-408`, `apps/web/src/entities/labeling/linkageEvaluator.ts:168-178`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:285-357`。

## LLM 交互组件配置链路

| 环节 | 证据 | 结论 |
| --- | --- | --- |
| 契约 type 与属性 | `packages/contracts/openapi/labelhub.yaml:2882-2884`, `packages/contracts/openapi/labelhub.yaml:2955-3000` | `llm_interaction` 是 `SchemaFieldType`; `SchemaField` 包含 `aiPrompt`。 |
| 字段默认值 | `apps/web/src/entities/schema/fieldFactory.ts:10-47` | `llm_interaction` 字段创建时落入 text/rich/llm 共享默认分支, 带 placeholder/help。 |
| Designer 配置 | `apps/web/src/features/schema-design/field-editors/LlmInteractionFieldEditor.tsx:12-26` | 编辑器配置字段标签、占位提示、`aiPrompt` 和必填。 |
| Formily 注册 | `apps/web/src/features/labeling/formily/adapters/componentRegistry.ts:3-16` | `llm_interaction` 映射到 `LabelHubLlmInteractionField`。 |
| Formily props | `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:27-45`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:102-122` | 运行时把 `field`, `sessionId`, `itemPayload` 传给组件; `llm_interaction` 表单值类型为 object。 |
| 前端调用 | `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:34-43`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:51-60` | 用户输入 text 后点击生成; 请求体包含 `sessionId`, `fieldPath: field.stableId`, `input.prompt: field.aiPrompt`, `input.text`; 返回值保存 `{ output, aiCallId }`。 |
| OpenAPI 操作 | `packages/contracts/openapi/labelhub.yaml:1540-1556`, `packages/contracts/openapi/labelhub.yaml:3696-3710` | `/ai-review/field-assist` 的 operationId 为 `createFieldAssistCall`; request 需要 `sessionId`, `fieldPath`, `input`。 |
| 服务端入口 | `services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:73-79`, `services/api/src/main/java/com/labelhub/api/module/ai/application/FieldAssistService.java:82-131` | Controller 调 `fieldAssistService.assist(...)`; Service 校验 session/schema/dataset/task, 调 AI provider, 持久化 ai_call 并返回响应。 |
| Prompt 边界 | `services/api/src/main/java/com/labelhub/api/module/ai/application/FieldAssistService.java:168-177`, `services/api/src/main/java/com/labelhub/api/module/ai/application/FieldAssistService.java:180-193` | 服务端提示词要求字段级辅助、只提供证据不做最终裁决; 响应包含 trace/provenance。 |

## `preference_compare` 组件覆盖初判

仅按组件能力映射, 不展开验证方案:

| 可能需求 | 可用 type | 结论 | 证据 |
| --- | --- | --- | --- |
| 展示 prompt/候选回答/元数据 | `show_item` | 原生支持; 可通过 `sourcePath` 读取 item payload | `apps/web/src/features/schema-design/field-editors/ShowItemFieldEditor.tsx:16-20`, `apps/web/src/features/labeling/formily/showItemSource.ts:3-12` |
| 偏好选择 A/B/平局 | `single_select` | 原生支持 | `apps/web/src/features/schema-design/field-editors/SelectFieldEditor.tsx:49-76`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:19-39` |
| 多标签质量问题 | `multi_select` | 原生支持 | `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:27-45`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:19-39` |
| 简短理由 | `text` | 原生支持, 单行 | `apps/web/src/features/labeling/formily/components/LabelHubTextField.tsx:1-29` |
| 长理由/多段解释 | `rich_text` | 部分支持; 没有独立 plain multiline type | `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23` |
| 结构化证据 | `json_editor` | 原生支持 | `apps/web/src/features/labeling/formily/components/LabelHubJsonField.tsx:1-34` |
| 附件/截图 | `file_upload` | 文件原生支持; 图片为部分支持 | `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:29-40`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:54-63` |
| 字段级 AI 辅助 | `llm_interaction` | 原生支持, 真实 session 下可调用 field assist | `apps/web/src/features/schema-design/field-editors/LlmInteractionFieldEditor.tsx:12-26`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:34-43` |
| safety_flag 条件显隐 | `visibleWhen`/`requiredWhen` | 原生支持 schema 字段值联动 | `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:13-37`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100` |

## 不支持或限制项

- 未发现独立 `textarea`/`long_text` 字段类型; 多行普通文本只能借用 `rich_text` 或使用特定语义的 TextArea 组件。证据: `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/entities/schema/schemaTypes.ts:16-29`, `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23`。
- 未发现独立 `image_upload` 字段类型; 图片上传只能作为 `file_upload` 的 accepted file type 配置。证据: `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:29-40`。
- `rich_text` 有原生 type 和 contentEditable 运行时, 但未发现富文本工具栏或格式化按钮。证据: `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23`, `apps/web/src/features/schema-design/field-editors/RichTextFieldEditor.tsx:18-20`。
- `llm_interaction` 在 schema preview 等无 session 场景会禁用生成按钮; 真正调用依赖 sessionId。证据: `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:58-60`, `apps/web/src/features/schema-design/SchemaFormilyPreviewPanel.tsx:144-151`。
- `file_upload` 在无 session 场景不能上传。证据: `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:61-68`。
