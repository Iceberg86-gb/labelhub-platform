# LabelHub Designer/Renderer 课题符合度核实 RESEARCH

日期: 2026-06-07

范围: 仅核实仓库现状, 不设计实现方案, 不估 LOC。结论以 OpenAPI 契约、Designer 前端、Formily Renderer、运行时/发布服务端代码为证据。`humanpending.md` 未触碰。

## 结论摘要

- 同源渲染: 达标。Designer 预览面板直接调用 `SchemaFormilyRenderer`, Labeler 作答页也调用同一个 `SchemaFormilyRenderer`; 该 Renderer 统一调用 `schemaToFormilyISchema`, `componentsMap`, `LABEL_HUB_COMPONENTS` 和同一组 `LabelHub*` 字段组件。证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:9-12`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:38-42`, `apps/web/src/features/labeling/formily/adapters/componentRegistry.ts:3-16`, `apps/web/src/features/labeling/formily/components/index.ts:14-28`。
- schema 可序列化: 达标。发布请求携带 `schemaJson`, 服务端标准化为 JSON Schema v2 存入 `schema_versions.schema_json`; 前端 Designer 和运行时均通过 `schemaFields(schemaJson)` 读取 `x-labelhub-fields` 或兼容 legacy `fields`。证据: `packages/contracts/openapi/labelhub.yaml:3038-3066`, `apps/web/src/entities/schema/runtimeSchema.ts:5-26`, `apps/web/src/features/schema-design/usePublishSchemaVersion.ts:27-34`, `services/api/src/main/java/com/labelhub/api/module/schema/service/SchemaService.java:157-180`, `services/api/src/main/java/com/labelhub/api/module/schema/entity/SchemaVersionEntity.java:12-21`。
- 物料覆盖: 课题列出的 7 类中, 单行文本、单选/多选/标签、文件上传、JSON 编辑器、LLM 交互、展示项达标; 多行普通文本、富文本、图片上传为部分。证据见下方能力矩阵。
- 自定义校验: 必填、条件必填、长度、正则、数值范围均有契约与前后端校验证据; 自定义函数为部分, 因为只支持命名白名单 `nonBlankTrimmed`, `httpsUrl`, `jsonObject`, 不是任意用户函数体。证据: `packages/contracts/openapi/labelhub.yaml:2885-2904`, `apps/web/src/entities/schema/customValidation.ts:3-24`, `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaCustomValidationFunctions.java:9-40`。
- 联动与 Tab: 达标。Designer 同时提供可视化 `visibleWhen`/`requiredWhen` 构造器与 JSON DSL 编辑; 运行时显隐和条件必填由同一 evaluator/reaction 执行; 分组容器与多 Tab 有 Designer 和 Renderer 组件。证据: `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx:21-26`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:29-37`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100`, `apps/web/src/features/schema-design/field-editors/TabContainerFieldEditor.tsx:63-123`, `apps/web/src/features/labeling/formily/components/LabelHubTabsContainer.tsx:8-52`。
- 预览态限制不破坏同源渲染判定。Designer preview 没传 `sessionId`/`itemPayload`, 所以 `file_upload` 和 `llm_interaction` 在预览中禁用上传/生成, `show_item` 在无 payload 时只能展示标题或静态内容; 但它们仍由同一个 Renderer 和同一组件渲染。证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:61-65`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:25-40`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:58-60`。

## 课题要求逐条符合度矩阵

| 要求项 | 状态 | 证据 | 差距/说明 |
| --- | --- | --- | --- |
| Designer 只产出 schema, Renderer 消费 schema, 两端不各自维护表单逻辑 | 达标 | `docs/architecture/labelhub-complete-design-baseline.md:408-413`, `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:104-126`, `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358` | Designer 负责编辑 `schemaFields`; preview/runtime 共享 Renderer。 |
| 同一份 schema 支撑 Owner 预览与 Labeler 作答 | 达标 | `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:17-22`, `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:73-75`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358` | Preview 的值状态是本地模拟; 作答页有 session/dataset payload。渲染链同源。 |
| 同一份 schema 支撑历史 submission 重渲染 | 达标 | `apps/web/src/pages/labeler/LabelerSubmissionPage.tsx:84-98`, `apps/web/src/pages/owner/OwnerSubmissionPage.tsx:104-112` | 历史页 readOnly 渲染仍使用 `SchemaFormilyRenderer`。 |
| schema 可序列化 JSON Schema | 达标 | `packages/contracts/openapi/labelhub.yaml:3038-3066`, `apps/web/src/entities/schema/runtimeSchema.ts:17-26`, `services/api/src/main/java/com/labelhub/api/module/schema/runtime/SchemaRuntimeAdapter.java:50-66` | v2 以 JSON Schema root + `x-labelhub-fields` 存 runtime metadata。 |
| 发布版本存储与运行时消费同构 | 达标 | `apps/web/src/features/schema-design/usePublishSchemaVersion.ts:27-34`, `services/api/src/main/java/com/labelhub/api/module/schema/service/SchemaService.java:157-180`, `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:240-250`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:73-75` | 服务端存 `schemaJson`; Labeler 从 session detail 的 `schemaVersion.schemaJson` 读字段。 |
| 单行文本 | 达标 | `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:9-60`, `apps/web/src/features/labeling/formily/components/LabelHubTextField.tsx:13-29` | 运行时为 Semi `Input`。 |
| 多行普通文本 | 部分 | `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/entities/schema/schemaTypes.ts:16-29`, `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23` | 未发现独立 `textarea`/`long_text` type; 可借 `rich_text` 或语义化 TextArea 组件, 但不是普通多行文本 type。 |
| 单选/多选/标签 | 达标 | `apps/web/src/features/schema-design/field-editors/SelectFieldEditor.tsx:30-76`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:39-45`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:23-40`, `apps/web/src/features/labeling/formily/components/LabelHubSelectField.tsx:63-70` | `multi_select` 以 `mode: multiple` / `Select multiple` 实现, 只读态按 Tag 展示。 |
| 富文本 | 部分 | `apps/web/src/features/schema-design/field-editors/RichTextFieldEditor.tsx:9-28`, `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23` | 有 `rich_text` type 和 contentEditable HTML 区域; 未发现加粗、列表、链接等工具栏按钮证据。 |
| 文件上传 | 达标 | `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:27-42`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:22-39`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:52-68` | 真实 session 下上传 `/sessions/{sessionId}/attachments`; 预览态禁用。 |
| 图片上传 | 部分 | `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:27-40`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:54-58` | 无独立 `image_upload`; 可通过 `file_upload.acceptedFileTypes` 限定图片 MIME/扩展名, 未发现图片预览/裁剪/尺寸校验。 |
| JSON 编辑器 | 达标 | `apps/web/src/features/schema-design/field-editors/JsonFieldEditor.tsx:5-24`, `apps/web/src/features/labeling/formily/components/LabelHubJsonField.tsx:7-34` | TextArea 输入并 JSON.parse; 不是 Monaco/树形编辑器, 但 type 与运行时组件原生存在。 |
| LLM 交互 | 达标 | `apps/web/src/features/schema-design/field-editors/LlmInteractionFieldEditor.tsx:9-28`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:25-43`, `packages/contracts/openapi/labelhub.yaml:1540-1556`, `services/api/src/main/java/com/labelhub/api/module/ai/service/FieldAssistService.java:83-131` | 需要真实 `sessionId`; preview 的生成按钮禁用是 session 约束。 |
| 展示项 | 达标 | `apps/web/src/features/schema-design/field-editors/ShowItemFieldEditor.tsx:9-21`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:48-55`, `apps/web/src/features/labeling/formily/components/LabelHubShowItem.tsx:4-11`, `apps/web/src/features/labeling/showItemSource.ts:3-12` | 可配置静态内容或 `sourcePath`; 预览无 item payload 时动态内容可能为空。 |
| 必填 | 达标 | `packages/contracts/openapi/labelhub.yaml:2885-2890`, `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:27-30`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyValidators.ts:11-13`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:71-78` | 前端 Formily 和后端提交校验均执行。 |
| 条件必填/联动校验 | 达标 | `packages/contracts/openapi/labelhub.yaml:2997-3000`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:29-37`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:255-264` | `requiredWhen` 生效时才要求; 隐藏字段直接跳过校验。 |
| 长度校验 | 达标 | `packages/contracts/openapi/labelhub.yaml:2890-2895`, `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:32-59`, `apps/web/src/entities/labeling/payloadValidation.ts:126-148`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:101-128` | 对文本类 payload 执行; Designer UI 主要在 `text` 暴露长度输入。 |
| 正则校验 | 达标 | `packages/contracts/openapi/labelhub.yaml:2900-2901`, `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:51-58`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyValidators.ts:15-25`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:118-126` | Designer UI 主要在 `text` 暴露正则输入; 后端提交校验会处理 invalid regex。 |
| 自定义函数校验 | 部分 | `packages/contracts/openapi/labelhub.yaml:2902-2904`, `apps/web/src/entities/schema/customValidation.ts:3-24`, `apps/web/src/features/schema-design/field-editors/editorUtils.tsx:44-56`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyValidators.ts:36-42`, `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaCustomValidationFunctions.java:9-40`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:211-220` | 支持命名白名单函数, 不执行用户提交的任意代码; 与基线“通过注册 id 调用”一致。 |
| 字段联动: 条件显示 | 达标 | `packages/contracts/openapi/labelhub.yaml:2925-2954`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:201-282`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:285-357`, `apps/web/src/entities/labeling/linkageEvaluator.ts:29-85` | 支持 atomic 和 `allOf`/`anyOf` 组; 支持 eq/neq/in/notIn/比较/empty。 |
| 分组容器 | 达标 | `apps/web/src/features/schema-design/field-editors/NestedObjectFieldEditor.tsx:26-54`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:76-82`, `apps/web/src/features/labeling/formily/components/LabelHubNestedObjectField.tsx:3-5` | UI 明示 P4b 仅支持一层 children。 |
| 多 Tab | 达标 | `apps/web/src/features/schema-design/field-editors/TabContainerFieldEditor.tsx:63-123`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:57-73`, `apps/web/src/features/labeling/formily/components/LabelHubTabsContainer.tsx:8-52` | Tab 内字段按 stableId 单独提交; UI/校验不支持 Tab 内再嵌套 Tab。 |
| 预览态限制是否影响同源渲染 | 达标 | `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:38-42`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:39-45`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:61-65`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:58-60` | Renderer 同源; 上传/生成是运行时能力, 受 `sessionId` 约束。 |

## 同源渲染调用链对比

### Designer 预览路径

1. `OwnerSchemaDesignerPage` 从 current version 或空 schema 得到 `draftDocument`, 字段编辑通过 `replaceSchemaFields(draftDocument, nextFields)` 写回同一个 document。证据: `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:74-82`, `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:104-126`。
2. 预览面板入参是 `draftFields`, 即 `schemaFields(draftDocument)`。证据: `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:299`。
3. `SchemaFormilyPreviewPanel` 注释明确说明它只读 `designer.schemaFields`, 并通过 `SchemaFormilyRenderer` 渲染; preview 内部 `AnswerPayload` 只是本地状态。证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:17-24`, `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:24-36`。
4. 预览态用同一个 `createVisibleSchemaFieldsSelector` 做可见字段筛选, 然后渲染 `SchemaFormilyRenderer`。证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:31-35`, `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`。
5. Renderer 用 `schemaToFormilyISchema(schemaFields, { sessionId, itemPayload })` 生成 Formily schema, 并用 `componentsMap` 注册组件。证据: `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:9-12`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:38-42`, `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:56-64`。

### Labeler 运行时路径

1. 作答 session 创建/领取时绑定当前 schema version。证据: `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:220-225`。
2. session detail 返回 session、task、schemaVersion、datasetItem 和 latestDraft。证据: `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:240-253`, `apps/web/src/features/labeling/useSessionDetailQuery.ts:7-23`。
3. Labeler 作答页从 `detail.schemaVersion.schemaJson` 提取字段, 与 preview 一样使用 `createVisibleSchemaFieldsSelector`。证据: `apps/web/src/pages/labeler/LabelerSessionPage.tsx:57-58`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:73-75`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:159-162`。
4. Labeler 作答页渲染同一个 `SchemaFormilyRenderer`, 但额外传入 `sessionId` 与 `itemPayload`。证据: `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358`。
5. Renderer 后续仍进入同一个 adapter、registry 和 `LabelHub*` component map。证据: `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:9-12`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:27-45`, `apps/web/src/features/labeling/formily/adapters/componentRegistry.ts:3-16`, `apps/web/src/features/labeling/formily/components/index.ts:14-28`。

### 共享点与差异点

| 对比点 | Designer 预览 | Labeler 运行时 | 判定 |
| --- | --- | --- | --- |
| schema 来源 | `draftDocument` -> `schemaFields(draftDocument)` | `detail.schemaVersion.schemaJson` -> `schemaFields(schemaJson)` | 同构结构, 都消费 `SchemaField[]`; 证据: `apps/web/src/pages/owner/OwnerSchemaDesignerPage.tsx:104-126`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:73-75` |
| Renderer | `SchemaFormilyRenderer` | `SchemaFormilyRenderer` | 同一个 React 组件; 证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358` |
| schema -> Formily adapter | `schemaToFormilyISchema` | `schemaToFormilyISchema` | 同一个 adapter; 证据: `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:38-42` |
| 组件注册 | `componentsMap` / `LABEL_HUB_COMPONENTS` | `componentsMap` / `LABEL_HUB_COMPONENTS` | 同一个 registry; 证据: `apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx:9-12`, `apps/web/src/features/labeling/formily/adapters/componentRegistry.ts:3-16` |
| 显隐/条件必填 | preview value + selector + reactions | answerPayload + selector + reactions | 同 evaluator; 证据: `apps/web/src/entities/labeling/visibleSchemaFields.ts:20-39`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100`, `apps/web/src/entities/labeling/linkageEvaluator.ts:29-85` |
| session 能力 | 不传 `sessionId`/`itemPayload` | 传 `sessionId`/`itemPayload` | 差异是运行时上下文, 不是渲染逻辑分叉; 证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358` |

## Schema 可序列化与同构消费

OpenAPI 契约定义 `SchemaDocument`: legacy 使用 `fields`, 新版本使用 JSON Schema root 关键字加 `x-labelhub-fields` 保存运行时渲染 metadata。证据: `packages/contracts/openapi/labelhub.yaml:3038-3066`。

前端 `runtimeSchema.ts` 是 Designer 和运行时共同的读取/更新适配层: `schemaFields(document)` 对 v2 读 `x-labelhub-fields`, 对 legacy 读 `fields`; `replaceSchemaFields` 会同步更新 v2 的 `properties`, `required`, `x-labelhub-fields`。证据: `apps/web/src/entities/schema/runtimeSchema.ts:5-26`。

发布链路中, `PublishSchemaModal` 把 `draftDocument` 作为 `schemaJson` 提交; mutation POST `/schemas/{schemaId}/versions` body 为 `{ schemaJson }`。证据: `apps/web/src/features/schema-design/PublishSchemaModal.tsx:30-38`, `apps/web/src/features/schema-design/usePublishSchemaVersion.ts:27-34`。

服务端发布时将请求 document 转 Map, 通过 `SchemaRuntimeAdapter.toStorageJson` 标准化, 再将标准化后的 `schemaJson` 存入 `SchemaVersionEntity`。证据: `services/api/src/main/java/com/labelhub/api/module/schema/service/SchemaService.java:157-180`, `services/api/src/main/java/com/labelhub/api/module/schema/runtime/SchemaRuntimeAdapter.java:50-66`, `services/api/src/main/java/com/labelhub/api/module/schema/entity/SchemaVersionEntity.java:12-21`。

运行时消费链路中, Labeler session detail 带回绑定的 `SchemaVersionEntity`; 前端从 `detail.schemaVersion.schemaJson` 提取字段渲染。证据: `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:240-250`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:73-75`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358`。

## 类型全集与 Designer 可添加能力

OpenAPI `SchemaFieldType` enum 共 12 个 type: `text`, `number`, `single_select`, `multi_select`, `date`, `file_upload`, `rich_text`, `json_editor`, `llm_interaction`, `show_item`, `nested_object`, `tab_container`。证据: `packages/contracts/openapi/labelhub.yaml:2882-2884`。

前端 `SCHEMA_FIELD_TYPES` 也是同 12 个 type, `FIELD_TYPE_PALETTE_GROUPS` 把它们分到物料区, `FieldTypePicker` 从该数组生成添加按钮, `createField` switch 覆盖 12 个 type, `FieldEditor` switch 覆盖 12 个编辑器。证据: `apps/web/src/entities/schema/schemaTypes.ts:16-44`, `apps/web/src/features/schema-design/DesignerFieldBuilder.tsx:50-82`, `apps/web/src/features/schema-design/FieldTypePicker.tsx:10-21`, `apps/web/src/entities/schema/fieldFactory.ts:10-47`, `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx:30-60`。

结论: 未发现“契约有但前端无”或“前端有但契约无”的字段 type 差异。放置限制存在但不是 type 缺口: `nested_object` 子字段不能再加 `nested_object`/`tab_container`, `tab_container` 内不能再嵌套 `tab_container`。证据: `apps/web/src/features/schema-design/field-editors/NestedObjectFieldEditor.tsx:39-43`, `apps/web/src/features/schema-design/field-editors/TabContainerFieldEditor.tsx:107-110`, `apps/web/src/entities/schema/schemaValidation.ts:47-90`。

## 校验能力核实

| 校验项 | 状态 | 证据 | 说明 |
| --- | --- | --- | --- |
| 契约字段 | 达标 | `packages/contracts/openapi/labelhub.yaml:2885-2904` | `required`, `minLength`, `maxLength`, `min`, `max`, `pattern`, `customFunction` 均在契约中。 |
| Designer 必填开关 | 达标 | `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:27-30`, `apps/web/src/features/schema-design/field-editors/SelectFieldEditor.tsx:44-47`, `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:23-26`, `apps/web/src/features/schema-design/field-editors/LlmInteractionFieldEditor.tsx:22-25` | 多个编辑器提供 `validation.required`。 |
| 文本长度/正则 UI | 达标 | `apps/web/src/features/schema-design/field-editors/TextFieldEditor.tsx:32-59` | `text` 编辑器暴露 min/max length 和 pattern。 |
| 数值范围 UI | 达标 | `apps/web/src/features/schema-design/field-editors/NumberFieldEditor.tsx:28-39` | `number` 编辑器暴露 min/max。 |
| 自定义函数 UI | 部分 | `apps/web/src/features/schema-design/field-editors/editorUtils.tsx:44-56`, `apps/web/src/entities/schema/customValidation.ts:3-24` | 输入命名函数; 仅白名单/兼容类型有效。 |
| Formily 即时校验 | 达标 | `apps/web/src/features/labeling/formily/adapters/schemaToFormilyValidators.ts:5-44` | required、text min/max/pattern、number min/max、自定义函数均转为 Formily validator。 |
| Labeler 提交前校验 | 达标 | `apps/web/src/entities/labeling/payloadValidation.ts:13-20`, `apps/web/src/entities/labeling/payloadValidation.ts:29-48`, `apps/web/src/entities/labeling/payloadValidation.ts:126-162` | 隐藏字段跳过; 静态 required 和 `requiredWhen` 合并; text/number/select/file/object/custom 校验。 |
| 服务端提交校验 | 达标 | `services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:409-419`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:44-99`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:101-128`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/AnswerPayloadValidator.java:211-220` | 提交时按绑定 schema version 校验, 不是只依赖前端。 |
| 服务端 schema 发布校验 | 达标 | `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java:48-58`, `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java:103-117`, `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java:165-218` | 发布前校验字段、联动、customFunction 兼容性。 |

## 联动、分组与 Tab

联动 DSL 的契约支持 atomic 条件与 `allOf`/`anyOf` 条件组, 运算符覆盖 `eq`, `neq`, `in`, `notIn`, `gt`, `gte`, `lt`, `lte`, `empty`, `notEmpty`。证据: `packages/contracts/openapi/labelhub.yaml:2925-2954`。

Designer 对每个字段追加可视化联动构造器和高级 JSON 编辑器。可视化构造器可选择 `visibleWhen` 或 `requiredWhen`, 可配置单条件或条件组, 也会展开 nested/tab 内候选字段并排除当前字段。证据: `apps/web/src/features/schema-design/field-editors/FieldEditor.tsx:21-26`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:29-37`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:201-282`, `apps/web/src/features/schema-design/field-editors/LinkageConditionBuilder.tsx:285-408`。

高级 JSON 编辑器可直接写 `visibleWhen`/`requiredWhen`; UI 只校验 JSON 语法, 字段引用、操作符和值类型在发布时统一校验。证据: `apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx:78-100`, `apps/web/src/features/schema-design/field-editors/LinkageJsonEditor.tsx:112-130`, `apps/web/src/entities/schema/schemaValidation.ts:149-248`, `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaValidator.java:165-265`。

运行时层面, `schemaToFormilyISchema` 把 linkage 转为 Formily reaction, `visibleWhen` 控制 `visible/display`, `requiredWhen` 与静态 required 共同控制 required; 前端和后端都有 evaluator。证据: `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:88-100`, `apps/web/src/entities/labeling/linkageEvaluator.ts:10-35`, `apps/web/src/entities/labeling/linkageEvaluator.ts:47-85`, `services/api/src/main/java/com/labelhub/api/module/submission/validation/LinkageEvaluator.java:20-72`。

分组容器 `nested_object` 在 Designer 中可添加子字段, Renderer 中转 object properties。证据: `apps/web/src/features/schema-design/field-editors/NestedObjectFieldEditor.tsx:20-54`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:76-82`, `apps/web/src/features/labeling/formily/components/LabelHubNestedObjectField.tsx:3-5`。

多 Tab `tab_container` 在 Designer 中可添加/删除/命名 Tab, 向当前 Tab 添加字段; Renderer 中以 `LabelHubTabsContainer` 和 `LabelHubTabPane` 展示, 每个 Tab children 继续走同一 `fieldsToProperties`。证据: `apps/web/src/features/schema-design/field-editors/TabContainerFieldEditor.tsx:31-60`, `apps/web/src/features/schema-design/field-editors/TabContainerFieldEditor.tsx:79-123`, `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:57-73`, `apps/web/src/features/labeling/formily/components/LabelHubTabsContainer.tsx:8-52`。

## 预览态限制对同源渲染判定的影响

Designer preview 不传 `sessionId` 或 `itemPayload`, Labeler 运行时传入二者。证据: `apps/web/src/features/labeling/formily/preview/SchemaFormilyPreviewPanel.tsx:144-151`, `apps/web/src/pages/labeler/LabelerSessionPage.tsx:349-358`。

`schemaToFormilyISchema` 把 `sessionId` 和 `itemPayload` 作为 component props 下发给同一组字段组件。证据: `apps/web/src/features/labeling/formily/adapters/schemaToFormilyISchema.ts:39-45`。

`file_upload` 只有有 `sessionId` 才执行上传, 预览态按钮禁用并提示“预览模式不上传文件”。证据: `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:22-39`, `apps/web/src/features/labeling/formily/components/LabelHubFileUploadField.tsx:61-65`。

`llm_interaction` 只有有 `sessionId` 和 token 才调用 `/ai-review/field-assist`, 预览态生成按钮禁用。证据: `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:25-43`, `apps/web/src/features/labeling/formily/components/LabelHubLlmInteractionField.tsx:58-60`, `packages/contracts/openapi/labelhub.yaml:3696-3710`。

`show_item` 使用 `itemPayload` 和 `sourcePath` 解析动态展示内容; preview 无 dataset payload 时动态内容可能为空, 但仍走同一 `LabelHubShowItem`。证据: `apps/web/src/features/labeling/formily/components/LabelHubShowItem.tsx:4-11`, `apps/web/src/features/labeling/showItemSource.ts:3-12`。

判定: 这些限制是 session/payload 上下文约束, 不构成 Designer preview 与 Labeler runtime 各自维护渲染逻辑。核心渲染链仍为同一 `SchemaFormilyRenderer` + `schemaToFormilyISchema` + `componentsMap`。

## 发现的差距与边界

- 未发现同源渲染缺口; 最高优先问题“是否同一套渲染逻辑”结论为达标。
- 未发现 OpenAPI type 与 Designer 可添加 type 不一致。
- 多行普通文本没有独立 type。证据: `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/entities/schema/schemaTypes.ts:16-29`。
- 富文本是轻量 contentEditable, 未发现工具栏级富文本编辑器能力。证据: `apps/web/src/features/labeling/formily/components/LabelHubRichTextField.tsx:13-23`, `apps/web/src/features/schema-design/field-editors/RichTextFieldEditor.tsx:18-20`。
- 图片上传没有独立 image type; 只能借 `file_upload.acceptedFileTypes`。证据: `packages/contracts/openapi/labelhub.yaml:2882-2884`, `apps/web/src/features/schema-design/field-editors/FileUploadFieldEditor.tsx:27-40`。
- 自定义函数校验是白名单命名函数, 不是任意代码执行; 这与基线“不执行用户提交的任意代码, 自定义函数通过注册 id 调用”一致。证据: `docs/architecture/labelhub-complete-design-baseline.md:412-413`, `apps/web/src/entities/schema/customValidation.ts:3-24`, `services/api/src/main/java/com/labelhub/api/module/schema/util/SchemaCustomValidationFunctions.java:9-40`。
